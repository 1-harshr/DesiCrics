package com.harsh.desicrics.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Something the scorer records between balls. Events share the match's sequence numbers with
 * [Delivery], so the scorecard replays both in order and Undo removes whichever came last.
 */
@Serializable
sealed interface MatchEvent {
    val seq: Int
    val innings: Int
    val timestamp: Long

    /** A batter leaves the crease. Retired hurt isn't out and can bat again later; retired out is a wicket. */
    @Serializable
    @SerialName("retire")
    data class Retire(
        override val seq: Int,
        override val innings: Int,
        val playerId: String,
        val out: Boolean,
        override val timestamp: Long = 0,
    ) : MatchEvent

    /** The batters change ends, e.g. they crossed before a catch, or the strike was marked wrong. */
    @Serializable
    @SerialName("swap_ends")
    data class SwapEnds(
        override val seq: Int,
        override val innings: Int,
        override val timestamp: Long = 0,
    ) : MatchEvent

    /**
     * Penalty runs. To the batting side, they go on the current innings. To the fielding side, they go on
     * that side's own innings: the one they already batted, or the one they bat next.
     */
    @Serializable
    @SerialName("penalty")
    data class Penalty(
        override val seq: Int,
        override val innings: Int,
        val toBattingSide: Boolean,
        val runs: Int,
        override val timestamp: Long = 0,
    ) : MatchEvent

    /** The batting side ends its innings now. In the second innings that concedes the chase. */
    @Serializable
    @SerialName("declare")
    data class Declare(
        override val seq: Int,
        override val innings: Int,
        override val timestamp: Long = 0,
    ) : MatchEvent

    /**
     * Rain or bad light cut the match short. In the first innings, [overs] applies to both innings. In the
     * second it's the chase's overs, and [target] the runs to win, worked out by whatever method the match
     * uses (DLS, a par table, or agreement).
     */
    @Serializable
    @SerialName("revise_target")
    data class ReviseTarget(
        override val seq: Int,
        override val innings: Int,
        val overs: Int,
        val target: Int? = null,
        override val timestamp: Long = 0,
    ) : MatchEvent

    /** The bowler can't finish the over (injury, say); the scorer picks who bowls the rest of it. */
    @Serializable
    @SerialName("replace_bowler")
    data class ReplaceBowler(
        override val seq: Int,
        override val innings: Int,
        override val timestamp: Long = 0,
    ) : MatchEvent
}
