package com.harsh.desicrics.scoring

import com.harsh.desicrics.model.Delivery
import com.harsh.desicrics.model.ExtraType
import com.harsh.desicrics.model.InningsScore
import com.harsh.desicrics.model.Match
import com.harsh.desicrics.model.MatchStatus

/**
 * Why [delivery] can't be the next ball given the match so far, or null if it can.
 * Checked on the device before sending and again on the server, so a buggy or hostile client
 * can't corrupt a scorecard.
 */
fun MatchState.validate(delivery: Delivery, nextSeq: Int): DeliveryError? {
    val innings = current
    val batting = innings.battingTeam.players.map { it.id }.toSet()
    val bowling = innings.bowlingTeam.players.map { it.id }.toSet()
    val out = innings.batters.filter { it.isOut }.map { it.player.id }.toSet()
    val crease = setOf(delivery.strikerId, delivery.nonStrikerId)

    return when {
        isComplete -> DeliveryError.MATCH_OVER
        delivery.seq != nextSeq -> DeliveryError.STALE
        delivery.innings != innings.number -> DeliveryError.WRONG_INNINGS
        delivery.strikerId == delivery.nonStrikerId -> DeliveryError.SAME_BATTER
        !batting.containsAll(crease) -> DeliveryError.BATTER_NOT_IN_TEAM
        crease.any { it in out } -> DeliveryError.BATTER_ALREADY_OUT
        innings.strikerId != null && innings.strikerId != delivery.strikerId -> DeliveryError.WRONG_STRIKER
        innings.nonStrikerId != null && innings.nonStrikerId != delivery.nonStrikerId -> DeliveryError.WRONG_NON_STRIKER
        delivery.bowlerId !in bowling -> DeliveryError.BOWLER_NOT_IN_TEAM
        innings.bowlerId != null && innings.bowlerId != delivery.bowlerId -> DeliveryError.OVER_NOT_FINISHED
        innings.bowlerId == null && delivery.bowlerId == innings.previousOverBowlerId -> DeliveryError.CONSECUTIVE_OVERS
        delivery.runsOffBat !in 0..7 -> DeliveryError.RUNS_OUT_OF_RANGE
        else -> extrasError(delivery) ?: wicketError(delivery, bowling)
    }
}

/** English message for API error responses; the app shows its own localised text for [validate]. */
fun MatchState.rejectionReason(delivery: Delivery, nextSeq: Int): String? = validate(delivery, nextSeq)?.message

private fun extrasError(delivery: Delivery): DeliveryError? = when (delivery.extra) {
    null -> DeliveryError.EXTRA_RUNS_ON_LEGAL_BALL.takeIf { delivery.extraRuns != 0 }
    ExtraType.WIDE -> DeliveryError.INVALID_WIDE.takeIf { delivery.runsOffBat != 0 || delivery.extraRuns !in 1..5 }
    ExtraType.NO_BALL -> DeliveryError.INVALID_NO_BALL.takeIf { delivery.extraRuns != 1 }
    ExtraType.BYE, ExtraType.LEG_BYE ->
        DeliveryError.INVALID_BYES.takeIf { delivery.runsOffBat != 0 || delivery.extraRuns !in 1..4 }
}

private fun wicketError(delivery: Delivery, bowling: Set<String>): DeliveryError? {
    val wicket = delivery.wicket ?: return null
    val kind = wicket.kind
    return when {
        wicket.playerOutId != delivery.strikerId && wicket.playerOutId != delivery.nonStrikerId ->
            DeliveryError.OUT_PLAYER_NOT_AT_CREASE
        kind.creditedToBowler && wicket.playerOutId != delivery.strikerId -> DeliveryError.ONLY_STRIKER_CAN_BE_OUT
        !kind.possibleOn(delivery.extra) -> DeliveryError.DISMISSAL_NOT_POSSIBLE_ON_EXTRA
        !kind.allowsRuns && delivery.runsRun > 0 -> DeliveryError.NO_RUNS_ON_DISMISSAL
        wicket.fielderId != null && !kind.hasFielder -> DeliveryError.FIELDER_NOT_ALLOWED
        wicket.fielderId != null && wicket.fielderId !in bowling -> DeliveryError.FIELDER_NOT_IN_TEAM
        else -> null
    }
}

enum class DeliveryError(val message: String) {
    MATCH_OVER("This match is already over"),
    STALE("The scorecard changed. Refresh and try again"),
    WRONG_INNINGS("That ball is for the wrong innings"),
    SAME_BATTER("Striker and non-striker must be different players"),
    BATTER_NOT_IN_TEAM("Both batters must be from the batting side"),
    BATTER_ALREADY_OUT("That batter is already out"),
    WRONG_STRIKER("Wrong striker for this ball"),
    WRONG_NON_STRIKER("Wrong non-striker for this ball"),
    BOWLER_NOT_IN_TEAM("The bowler must be from the fielding side"),
    OVER_NOT_FINISHED("The over isn't finished yet"),
    CONSECUTIVE_OVERS("A bowler can't bowl two overs in a row"),
    RUNS_OUT_OF_RANGE("Runs off the bat must be between 0 and 7"),
    EXTRA_RUNS_ON_LEGAL_BALL("A legal ball can't have extra runs"),
    INVALID_WIDE("A wide is 1–5 runs with nothing off the bat"),
    INVALID_NO_BALL("A no-ball carries exactly 1 penalty run"),
    INVALID_BYES("Byes and leg byes are 1–4 runs with nothing off the bat"),
    OUT_PLAYER_NOT_AT_CREASE("Only a batter at the crease can be out"),
    ONLY_STRIKER_CAN_BE_OUT("Only the striker can be out that way"),
    DISMISSAL_NOT_POSSIBLE_ON_EXTRA("That dismissal isn't possible on this kind of ball"),
    NO_RUNS_ON_DISMISSAL("No runs count on that dismissal"),
    FIELDER_NOT_ALLOWED("That dismissal doesn't have a fielder"),
    FIELDER_NOT_IN_TEAM("The fielder must be from the fielding side"),
}

/** The match row with its denormalised summary, status and result refreshed from [state]. */
fun Match.withScore(state: MatchState): Match = copy(
    scoreSummary = state.scoreSummary,
    status = if (state.isComplete) MatchStatus.COMPLETED else MatchStatus.IN_PROGRESS,
    result = state.result,
    innings = state.innings.map { InningsScore(it.battingSide, it.runs, it.wickets, it.legalBalls) },
)
