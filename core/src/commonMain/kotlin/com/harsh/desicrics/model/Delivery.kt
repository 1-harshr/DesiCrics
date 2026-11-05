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
    val nonStrikerId: String,
    val bowlerId: String,
    val runsOffBat: Int = 0,
    val extra: ExtraType? = null,
    /**
     * Runs credited as extras. Wide: 1 + runs taken. No-ball: the 1-run penalty.
     * Bye / leg bye: runs taken.
     */
    val extraRuns: Int = 0,
    val wicket: Wicket? = null,
    val timestamp: Long = 0,
) {
    val isLegal: Boolean get() = extra != ExtraType.WIDE && extra != ExtraType.NO_BALL

    val totalRuns: Int get() = runsOffBat + extraRuns

    /** Runs the batters actually ran, which decides whether they swap ends. */
    val runsRun: Int get() = totalRuns - if (isLegal) 0 else 1

    /** Runs charged to the bowler: byes and leg byes are not. */
    val bowlerRuns: Int
        get() = runsOffBat + if (extra == ExtraType.WIDE || extra == ExtraType.NO_BALL) extraRuns else 0
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
)

@Serializable
enum class DismissalKind(val displayName: String, val creditedToBowler: Boolean) {
    BOWLED("Bowled", true),
    CAUGHT("Caught", true),
    LBW("LBW", true),
    RUN_OUT("Run out", false),
    STUMPED("Stumped", true),
    HIT_WICKET("Hit wicket", true),
}
