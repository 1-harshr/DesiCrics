package com.harsh.desicrics.model

import kotlinx.serialization.Serializable

/** A third of the scheduled overs: the start, the middle and the finish of an innings. */
@Serializable
enum class InningsPhase { START, MIDDLE, FINISH }

@Serializable
data class WinRecord(val played: Int = 0, val won: Int = 0)

/** Runs, legal balls and wickets in one phase, added up over matches. */
@Serializable
data class PhaseStat(val phase: InningsPhase, val runs: Int = 0, val balls: Int = 0, val wickets: Int = 0) {
    val runRate: Double get() = if (balls == 0) 0.0 else runs * 6.0 / balls
}

@Serializable
data class Performer(val name: String, val profileId: String? = null, val value: Int, val matches: Int)

/**
 * What a saved team's completed matches say about how it plays: the organiser view. Computed from
 * ball-by-ball data, so it's only as complete as the matches scored in the app.
 */
@Serializable
data class TeamInsights(
    val teamId: String,
    val played: Int = 0,
    val won: Int = 0,
    val lost: Int = 0,
    val tied: Int = 0,
    val battingFirst: WinRecord = WinRecord(),
    val chasing: WinRecord = WinRecord(),
    /** Matches where this team won the toss. */
    val tossWon: WinRecord = WinRecord(),
    /** Average first-innings total when this team batted first; null if it never has. */
    val averageFirstInnings: Int? = null,
    /** This team's scoring by phase. */
    val batting: List<PhaseStat> = emptyList(),
    /** What this team conceded by phase. */
    val bowling: List<PhaseStat> = emptyList(),
    /** How this team's batters got out, most common first. */
    val howOut: Map<DismissalKind, Int> = emptyMap(),
    val topRunScorers: List<Performer> = emptyList(),
    val topWicketTakers: List<Performer> = emptyList(),
)
