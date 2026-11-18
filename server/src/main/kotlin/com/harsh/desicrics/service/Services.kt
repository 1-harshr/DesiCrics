package com.harsh.desicrics.service

import com.harsh.desicrics.api.AddPlayerRequest
import com.harsh.desicrics.api.CreateMatchRequest
import com.harsh.desicrics.api.HandoverCode
import com.harsh.desicrics.api.MatchDetails
import com.harsh.desicrics.api.MatchSetupError
import com.harsh.desicrics.api.UpdateProfileRequest
import com.harsh.desicrics.auth.FirebasePrincipal
import com.harsh.desicrics.model.Delivery
import com.harsh.desicrics.model.Match
import com.harsh.desicrics.model.MatchEvent
import com.harsh.desicrics.model.MatchStatus
import com.harsh.desicrics.model.Player
import com.harsh.desicrics.model.Team
import com.harsh.desicrics.model.UserProfile
import com.harsh.desicrics.scoring.ScoringEngine
import com.harsh.desicrics.scoring.rejectionReason
import com.harsh.desicrics.scoring.withScore
import com.harsh.desicrics.store.MatchStore
import com.harsh.desicrics.store.UserStore
import io.ktor.http.HttpStatusCode
import java.util.UUID

/** A failure the client should see, with the HTTP status to send. */
class ApiException(val status: HttpStatusCode, override val message: String) : Exception(message)

class ProfileService(
    private val users: UserStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun get(user: FirebasePrincipal): UserProfile? = users.get(user.uid)

    suspend fun update(user: FirebasePrincipal, request: UpdateProfileRequest): UserProfile {
        request.validationError()?.let { throw ApiException(HttpStatusCode.BadRequest, it) }
        val existing = users.get(user.uid)
        val profile = UserProfile(
            uid = user.uid,
            name = request.name.trim(),
            phone = request.phone.trim(),
            // Email comes from the verified token, never from the request body.
            email = user.email,
            photoUrl = request.photoUrl,
            city = request.city.trim(),
            role = request.role,
            battingStyle = request.battingStyle,
            bowlingStyle = request.bowlingStyle,
            createdAt = existing?.createdAt ?: clock(),
        )
        users.upsert(profile)
        return profile
    }
}

/**
 * Match rules, independent of where matches are stored. The server is the source of truth: it validates
 * every ball with the shared engine and computes the score itself rather than trusting the client.
 */
