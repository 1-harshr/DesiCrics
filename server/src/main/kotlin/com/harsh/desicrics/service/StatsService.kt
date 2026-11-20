package com.harsh.desicrics.service

import com.harsh.desicrics.cities.Cities
import com.harsh.desicrics.api.MatchDetails
import com.harsh.desicrics.auth.FirebasePrincipal
import com.harsh.desicrics.live.MatchListener
import com.harsh.desicrics.model.CareerStats
import com.harsh.desicrics.model.FollowType
import com.harsh.desicrics.model.FormatFilter
import com.harsh.desicrics.model.LeaderboardMetric
import com.harsh.desicrics.model.LeaderboardRow
import com.harsh.desicrics.model.LeaderboardScope
import com.harsh.desicrics.model.MatchStatus
import com.harsh.desicrics.model.PlayerMatchLine
import com.harsh.desicrics.stats.StatsEngine
import com.harsh.desicrics.store.MatchStore
import com.harsh.desicrics.store.PlayerStore
import com.harsh.desicrics.store.SocialStore
import com.harsh.desicrics.store.StatsStore
import com.harsh.desicrics.store.TeamStore
import com.harsh.desicrics.store.UserStore
import io.ktor.http.HttpStatusCode
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Keeps the stats projection in step with matches: a match's lines are rewritten whenever it completes or stops being complete. */
class StatsProjector(private val stats: StatsStore, private val matches: MatchStore) : MatchListener {

    override suspend fun changed(before: MatchDetails?, after: MatchDetails) {
        val wasComplete = before?.match?.status == MatchStatus.COMPLETED
        val isComplete = after.match.status == MatchStatus.COMPLETED
        if (isComplete || wasComplete) stats.replace(after.match.id, StatsEngine.lines(after))
    }

    /** Projects matches that finished before stats existed. Safe to run on every start. */
    suspend fun backfill() {
        for (id in stats.completedMatchesWithoutLines()) {
            val details = matches.details(id) ?: continue
            stats.replace(id, StatsEngine.lines(details))
        }
    }
}

/** Career stats, comparisons and leaderboards, all sums over the projection. */
class StatsService(
    private val stats: StatsStore,
    private val players: PlayerStore,
    private val users: UserStore,
    private val teams: TeamStore,
    private val social: SocialStore,
) {
    suspend fun career(profileId: String, season: Int?, format: FormatFilter): CareerStats {
        if (players.summaries(listOf(profileId)).isEmpty()) throw ApiException(HttpStatusCode.NotFound, "Player not found")
        return StatsEngine.career(profileId, stats.forProfiles(listOf(profileId)).filtered(season, format))
    }

    suspend fun compare(a: String, b: String, season: Int?, format: FormatFilter): List<CareerStats> =
        listOf(career(a, season, format), career(b, season, format))

    /** The top 50 in a scope by a metric. Ties keep whoever played fewer matches first. */
    suspend fun leaderboard(
        user: FirebasePrincipal,
        scope: LeaderboardScope,
        id: String?,
        metric: LeaderboardMetric,
        season: Int?,
    ): List<LeaderboardRow> {
        val lines = when (scope) {
            LeaderboardScope.CITY -> {
                val city = id?.takeIf { it.isNotBlank() } ?: users.get(user.uid)?.city.orEmpty()
                if (city.isBlank()) return emptyList()
                stats.forProfiles(stats.profilesInCity(Cities.India.canonical(city)))
            }
            LeaderboardScope.TOURNAMENT -> stats.forTournament(id ?: throw ApiException(HttpStatusCode.BadRequest, "Which tournament?"))
            LeaderboardScope.FRIENDS -> stats.forProfiles(friendsOf(user.uid))
        }.filtered(season, FormatFilter.ALL)
        val rows = lines.groupBy { it.profileId }.map { (profile, mine) ->
            val career = StatsEngine.career(profile, mine)
            val value = when (metric) {
                LeaderboardMetric.RUNS -> career.batting.runs
                LeaderboardMetric.WICKETS -> career.bowling.wickets
                LeaderboardMetric.MVP -> career.mvpTenths
                LeaderboardMetric.SIXES -> career.batting.sixes
                LeaderboardMetric.CATCHES -> career.fielding.catches
            }
            Triple(profile, value, career.matches)
        }.filter { it.second > 0 }.sortedWith(compareByDescending<Triple<String, Int, Int>> { it.second }.thenBy { it.third }).take(50)
        val summaries = players.summaries(rows.map { it.first })
        return rows.mapNotNull { (profile, value, matches) -> summaries[profile]?.let { LeaderboardRow(it, value, matches) } }
    }

    /** The caller, the players they follow, and everyone in their teams. */
    private suspend fun friendsOf(uid: String): Set<String> {
        val followed = social.follows(uid).filter { it.first == FollowType.PLAYER }.map { it.second }
        val teammates = teams.forPlayer(uid).mapNotNull { teams.get(it.id) }.flatMap { team -> team.members.map { it.player.profileId } }
        return (followed + teammates + uid).toSet()
    }

    @OptIn(ExperimentalTime::class)
    private fun List<PlayerMatchLine>.filtered(season: Int?, format: FormatFilter) = filter { line ->
        line.oversPerInnings in format.overs &&
            (season == null || Instant.fromEpochMilliseconds(line.playedAt).toLocalDateTime(TimeZone.UTC).year == season)
    }
}
