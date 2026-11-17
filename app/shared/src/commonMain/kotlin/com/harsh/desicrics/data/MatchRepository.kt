package com.harsh.desicrics.data

import com.harsh.desicrics.api.CreateMatchRequest
import com.harsh.desicrics.api.MatchDetails
import com.harsh.desicrics.data.api.DesiCricsApi
import com.harsh.desicrics.model.Delivery
import com.harsh.desicrics.model.Match
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class MatchRepository(private val api: DesiCricsApi) {

    /** Polled for now; a WebSocket from the server can replace this without changing callers. */
    fun observeRecentMatches(limit: Int = 30): Flow<Result<List<Match>>> =
        poll(RECENT_MATCHES_INTERVAL) { api.recentMatches(limit) }

    /** Emits null if the match doesn't exist. */
    fun observeMatch(matchId: String): Flow<Result<MatchDetails?>> =
        poll(LIVE_MATCH_INTERVAL) { api.match(matchId) }

    /** Returns the new match's ID. */
    suspend fun createMatch(request: CreateMatchRequest): String = api.createMatch(request).match.id

    /** The server validates the ball and returns the authoritative scorecard. */
    suspend fun recordDelivery(matchId: String, delivery: Delivery): MatchDetails = api.recordDelivery(matchId, delivery)

    suspend fun undoLastDelivery(matchId: String): MatchDetails = api.undoLastDelivery(matchId)

    /** Fetches straight away, then every [interval]. Failures are emitted, not thrown, so polling survives a bad network. */
    private fun <T> poll(interval: Duration, fetch: suspend () -> T): Flow<Result<T>> = flow {
        while (true) {
            val result = try {
                Result.success(fetch())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
            emit(result)
            delay(interval)
        }
    }

    private companion object {
        val LIVE_MATCH_INTERVAL = 4.seconds
        val RECENT_MATCHES_INTERVAL = 15.seconds
    }
}
