package com.harsh.desicrics.model

import kotlinx.serialization.Serializable

/**
 * The playing conditions the scorer picked for one match. Fixed once the match is created, so replaying
 * the balls always gives the same scorecard. The defaults are the app's original, most lenient behaviour,
 * which is also what matches created before rules existed get.
 *
 * Restrictions have a [RuleMode]; the rest (penalty runs, re-bowling, last man stands) change how balls are
 * scored and simply apply.
 */
@Serializable
data class MatchRules(
    /** The last batter may bat alone, so a side is all out only when every player is out. */
    val lastManStands: Boolean = false,

    /** When a batter who retired hurt (or retired at [retireAtRuns]) may bat again. */
    val retiredReturn: RetiredReturn = RetiredReturn.ANY_TIME,
    val retiredReturnMode: RuleMode = RuleMode.OFF,

    /** A batter on this many runs must retire, not out, before the next ball. */
    val retireAtRuns: Int = 25,
    val retireAtMode: RuleMode = RuleMode.OFF,

    /** Most overs one bowler may bowl. Null means an even share: a fifth of the overs, rounded up. */
    val bowlerOverLimit: Int? = null,
    val bowlerOverLimitMode: RuleMode = RuleMode.OFF,

    /** The bowler of the previous over can't bowl this one, not even to finish it. */
    val consecutiveOversMode: RuleMode = RuleMode.OFF,

    /** Runs a wide or no-ball gives on its own, before anything run or hit. 1 in the laws. */
    val widePenalty: Int = 1,
    val noBallPenalty: Int = 1,
    /** Whether a wide or no-ball is bowled again. False means it counts as one of the over's six. */
    val wideRebowled: Boolean = true,
    val noBallRebowled: Boolean = true,

    /** The ball after a no-ball is a free hit: the batter can't be out in any way a no-ball rules out. */
    val freeHitMode: RuleMode = RuleMode.OFF,

    /**
     * Ask where each scoring shot went, for the wagon wheel. New matches get it on through the presets; the
     * bare default stays off because rules are stored without their defaults, so matches saved before it
     * was turned on keep reading as off.
     */
    val wagonWheel: Boolean = false,
    /** Fielding-restriction overs at the start of each innings, shown on the scoreboard. 0 for none. */
    val powerplayOvers: Int = 0,
) {
    /** The bowler's over limit for a match of [oversPerInnings] overs. */
    fun overLimit(oversPerInnings: Int): Int = bowlerOverLimit ?: ((oversPerInnings + 4) / 5)

    fun penalty(extra: ExtraType): Int = when (extra) {
        ExtraType.WIDE -> widePenalty
        ExtraType.NO_BALL -> noBallPenalty
        ExtraType.BYE, ExtraType.LEG_BYE -> 0
    }

    /** Whether a ball of type [extra] is one of the over's six. */
    fun countsAsBall(extra: ExtraType?): Boolean = when (extra) {
        ExtraType.WIDE -> !wideRebowled
        ExtraType.NO_BALL -> !noBallRebowled
        null, ExtraType.BYE, ExtraType.LEG_BYE -> true
    }

    /** Checked on the device and again on the server. */
    fun isValid(): Boolean =
        retireAtRuns in RETIRE_AT && widePenalty in PENALTY && noBallPenalty in PENALTY &&
            (bowlerOverLimit == null || bowlerOverLimit in 1..50) && powerplayOvers in 0..50

    companion object {
        val RETIRE_AT = 5..200
        val PENALTY = 0..2

        /** The original, most lenient behaviour, with the wagon wheel on: where new matches start. */
        val Relaxed = MatchRules(wagonWheel = true)

        /** The laws plus the usual limited-overs playing conditions, all enforced. */
        val Standard = MatchRules(
            retiredReturn = RetiredReturn.AFTER_WICKET,
            retiredReturnMode = RuleMode.ENFORCE,
            bowlerOverLimitMode = RuleMode.ENFORCE,
            consecutiveOversMode = RuleMode.ENFORCE,
            freeHitMode = RuleMode.ENFORCE,
            wagonWheel = true,
            powerplayOvers = 6,
        )

        /** Standard conditions, but the scorer is warned instead of stopped. */
        val Club = Standard.copy(
            retiredReturnMode = RuleMode.WARN,
            bowlerOverLimitMode = RuleMode.WARN,
            consecutiveOversMode = RuleMode.WARN,
            freeHitMode = RuleMode.WARN,
        )

        /** Street and box cricket: last man bats on, retired batters come back at the end, no bowling limits. */
        val Gully = MatchRules(
            lastManStands = true,
            retiredReturn = RetiredReturn.AT_END,
            retiredReturnMode = RuleMode.ENFORCE,
            wagonWheel = true,
        )
    }
}

/** How firmly a rule holds when a ball breaks it. */
@Serializable
enum class RuleMode {
    OFF,
    /** The scorer is told and can record the ball anyway. */
    WARN,
    /** The ball can't be recorded, on the device or the server. */
    ENFORCE,
}

@Serializable
enum class RetiredReturn {
    ANY_TIME,
    /** Once a wicket has fallen since they retired. */
    AFTER_WICKET,
    /** Once nobody else is left to come in. */
    AT_END,
    NEVER,
}
