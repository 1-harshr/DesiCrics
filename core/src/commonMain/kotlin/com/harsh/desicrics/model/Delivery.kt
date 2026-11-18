package com.harsh.desicrics.model

import kotlinx.serialization.Serializable

/**
 * One ball, keyed by (match, seq). The scorecard is always derived from the full list of
 * deliveries, which keeps undo and corrections trivial.
 */
@Serializable
data class Delivery(
    /** Position across the whole match, starting at 0. */
    val seq: Int,
    val innings: Int,
    val strikerId: String,
    /** Null when the last batter bats alone (see [MatchRules.lastManStands]). */
    val nonStrikerId: String?,
    val bowlerId: String,
    val runsOffBat: Int = 0,
    val extra: ExtraType? = null,
    /**
     * Runs credited as extras. Wide: [extraPenalty] + runs taken. No-ball: [extraPenalty].
     * Bye / leg bye: runs taken.
     */
    val extraRuns: Int = 0,
    val wicket: Wicket? = null,
    val timestamp: Long = 0,
    /**
     * The penalty a wide or no-ball gives on its own, from the match's rules. Kept on the ball so it
     * describes itself without the rules.
     */
    val extraPenalty: Int = 1,
    /** A wide or no-ball the match doesn't re-bowl, so it counts as one of the over's six. */
    val countsAsBall: Boolean = false,
) {
    /** Counts as one of the over's six balls. */
    val isLegal: Boolean get() = !isWideOrNoBall || countsAsBall

    val isWideOrNoBall: Boolean get() = extra == ExtraType.WIDE || extra == ExtraType.NO_BALL

    val totalRuns: Int get() = runsOffBat + extraRuns

    /** Runs the batters actually ran, which decides whether they swap ends. */
    val runsRun: Int get() = totalRuns - if (isWideOrNoBall) extraPenalty else 0

    /** Runs charged to the bowler: byes and leg byes are not. */
    val bowlerRuns: Int
        get() = runsOffBat + if (isWideOrNoBall) extraRuns else 0
}

@Serializable
enum class ExtraType(val shortName: String) {
    WIDE("wd"),
    NO_BALL("nb"),
    BYE("b"),
    LEG_BYE("lb"),
}

@Serializable
data class Wicket(
    val kind: DismissalKind,
    val playerOutId: String,
    /** Catcher, run-out fielder or keeper. Optional: casual scorers often skip it. */
    val fielderId: String? = null,
)

@Serializable
enum class DismissalKind(
    val displayName: String,
    val creditedToBowler: Boolean,
    /** Whether a fielder can be named: the catcher, the run-out fielder or the keeper. */
    val hasFielder: Boolean,
    /** Whether runs completed before the dismissal still count. */
    val allowsRuns: Boolean,
    /** Only the striker can be out this way; otherwise either batter can. */
    val strikerOnly: Boolean = true,
) {
    BOWLED("Bowled", creditedToBowler = true, hasFielder = false, allowsRuns = false),
    CAUGHT("Caught", creditedToBowler = true, hasFielder = true, allowsRuns = false),
    LBW("LBW", creditedToBowler = true, hasFielder = false, allowsRuns = false),
    RUN_OUT("Run out", creditedToBowler = false, hasFielder = true, allowsRuns = true, strikerOnly = false),
    STUMPED("Stumped", creditedToBowler = true, hasFielder = true, allowsRuns = false),
    HIT_WICKET("Hit wicket", creditedToBowler = true, hasFielder = false, allowsRuns = false),
    OBSTRUCTING_FIELD("Obstructing the field", creditedToBowler = false, hasFielder = false, allowsRuns = true, strikerOnly = false),
    HIT_BALL_TWICE("Hit the ball twice", creditedToBowler = false, hasFielder = false, allowsRuns = false),

    /** A local rule ("one tip one hand", over the wall). Either batter, runs can count, not the bowler's. */
    OTHER("Out", creditedToBowler = false, hasFielder = false, allowsRuns = true, strikerOnly = false);

    /** Whether this dismissal can happen on a ball with [extra]. */
    fun possibleOn(extra: ExtraType?): Boolean = when (this) {
        RUN_OUT, OBSTRUCTING_FIELD, OTHER -> true
        STUMPED, HIT_WICKET -> extra == null || extra == ExtraType.WIDE
        HIT_BALL_TWICE -> extra == null || extra == ExtraType.NO_BALL
        BOWLED, CAUGHT, LBW -> extra == null
    }
}
