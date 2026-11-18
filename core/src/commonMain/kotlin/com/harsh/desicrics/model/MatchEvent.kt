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

    /** The bowler can't finish the over (injury, say); the scorer picks who bowls the rest of it. */
    @Serializable
    @SerialName("replace_bowler")
    data class ReplaceBowler(
        override val seq: Int,
        override val innings: Int,
        override val timestamp: Long = 0,
    ) : MatchEvent
}
