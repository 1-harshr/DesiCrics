package com.harsh.desicrics.service

import com.harsh.desicrics.api.AddPlayerRequest
import com.harsh.desicrics.api.CreateMatchRequest
import com.harsh.desicrics.api.HandoverCode
import com.harsh.desicrics.api.MatchDetails
import com.harsh.desicrics.api.MatchSetupError
import com.harsh.desicrics.api.SquadRequest
import com.harsh.desicrics.api.StartFixtureRequest
import com.harsh.desicrics.api.UpdateProfileRequest
import com.harsh.desicrics.auth.FirebasePrincipal
import com.harsh.desicrics.live.MatchListener
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
import com.harsh.desicrics.store.PlayerStore
import com.harsh.desicrics.store.TeamStore
import com.harsh.desicrics.store.UserStore
import io.ktor.http.HttpStatusCode
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/** A failure the client should see, with the HTTP status to send. */
class ApiException(val status: HttpStatusCode, override val message: String) : Exception(message)

class ProfileService(
    private val users: UserStore,
    private val players: PlayerStore,
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
        players.syncUser(profile)
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
    private val players: PlayerStore,
    private val teams: TeamStore,
    /** Told about every change: the live feed and notifications. */
    listeners: List<MatchListener> = emptyList(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val listeners = CopyOnWriteArrayList(listeners)

    /** For listeners that need this service themselves, like tournaments advancing a round. */
    fun listen(listener: MatchListener) {
        listeners += listener
    }

    suspend fun recent(limit: Int): List<Match> = matches.recent(limit.coerceIn(1, 100))

    /** Matches the caller played in, scored or created, newest first. */
    suspend fun mine(user: FirebasePrincipal): List<Match> = matches.forPlayer(user.uid, limit = 100)

    suspend fun details(matchId: String): MatchDetails =
        matches.details(matchId) ?: throw ApiException(HttpStatusCode.NotFound, "Match not found")

    suspend fun create(user: FirebasePrincipal, request: CreateMatchRequest): MatchDetails {
        request.validationError()?.let { throw ApiException(HttpStatusCode.BadRequest, it) }
        if (users.get(user.uid) == null) {
            throw ApiException(HttpStatusCode.Conflict, "Set up your profile before starting a match")
        }
        checkSquads(request)
        val match = Match(
            id = newId(),
            createdBy = user.uid,
            createdAt = clock(),
            teamA = team(request.teamA),
            teamB = team(request.teamB),
            oversPerInnings = request.oversPerInnings,
            tossWinner = request.tossWinner,
            tossDecision = request.tossDecision,
            status = if (request.pending) MatchStatus.SCHEDULED else MatchStatus.IN_PROGRESS,
            scheduledAt = request.scheduledAt,
            venue = request.venue.trim(),
            rules = request.rules,
        )
        matches.create(match)
        return MatchDetails(match, emptyList()).also { announce(null, it) }
    }

    /** Saves a tournament fixture: saved teams, no squads yet. */
    suspend fun createFixture(match: Match) {
        matches.create(match)
        announce(null, MatchDetails(match, emptyList()))
    }

    /**
     * Starts a scheduled fixture with the squads and toss in [request]; the caller becomes its scorer.
     * Whether they're allowed to is the tournament's call, checked before this.
     */
    suspend fun startFixture(user: FirebasePrincipal, matchId: String, request: StartFixtureRequest): MatchDetails {
        val before = details(matchId)
        val fixture = before.match
        if (fixture.status != MatchStatus.SCHEDULED) throw ApiException(HttpStatusCode.Conflict, "That match has already started")
        if (request.teamA.teamId != fixture.teamA.teamId || request.teamB.teamId != fixture.teamB.teamId) {
            throw ApiException(HttpStatusCode.BadRequest, "Pick squads from the teams in this fixture")
        }
        val asMatch = CreateMatchRequest(
            request.teamA.copy(name = fixture.teamA.name), request.teamB.copy(name = fixture.teamB.name),
            fixture.oversPerInnings, request.tossWinner, request.tossDecision, fixture.rules,
        )
        asMatch.validationError()?.let { throw ApiException(HttpStatusCode.BadRequest, it) }
        checkSquads(asMatch)
        val started = fixture.copy(
            teamA = team(asMatch.teamA),
            teamB = team(asMatch.teamB),
            tossWinner = request.tossWinner,
            tossDecision = request.tossDecision,
            status = MatchStatus.IN_PROGRESS,
            scorerUid = user.uid,
        )
        val next = MatchDetails(started, emptyList()).rescored()
        if (!matches.startFixture(next.match)) throw ApiException(HttpStatusCode.Conflict, "That match has already started")
        return next.also { announce(before, it) }
    }

    suspend fun schedule(matchId: String, scheduledAt: Long?): MatchDetails {
        matches.setSchedule(matchId, scheduledAt)
        return details(matchId)
    }

    /** Every linked player and team must exist. */
    private suspend fun checkSquads(request: CreateMatchRequest) {
        val profileIds = (request.teamA.players + request.teamB.players).mapNotNull { it.profileId }
        if (players.summaries(profileIds).size != profileIds.toSet().size) {
            throw ApiException(HttpStatusCode.BadRequest, "One of those players doesn't exist")
        }
        for (teamId in listOfNotNull(request.teamA.teamId, request.teamB.teamId)) {
            if (teams.get(teamId) == null) throw ApiException(HttpStatusCode.BadRequest, "One of those teams doesn't exist")
        }
    }

    private fun team(squad: SquadRequest): Team {
        val roster = squad.players.map { Player(newId(), it.name.trim(), it.profileId) }
        return Team(
            name = squad.name.trim(),
            players = roster,
            teamId = squad.teamId,
            captainId = squad.captain?.let { roster[it].id },
            keeperId = squad.keeper?.let { roster[it].id },
        )
    }

    suspend fun recordDelivery(user: FirebasePrincipal, matchId: String, delivery: Delivery): MatchDetails {
        val details = scorersMatch(user, matchId)
        details.state().rejectionReason(delivery, details.nextSeq)?.let { throw ApiException(HttpStatusCode.Conflict, it) }
        val saved = delivery.copy(timestamp = clock())
        val next = details.copy(deliveries = details.deliveries + saved).rescored()
        if (!matches.addDelivery(next.match, saved)) {
            throw ApiException(HttpStatusCode.Conflict, "That ball was already recorded. Refresh and try again")
        }
        return next.also { announce(details, it) }
    }

    suspend fun recordEvent(user: FirebasePrincipal, matchId: String, event: MatchEvent): MatchDetails {
        val details = scorersMatch(user, matchId)
        details.state().rejectionReason(event, details.nextSeq)?.let { throw ApiException(HttpStatusCode.Conflict, it) }
        val saved = event.stamped(clock())
        val next = details.copy(events = details.events + saved).rescored()
        if (!matches.addEvent(next.match, saved)) {
            throw ApiException(HttpStatusCode.Conflict, "That was already recorded. Refresh and try again")
        }
        return next.also { announce(details, it) }
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
        return next.also { announce(details, it) }
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
        return next.also { announce(details, it) }
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
        // Watchers need the new scorer too, so the old one's phone stops offering to score.
        return details(matchId).also { announce(it, it) }
    }

    private suspend fun announce(before: MatchDetails?, after: MatchDetails) = listeners.forEach { it.changed(before, after) }

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
