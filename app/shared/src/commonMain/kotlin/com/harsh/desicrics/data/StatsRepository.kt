package com.harsh.desicrics.data

import com.harsh.desicrics.data.api.DesiCricsApi
import com.harsh.desicrics.model.CareerStats
import com.harsh.desicrics.model.FormatFilter
import com.harsh.desicrics.model.LeaderboardMetric
import com.harsh.desicrics.model.LeaderboardRow
import com.harsh.desicrics.model.LeaderboardScope

/** Career stats, comparisons and leaderboards, all worked out by the server. */
class StatsRepository(private val api: DesiCricsApi) {

    suspend fun career(profileId: String, season: Int?, format: FormatFilter): CareerStats = api.playerStats(profileId, season, format)

    suspend fun compare(a: String, b: String, season: Int?, format: FormatFilter): Pair<CareerStats, CareerStats> =
        api.compare(a, b, season, format).let { it[0] to it[1] }

    suspend fun leaderboard(scope: LeaderboardScope, id: String?, metric: LeaderboardMetric, season: Int?): List<LeaderboardRow> =
        api.leaderboard(scope, id, metric, season)
}
