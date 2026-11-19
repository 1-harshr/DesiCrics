package com.harsh.desicrics.model

import kotlinx.serialization.Serializable

/** A match row in SQL Connect, with its players. Deliveries are stored separately, one row per ball. */
@Serializable
data class Match(
    val id: String,
    val createdBy: String,
    val createdAt: Long,
    val teamA: Team,
    val teamB: Team,
    val oversPerInnings: Int,
    val tossWinner: TeamSide,
    val tossDecision: TossDecision,
    val status: MatchStatus = MatchStatus.SETUP,
    /** Denormalised for list screens, e.g. "Strikers 145/6 (20.0) · Royals 98/3 (12.4)". */
    val scoreSummary: String = "",
    val result: MatchResult? = null,
    /** Score of each innings started so far, for list screens that don't load the deliveries. */
    val innings: List<InningsScore> = emptyList(),
    val rules: MatchRules = MatchRules(),
    /** Who scores the match now. Starts as [createdBy]; a handover code moves it to another device's user. */
    val scorerUid: String = createdBy,
    /** When a [MatchStatus.SCHEDULED] fixture is due to start, if anyone said. */
    val scheduledAt: Long? = null,
    /** Set when the match is a tournament fixture. */
    val fixture: FixtureInfo? = null,
    val venue: String = "",
) {
    val battingFirst: TeamSide
        get() = if (tossDecision == TossDecision.BAT) tossWinner else tossWinner.other

    fun team(side: TeamSide): Team = if (side == TeamSide.A) teamA else teamB
}

/** One side of a match. [teamId] links it to a [SavedTeam] when the match was started from one. */
@Serializable
data class Team(
    val name: String,
    val players: List<Player>,
    val teamId: String? = null,
    /** Match player IDs, not profile IDs. */
    val captainId: String? = null,
    val keeperId: String? = null,
)

/**
 * A player in one match. [id] is only for this match; [profileId] links them to a user or placeholder
 * (see [PlayerSummary]) so their stats follow them. Null for a guest typed in by name.
 */
@Serializable
data class Player(
    val id: String,
    val name: String,
    val profileId: String? = null,
)

@Serializable
enum class TeamSide {
    A, B;

    val other: TeamSide get() = if (this == A) B else A
}

@Serializable
enum class TossDecision(val displayName: String) {
    BAT("Bat"),
    BOWL("Bowl"),
}

@Serializable
enum class MatchStatus {
    /** Created, but the roster isn't fully saved yet. */
    SETUP,
    /** A fixture that hasn't started, e.g. from a tournament. It gets its squads and toss when it starts. */
    SCHEDULED,
    IN_PROGRESS,
    COMPLETED,
}

/**
 * How a completed match ended. [ending] is null when it was played out: then [winner] is null only for a
 * tie. Otherwise it stopped early: abandoned (no winner, [reason] says why), conceded, or a walkover.
 */
@Serializable
data class MatchResult(
    val winner: TeamSide?,
    val margin: Int = 0,
    val marginUnit: MarginUnit? = null,
    val ending: MatchEnding? = null,
    val reason: AbandonReason? = null,
) {
    val isTie: Boolean get() = winner == null && ending == null

    /** Abandoned: nobody won, and it wasn't a tie. */
    val isNoResult: Boolean get() = ending == MatchEnding.ABANDONED

    companion object {
        val Tie = MatchResult(winner = null)
    }
}

@Serializable
enum class MarginUnit { RUNS, WICKETS }

/** A match that stopped before its innings were finished. */
@Serializable
enum class MatchEnding {
    /** Called off with no result: rain, light, the ground, anything. Before or during play. */
    ABANDONED,
    /** One team gave up during play; the other wins. */
    CONCEDED,
    /** One team didn't turn up, so the other wins without a ball bowled. */
    WALKOVER,
}

/** Why a match was abandoned. [displayName] is English, for server text; the app words it itself. */
@Serializable
enum class AbandonReason(val displayName: String) {
    RAIN("rain"),
    BAD_LIGHT("bad light"),
    GROUND("ground unavailable"),
    OTHER("abandoned"),
}

/**
 * English line for a match that stopped early ("No result (rain)", "Royals won by walkover"), or null if it
 * was played out. For server text: the stored summary, notifications and the public page.
 */
fun Match.endingLine(result: MatchResult): String? {
    val winner = result.winner?.let { team(it).name }
    return when (result.ending) {
        null -> null
        MatchEnding.ABANDONED -> "No result" + (result.reason?.takeIf { it != AbandonReason.OTHER }?.let { " (${it.displayName})" } ?: "")
        MatchEnding.CONCEDED -> "$winner won, ${team(result.winner!!.other).name} conceded"
        MatchEnding.WALKOVER -> "$winner won by walkover"
    }
}

/** One innings' total, kept on the match row so feeds can show "142/6 (16.4)" per team. */
@Serializable
data class InningsScore(
    val battingSide: TeamSide,
    val runs: Int,
    val wickets: Int,
    val legalBalls: Int,
    /** Runs to win, for the chase. Null for the first innings and for rows saved before targets were kept. */
    val target: Int? = null,
    /** The innings' ball limit, which rain can cut. Null for rows saved before it was kept. */
    val maxBalls: Int? = null,
    /** Bowled out, so net run rate counts the full [maxBalls] as faced. */
    val allOut: Boolean = false,
)