class MatchService(
    private val matches: MatchStore,
    private val users: UserStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun recent(limit: Int): List<Match> = matches.recent(limit.coerceIn(1, 100))

    suspend fun details(matchId: String): MatchDetails =
        matches.details(matchId) ?: throw ApiException(HttpStatusCode.NotFound, "Match not found")

    suspend fun create(user: FirebasePrincipal, request: CreateMatchRequest): MatchDetails {
        request.validationError()?.let { throw ApiException(HttpStatusCode.BadRequest, it) }
        if (users.get(user.uid) == null) {
            throw ApiException(HttpStatusCode.Conflict, "Set up your profile before starting a match")
        }
        fun team(name: String, players: List<String>) = Team(name.trim(), players.map { Player(newId(), it.trim()) })
        val match = Match(
            id = newId(),
            createdBy = user.uid,
            createdAt = clock(),
            teamA = team(request.teamAName, request.teamAPlayers),
            teamB = team(request.teamBName, request.teamBPlayers),
            oversPerInnings = request.oversPerInnings,
            tossWinner = request.tossWinner,
            tossDecision = request.tossDecision,
            status = MatchStatus.IN_PROGRESS,
            rules = request.rules,
        )
        matches.create(match)
        return MatchDetails(match, emptyList())
    }

    suspend fun recordDelivery(user: FirebasePrincipal, matchId: String, delivery: Delivery): MatchDetails {
        val details = scorersMatch(user, matchId)
        details.state().rejectionReason(delivery, details.nextSeq)?.let { throw ApiException(HttpStatusCode.Conflict, it) }
        val saved = delivery.copy(timestamp = clock())
        val next = details.copy(deliveries = details.deliveries + saved).rescored()
        if (!matches.addDelivery(next.match, saved)) {
            throw ApiException(HttpStatusCode.Conflict, "That ball was already recorded. Refresh and try again")
        }
        return next
    }

    suspend fun recordEvent(user: FirebasePrincipal, matchId: String, event: MatchEvent): MatchDetails {
        val details = scorersMatch(user, matchId)
        details.state().rejectionReason(event, details.nextSeq)?.let { throw ApiException(HttpStatusCode.Conflict, it) }
        val saved = event.stamped(clock())
        val next = details.copy(events = details.events + saved).rescored()
        if (!matches.addEvent(next.match, saved)) {
            throw ApiException(HttpStatusCode.Conflict, "That was already recorded. Refresh and try again")
        }
        return next
    }

    /** Removes the latest ball or event, whichever came last. */
    suspend fun undoLast(user: FirebasePrincipal, matchId: String): MatchDetails {
        val details = scorersMatch(user, matchId)
        val lastSeq = details.nextSeq - 1
        if (lastSeq < 0) throw ApiException(HttpStatusCode.Conflict, "Nothing to undo")
        val next = details.copy(
            deliveries = details.deliveries.filter { it.seq != lastSeq },
            events = details.events.filter { it.seq != lastSeq },
        ).rescored()
        if (!matches.removeEntry(next.match, lastSeq)) {
            throw ApiException(HttpStatusCode.Conflict, "That was already undone. Refresh and try again")
        }
        return next
    }

    /** A latecomer or substitute joins one side. Allowed at any point, even mid-innings. */
    suspend fun addPlayer(user: FirebasePrincipal, matchId: String, request: AddPlayerRequest): MatchDetails {
        request.validationError()?.let { throw ApiException(HttpStatusCode.BadRequest, it) }
        val details = scorersMatch(user, matchId)
        if (details.match.team(request.side).players.size >= CreateMatchRequest.MAX_PLAYERS) {
            throw ApiException(HttpStatusCode.Conflict, MatchSetupError.TOO_MANY_PLAYERS.message)
        }
        matches.addPlayer(matchId, request.side, Player(newId(), request.name.trim()))
        // A bigger side can change whether an innings is over, so refresh the stored score too.
        val next = details(matchId).rescored()
        matches.updateScore(next.match)
        return next
    }

    /** Recomputes the stored score of matches scored before per-innings scores existed. Safe to run on every start. */
    suspend fun backfillInningsScores() {
        for (id in matches.idsMissingInningsScores()) {
            val details = matches.details(id) ?: continue
            matches.updateScore(details.rescored().match)
        }
    }

    /** A code the scorer shows so someone else can take over scoring; valid for [HANDOVER_TTL_MS]. */
    suspend fun startHandover(user: FirebasePrincipal, matchId: String): HandoverCode {
        scorersMatch(user, matchId)
        val expiresAt = clock() + HANDOVER_TTL_MS
        repeat(5) {
            val code = (0 until 6).map { HANDOVER_DIGITS.random() }.joinToString("")
            if (matches.saveHandover(matchId, code, expiresAt)) return HandoverCode(code, expiresAt)
        }
        throw ApiException(HttpStatusCode.ServiceUnavailable, "Couldn't make a handover code. Try again")
    }

    /** The caller becomes the scorer of the match the code was made for. Codes work once. */
    suspend fun takeHandover(user: FirebasePrincipal, code: String): MatchDetails {
        if (users.get(user.uid) == null) {
            throw ApiException(HttpStatusCode.Conflict, "Set up your profile before scoring a match")
        }
        val matchId = matches.takeHandover(code.trim(), clock(), user.uid)
            ?: throw ApiException(HttpStatusCode.NotFound, "That handover code is wrong or has expired")
        return details(matchId)
    }

    private fun MatchDetails.state() = ScoringEngine.compute(match, deliveries, events)

    private fun MatchDetails.rescored() = copy(match = match.withScore(state()))

    private fun MatchEvent.stamped(at: Long): MatchEvent = when (this) {
        is MatchEvent.Retire -> copy(timestamp = at)
        is MatchEvent.SwapEnds -> copy(timestamp = at)
        is MatchEvent.ReplaceBowler -> copy(timestamp = at)
        is MatchEvent.Penalty -> copy(timestamp = at)
        is MatchEvent.Declare -> copy(timestamp = at)
        is MatchEvent.ReviseTarget -> copy(timestamp = at)
    }

    private suspend fun scorersMatch(user: FirebasePrincipal, matchId: String): MatchDetails {
        val details = details(matchId)
        if (details.match.scorerUid != user.uid) {
            throw ApiException(HttpStatusCode.Forbidden, "Only the scorer can change this match")
        }
        return details
    }

    private fun newId() = UUID.randomUUID().toString()

    private companion object {
        const val HANDOVER_TTL_MS = 10 * 60 * 1000L
        val HANDOVER_DIGITS = '0'..'9'
    }
}
