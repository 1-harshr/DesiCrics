package com.harsh.desicrics.data

import com.harsh.desicrics.data.sqlconnect.AddMatchPlayerVariables
import com.harsh.desicrics.data.sqlconnect.CreateMatchData
import com.harsh.desicrics.data.sqlconnect.CreateMatchVariables
import com.harsh.desicrics.data.sqlconnect.GetMatchData
import com.harsh.desicrics.data.sqlconnect.GetMatchVariables
import com.harsh.desicrics.data.sqlconnect.IgnoredData
import com.harsh.desicrics.data.sqlconnect.ListRecentMatchesData
import com.harsh.desicrics.data.sqlconnect.ListRecentMatchesVariables
import com.harsh.desicrics.data.sqlconnect.MatchIdVariables
import com.harsh.desicrics.data.sqlconnect.RecordDeliveryVariables
import com.harsh.desicrics.data.sqlconnect.SqlConnectClient
import com.harsh.desicrics.data.sqlconnect.UndoDeliveryVariables
import com.harsh.desicrics.model.Delivery
import com.harsh.desicrics.model.Match
import com.harsh.desicrics.model.TeamSide
import com.harsh.desicrics.model.TossDecision
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class MatchSnapshot(
    val match: Match,
    val deliveries: List<Delivery>,
)

class MatchRepository(private val sqlConnect: SqlConnectClient) {

    /** Polled, since SQL Connect's realtime subscriptions aren't available over its REST API. */
    fun observeRecentMatches(limit: Int = 30): Flow<Result<List<Match>>> = poll(RECENT_MATCHES_INTERVAL) {
        sqlConnect.query<ListRecentMatchesVariables, ListRecentMatchesData>(
            "ListRecentMatches",
            ListRecentMatchesVariables(limit),
        ).matches.map { it.toModel() }
    }

    /** Emits null if the match doesn't exist. */
    fun observeMatch(matchId: String): Flow<Result<MatchSnapshot?>> = poll(LIVE_MATCH_INTERVAL) {
        sqlConnect.query<GetMatchVariables, GetMatchData>("GetMatch", GetMatchVariables(matchId))
            .match
            ?.let { row -> MatchSnapshot(row.toModel(), row.deliveries.map { it.toModel() }) }
    }

    /**
     * Creates the match, adds every player, then starts it. The match stays hidden (SETUP) until the
     * last step, so a failure part-way never shows a half-built roster.
     */
    suspend fun createMatch(
        teamAName: String,
        teamAPlayers: List<String>,
        teamBName: String,
        teamBPlayers: List<String>,
        oversPerInnings: Int,
        tossWinner: TeamSide,
        tossDecision: TossDecision,
    ): String {
        val matchId = sqlConnect.mutation<CreateMatchVariables, CreateMatchData>(
            "CreateMatch",
            CreateMatchVariables(teamAName, teamBName, oversPerInnings, tossWinner, tossDecision),
        ).match_insert.id

        val players = teamAPlayers.mapIndexed { i, name -> AddMatchPlayerVariables(matchId, TeamSide.A, i, name) } +
            teamBPlayers.mapIndexed { i, name -> AddMatchPlayerVariables(matchId, TeamSide.B, i, name) }
        coroutineScope {
            players.map { async { sqlConnect.mutation<AddMatchPlayerVariables, IgnoredData>("AddMatchPlayer", it) } }.awaitAll()
        }

        sqlConnect.mutation<MatchIdVariables, IgnoredData>("StartMatch", MatchIdVariables(matchId))
        return matchId
    }

    /** Saves the ball and the refreshed summary in one transaction. [match] already carries the new summary. */
    suspend fun recordDelivery(match: Match, delivery: Delivery) {
        sqlConnect.mutation<RecordDeliveryVariables, IgnoredData>(
            "RecordDelivery",
            RecordDeliveryVariables(
                matchId = match.id,
                seq = delivery.seq,
                innings = delivery.innings,
                strikerId = delivery.strikerId,
                nonStrikerId = delivery.nonStrikerId,
                bowlerId = delivery.bowlerId,
                runsOffBat = delivery.runsOffBat,
                extra = delivery.extra,
                extraRuns = delivery.extraRuns,
                wicketKind = delivery.wicket?.kind,
                playerOutId = delivery.wicket?.playerOutId,
                scoreSummary = match.scoreSummary,
                status = match.status,
                result = match.result,
            ),
        )
    }

    suspend fun undoDelivery(match: Match, delivery: Delivery) {
        sqlConnect.mutation<UndoDeliveryVariables, IgnoredData>(
            "UndoDelivery",
            UndoDeliveryVariables(match.id, delivery.seq, match.scoreSummary, match.status, match.result),
        )
    }

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
