package com.harsh.desicrics.model

import kotlinx.serialization.Serializable

/**
 * What one player did in one completed match: the unit career stats and leaderboards add up. Written by
 * the server whenever a match completes, never by the app.
 */
@Serializable
data class PlayerMatchLine(
    val profileId: String,
    val matchId: String,
    val playedAt: Long,
    val team: String,
    val opponent: String,
    val oversPerInnings: Int,
    val tournamentId: String? = null,
    val batted: Boolean = false,
    val runs: Int = 0,
    val balls: Int = 0,
    val fours: Int = 0,
    val sixes: Int = 0,
    val notOut: Boolean = false,
    val bowledBalls: Int = 0,
    val runsConceded: Int = 0,
    val wickets: Int = 0,
    val maidens: Int = 0,
    val catches: Int = 0,
    val runOuts: Int = 0,
    val stumpings: Int = 0,
    /** Tenths of a point, so totals stay exact. */
    val mvpTenths: Int = 0,
    val playerOfTheMatch: Boolean = false,
    /** Runs off the bat per wagon wheel zone, when the match kept one. */
    val zones: Map<FieldZone, Int> = emptyMap(),
    val won: Boolean = false,
    /** The opposing saved team, if it was one, for rankings weighted by opposition strength. */
    val opponentTeamId: String? = null,
)

/** Match length, for filtering stats: the overs a side gets. */
@Serializable
enum class FormatFilter(val overs: IntRange) {
    ALL(0..Int.MAX_VALUE),
    /** Up to 10 overs a side: box, gully and T10. */
    SHORT(0..10),
    /** 11 to 20 overs. */
    T20(11..20),
    /** More than 20. */
    LONG(21..Int.MAX_VALUE),
}

@Serializable
data class BattingStats(
    val innings: Int = 0,
    val notOuts: Int = 0,
    val runs: Int = 0,
    val balls: Int = 0,
    val fours: Int = 0,
    val sixes: Int = 0,
    val highest: Int = 0,
    val highestNotOut: Boolean = false,
    val fifties: Int = 0,
    val hundreds: Int = 0,
    val ducks: Int = 0,
) {
    /** Runs per dismissal; null until they've been out. */
    val average: Double? get() = (innings - notOuts).takeIf { it > 0 }?.let { runs.toDouble() / it }
    val strikeRate: Double get() = if (balls == 0) 0.0 else runs * 100.0 / balls
}

@Serializable
data class BowlingStats(
    val innings: Int = 0,
    val balls: Int = 0,
    val runs: Int = 0,
    val wickets: Int = 0,
    val maidens: Int = 0,
    val bestWickets: Int = 0,
    val bestRuns: Int = 0,
    val threeWickets: Int = 0,
    val fiveWickets: Int = 0,
) {
    val economy: Double get() = if (balls == 0) 0.0 else runs * 6.0 / balls
    /** Runs per wicket; null until they've taken one. */
    val average: Double? get() = wickets.takeIf { it > 0 }?.let { runs.toDouble() / it }
}

@Serializable
data class FieldingStats(val catches: Int = 0, val runOuts: Int = 0, val stumpings: Int = 0)

/** One innings for the form chart. */
@Serializable
data class FormEntry(val matchId: String, val playedAt: Long, val runs: Int, val notOut: Boolean, val wickets: Int, val batted: Boolean)

@Serializable
enum class Badge {
    FIRST_FIFTY, CENTURY, FIVE_WICKET_HAUL, RUNS_500, RUNS_1000, WICKETS_25, WICKETS_50, MATCHES_25, MATCHES_50, PLAYER_OF_THE_MATCH,
}

@Serializable
data class CareerStats(
    val profileId: String,
    val matches: Int = 0,
    val won: Int = 0,
    val batting: BattingStats = BattingStats(),
    val bowling: BowlingStats = BowlingStats(),
    val fielding: FieldingStats = FieldingStats(),
    val mvpTenths: Int = 0,
    val playerOfTheMatch: Int = 0,
    /** Up to the last 10 matches, oldest first. */
    val form: List<FormEntry> = emptyList(),
    val zones: Map<FieldZone, Int> = emptyMap(),
    val badges: List<Badge> = emptyList(),
)

@Serializable
enum class LeaderboardScope { CITY, TOURNAMENT, FRIENDS }

@Serializable
enum class LeaderboardMetric { RUNS, WICKETS, MVP, SIXES, CATCHES }

@Serializable
data class LeaderboardRow(val player: PlayerSummary, val value: Int, val matches: Int)
