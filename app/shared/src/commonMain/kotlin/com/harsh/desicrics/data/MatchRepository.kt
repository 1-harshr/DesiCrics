package com.harsh.desicrics.data

import com.harsh.desicrics.api.AddPlayerRequest
import com.harsh.desicrics.api.CreateMatchRequest
import com.harsh.desicrics.api.HandoverCode
import com.harsh.desicrics.api.MatchDetails
import com.harsh.desicrics.data.api.DesiCricsApi
import com.harsh.desicrics.model.Delivery
import com.harsh.desicrics.model.Match
import com.harsh.desicrics.model.MatchEvent
import com.harsh.desicrics.model.TodayFeed
import com.harsh.desicrics.model.TeamSide
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class MatchRepository(private val api: DesiCricsApi, private val analytics: Analytics) {

    /** The home feed, refreshed every so often. */
    fun observeFeed(): Flow<Result<TodayFeed>> = poll(FEED_INTERVAL) { api.feed() }

    /**
     * The match as it changes: every ball arrives over the live WebSocket within a second or so. If the
     * socket can't connect or drops, one poll keeps the screen current and the socket is retried after a
     * short wait, so a flaky network degrades to polling rather than to a frozen scorecard. Emits null
     * if the match doesn't exist.
     */
    fun observeMatch(matchId: String): Flow<Result<MatchDetails?>> = flow {
        val first = fetch { api.match(matchId) }
        emit(first)
        if (first.getOrNull() == null && first.isSuccess) return@flow
        while (true) {
            try {
                api.liveMatch(matchId).collect { emit(Result.success(it)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emit(fetch { api.match(matchId) })
            }
            delay(RECONNECT_DELAY)
        }
    }

    /** Returns the new match's ID. */
    suspend fun createMatch(request: CreateMatchRequest): String = api.createMatch(request).match.id.also {
        analytics.log(
            AnalyticsEvents.MATCH_CREATED,
            mapOf("overs" to "${request.oversPerInnings}", "saved_teams" to "${listOfNotNull(request.teamA.teamId, request.teamB.teamId).size}"),
        )
    }

    /** The server validates the ball and returns the authoritative scorecard. */
    suspend fun recordDelivery(matchId: String, delivery: Delivery): MatchDetails = api.recordDelivery(matchId, delivery)

    suspend fun recordEvent(matchId: String, event: MatchEvent): MatchDetails = api.recordEvent(matchId, event)

    suspend fun addPlayer(matchId: String, side: TeamSide, name: String): MatchDetails = api.addPlayer(matchId, AddPlayerRequest(side, name))

    suspend fun startHandover(matchId: String): HandoverCode = api.startHandover(matchId)

    /** Returns the match ID the caller now scores, or null if the code is wrong or has expired. */
    suspend fun takeHandover(code: String): String? = api.takeHandover(code)?.match?.id
        ?.also { analytics.log(AnalyticsEvents.SCORING_HANDED_OVER, emptyMap()) }

    /** Removes the latest ball or event. */
    suspend fun undoLast(matchId: String): MatchDetails = api.undoLast(matchId)

    /** Fetches straight away, then every [interval]. Failures are emitted, not thrown, so polling survives a bad network. */
    private fun <T> poll(interval: Duration, call: suspend () -> T): Flow<Result<T>> = flow {
        while (true) {
            emit(fetch(call))
            delay(interval)
        }
    }

    private suspend fun <T> fetch(call: suspend () -> T): Result<T> = try {
        Result.success(call())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private companion object {
        val FEED_INTERVAL = 15.seconds
        val RECONNECT_DELAY = 3.seconds
    }
}
