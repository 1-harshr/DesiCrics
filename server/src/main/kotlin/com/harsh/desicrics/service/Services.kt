package com.harsh.desicrics.service

import com.harsh.desicrics.api.CreateMatchRequest
import com.harsh.desicrics.api.MatchDetails
import com.harsh.desicrics.api.UpdateProfileRequest
import com.harsh.desicrics.auth.FirebasePrincipal
import com.harsh.desicrics.model.Delivery
import com.harsh.desicrics.model.Match
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
        )
        matches.create(match)
        return MatchDetails(match, emptyList())
    }

    suspend fun recordDelivery(user: FirebasePrincipal, matchId: String, delivery: Delivery): MatchDetails {
        val (match, deliveries) = scorersMatch(user, matchId)
        val nextSeq = (deliveries.maxOfOrNull { it.seq } ?: -1) + 1
        ScoringEngine.compute(match, deliveries).rejectionReason(delivery, nextSeq)?.let {
            throw ApiException(HttpStatusCode.Conflict, it)
        }
        val saved = delivery.copy(timestamp = clock())
        val updatedDeliveries = deliveries + saved
        val updated = match.withScore(ScoringEngine.compute(match, updatedDeliveries))
        if (!matches.addDelivery(updated, saved)) {
            throw ApiException(HttpStatusCode.Conflict, "That ball was already recorded. Refresh and try again")
        }
        return MatchDetails(updated, updatedDeliveries)
    }

    suspend fun undoLastDelivery(user: FirebasePrincipal, matchId: String): MatchDetails {
        val (match, deliveries) = scorersMatch(user, matchId)
        val last = deliveries.maxByOrNull { it.seq } ?: throw ApiException(HttpStatusCode.Conflict, "No balls to undo")
        val remaining = deliveries - last
        val updated = match.withScore(ScoringEngine.compute(match, remaining))
        if (!matches.removeDelivery(updated, last.seq)) {
            throw ApiException(HttpStatusCode.Conflict, "That ball was already undone. Refresh and try again")
        }
        return MatchDetails(updated, remaining)
    }

    /** Recomputes the stored score of matches scored before per-innings scores existed. Safe to run on every start. */
    suspend fun backfillInningsScores() {
        for (id in matches.idsMissingInningsScores()) {
            val (match, deliveries) = matches.details(id) ?: continue
            matches.updateScore(match.withScore(ScoringEngine.compute(match, deliveries)))
        }
    }

    private suspend fun scorersMatch(user: FirebasePrincipal, matchId: String): MatchDetails {
        val details = details(matchId)
        if (details.match.createdBy != user.uid) {
            throw ApiException(HttpStatusCode.Forbidden, "Only the scorer can change this match")
        }
        return details
    }

    private fun newId() = UUID.randomUUID().toString()
}
