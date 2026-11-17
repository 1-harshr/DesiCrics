package com.harsh.desicrics.scoring

import com.harsh.desicrics.model.Delivery
import com.harsh.desicrics.model.DismissalKind
import com.harsh.desicrics.model.ExtraType
import com.harsh.desicrics.model.Match
import com.harsh.desicrics.model.MatchStatus

/**
 * Whether [delivery] can be the next ball given the match so far. Returns a reason it can't, or null.
 * The server enforces this so a buggy or hostile client can't corrupt a scorecard.
 */
fun MatchState.rejectionReason(delivery: Delivery, nextSeq: Int): String? {
    val innings = current
    val batting = innings.battingTeam.players.map { it.id }.toSet()
    val bowling = innings.bowlingTeam.players.map { it.id }.toSet()
    val outOrRetired = innings.batters.filter { it.isOut }.map { it.player.id }.toSet()
    val crease = setOf(delivery.strikerId, delivery.nonStrikerId)

    return when {
        isComplete -> "This match is already over"
        delivery.seq != nextSeq -> "The scorecard changed. Refresh and try again"
        delivery.innings != innings.number -> "That ball is for the wrong innings"
        delivery.strikerId == delivery.nonStrikerId -> "Striker and non-striker must be different players"
        !batting.containsAll(crease) -> "Both batters must be from ${innings.battingTeam.name}"
        crease.any { it in outOrRetired } -> "That batter is already out"
        innings.strikerId != null && innings.strikerId != delivery.strikerId -> "Wrong striker for this ball"
        innings.nonStrikerId != null && innings.nonStrikerId != delivery.nonStrikerId -> "Wrong non-striker for this ball"
        delivery.bowlerId !in bowling -> "The bowler must be from ${innings.bowlingTeam.name}"
        innings.bowlerId != null && innings.bowlerId != delivery.bowlerId -> "The over isn't finished yet"
        innings.bowlerId == null && delivery.bowlerId == innings.previousOverBowlerId -> "A bowler can't bowl two overs in a row"
        delivery.runsOffBat !in 0..7 -> "Runs off the bat must be between 0 and 7"
        else -> extrasError(delivery) ?: wicketError(delivery)
    }
}

private fun extrasError(delivery: Delivery): String? = when (delivery.extra) {
    null -> "A legal ball can't have extra runs".takeIf { delivery.extraRuns != 0 }
    ExtraType.WIDE -> "A wide is 1–5 runs with nothing off the bat"
        .takeIf { delivery.runsOffBat != 0 || delivery.extraRuns !in 1..5 }
    ExtraType.NO_BALL -> "A no-ball carries exactly 1 penalty run".takeIf { delivery.extraRuns != 1 }
    ExtraType.BYE, ExtraType.LEG_BYE -> "Byes and leg byes are 1–4 runs with nothing off the bat"
        .takeIf { delivery.runsOffBat != 0 || delivery.extraRuns !in 1..4 }
}

private fun wicketError(delivery: Delivery): String? {
    val wicket = delivery.wicket ?: return null
    return when {
        wicket.playerOutId != delivery.strikerId && wicket.playerOutId != delivery.nonStrikerId ->
            "Only a batter at the crease can be out"
        wicket.kind != DismissalKind.RUN_OUT && wicket.playerOutId != delivery.strikerId ->
            "Only the striker can be out ${wicket.kind.displayName.lowercase()}"
        delivery.extra != null -> "Wickets off extras aren't supported yet"
        else -> null
    }
}

/** The match row with its denormalised summary, status and result refreshed from [state]. */
fun Match.withScore(state: MatchState): Match = copy(
    scoreSummary = state.scoreSummary,
    status = if (state.isComplete) MatchStatus.COMPLETED else MatchStatus.IN_PROGRESS,
    result = state.result,
)
