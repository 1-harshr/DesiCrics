package com.harsh.desicrics.scoring

import com.harsh.desicrics.model.Delivery
import com.harsh.desicrics.model.ExtraType
import com.harsh.desicrics.model.InningsScore
import com.harsh.desicrics.model.Match
import com.harsh.desicrics.model.MatchEvent
import com.harsh.desicrics.model.MatchRules
import com.harsh.desicrics.model.MatchStatus
import com.harsh.desicrics.model.RuleMode

/**
 * Why [delivery] can't be the next ball given the match so far, or null if it can: either it makes no
 * sense, or it breaks a match rule the scorer chose to enforce. Checked on the device before sending and
 * again on the server, so a buggy or hostile client can't corrupt a scorecard.
 */
fun MatchState.validate(delivery: Delivery, nextSeq: Int): DeliveryError? =
    deliveryError(delivery, nextSeq) ?: ruleBreaks(delivery).firstOrNull { it.mode == RuleMode.ENFORCE }?.error

/** Match rules set to warn that [delivery] breaks. The app asks the scorer before recording it. */
fun MatchState.warnings(delivery: Delivery): List<DeliveryError> =
    ruleBreaks(delivery).filter { it.mode == RuleMode.WARN }.map { it.error }

/** A match rule a ball would break, and how firmly the match holds it. */
data class RuleBreak(val error: DeliveryError, val mode: RuleMode)

/**
 * The rule [bowlerId] would break by bowling the next ball, if any, so the bowler picker can hide or flag them.
 * [RuleMode.OFF] rules never show up here.
 */
fun MatchState.bowlerRuleBreak(bowlerId: String): RuleBreak? {
    val rules = match.rules
    val card = current.bowlers.firstOrNull { it.player.id == bowlerId }
    return when {
        rules.consecutiveOversMode != RuleMode.OFF && bowlerId == current.previousOverBowlerId ->
            RuleBreak(DeliveryError.CONSECUTIVE_OVERS, rules.consecutiveOversMode)
        rules.bowlerOverLimitMode != RuleMode.OFF && card != null &&
            card.legalBalls >= rules.overLimit(current.maxBalls / 6) * 6 ->
            RuleBreak(DeliveryError.BOWLER_OVER_LIMIT, rules.bowlerOverLimitMode)
        else -> null
    }
}

/** Every rule [delivery] breaks that the match hasn't switched off. */
private fun MatchState.ruleBreaks(delivery: Delivery): List<RuleBreak> {
    val rules = match.rules
    val innings = current
    val crease = listOfNotNull(delivery.strikerId, delivery.nonStrikerId)
    return listOfNotNull(
        RuleBreak(DeliveryError.RETIRED_CANNOT_RETURN, rules.retiredReturnMode)
            .takeIf { crease.any { it in innings.returnBlocked } },
        RuleBreak(DeliveryError.MUST_RETIRE, rules.retireAtMode).takeIf {
            innings.batters.any { it.player.id in crease && it.runs >= rules.retireAtRuns && it.retiredAtWickets == null }
        },
        bowlerRuleBreak(delivery.bowlerId),
        RuleBreak(DeliveryError.NOT_OUT_ON_FREE_HIT, rules.freeHitMode).takeIf {
            val kind = delivery.wicket?.kind
            innings.isFreeHit && kind != null && !kind.possibleOn(ExtraType.NO_BALL)
        },
    ).filter { it.mode != RuleMode.OFF }
}

private fun MatchState.deliveryError(delivery: Delivery, nextSeq: Int): DeliveryError? {
    val innings = current
    val batting = innings.battingTeam.players.map { it.id }.toSet()
    val bowling = innings.bowlingTeam.players.map { it.id }.toSet()
    val out = innings.batters.filter { it.isOut }.map { it.player.id }.toSet()
    val crease = setOfNotNull(delivery.strikerId, delivery.nonStrikerId)

    return when {
        isComplete -> DeliveryError.MATCH_OVER
        delivery.seq != nextSeq -> DeliveryError.STALE
        delivery.innings != innings.number -> DeliveryError.WRONG_INNINGS
        delivery.strikerId == delivery.nonStrikerId -> DeliveryError.SAME_BATTER
        // Batting alone is only for the last batter, when the match lets the last man stand.
        delivery.nonStrikerId == null && (!match.rules.lastManStands || innings.canComeIn.any { it.id != delivery.strikerId }) ->
            DeliveryError.NON_STRIKER_MISSING
        !batting.containsAll(crease) -> DeliveryError.BATTER_NOT_IN_TEAM
        crease.any { it in out } -> DeliveryError.BATTER_ALREADY_OUT
        innings.strikerId != null && innings.strikerId != delivery.strikerId -> DeliveryError.WRONG_STRIKER
        innings.nonStrikerId != null && innings.nonStrikerId != delivery.nonStrikerId -> DeliveryError.WRONG_NON_STRIKER
        delivery.bowlerId !in bowling -> DeliveryError.BOWLER_NOT_IN_TEAM
        // Mid-over bowler changes go through MatchEvent.ReplaceBowler. Whether one bowler can bowl two overs
        // in a row is a match rule (ruleBreaks), since small local sides often have to.
        innings.bowlerId != null && innings.bowlerId != delivery.bowlerId -> DeliveryError.OVER_NOT_FINISHED
        delivery.runsOffBat !in 0..7 -> DeliveryError.RUNS_OUT_OF_RANGE
        delivery.zone != null && delivery.runsOffBat == 0 -> DeliveryError.ZONE_WITHOUT_RUNS
        else -> extrasError(delivery, match.rules) ?: wicketError(delivery, bowling)
    }
}

/** English message for API error responses; the app shows its own localised text for [validate]. */
fun MatchState.rejectionReason(delivery: Delivery, nextSeq: Int): String? = validate(delivery, nextSeq)?.message

/** Why [event] can't be recorded next, or null if it can. */
fun MatchState.validate(event: MatchEvent, nextSeq: Int): DeliveryError? {
    val innings = current
    return when {
        isComplete -> DeliveryError.MATCH_OVER
        event.seq != nextSeq -> DeliveryError.STALE
        event.innings != innings.number -> DeliveryError.WRONG_INNINGS
        else -> when (event) {
            is MatchEvent.Retire ->
                DeliveryError.BATTER_NOT_AT_CREASE.takeIf { event.playerId != innings.strikerId && event.playerId != innings.nonStrikerId }
            is MatchEvent.SwapEnds ->
                DeliveryError.BATTERS_MISSING.takeIf { innings.strikerId == null || innings.nonStrikerId == null }
            is MatchEvent.ReplaceBowler -> DeliveryError.NO_BOWLER_TO_REPLACE.takeIf { innings.bowlerId == null }
            is MatchEvent.Penalty -> DeliveryError.PENALTY_OUT_OF_RANGE.takeIf { event.runs !in 1..10 }
            is MatchEvent.Declare -> null
            is MatchEvent.ReviseTarget -> when {
                event.overs !in 1..match.oversPerInnings || event.overs * 6 < innings.legalBalls -> DeliveryError.REVISED_OVERS_INVALID
                // The first innings has no target to revise; the chase must have one.
                (innings.number == 1) != (event.target == null) -> DeliveryError.REVISED_TARGET_INVALID
                event.target != null && event.target !in 1..MAX_TARGET -> DeliveryError.REVISED_TARGET_INVALID
                else -> null
            }
        }
    }
}

/** English message for API error responses. */
fun MatchState.rejectionReason(event: MatchEvent, nextSeq: Int): String? = validate(event, nextSeq)?.message

private fun extrasError(delivery: Delivery, rules: MatchRules): DeliveryError? {
    val extra = delivery.extra
    if (delivery.isWideOrNoBall &&
        (delivery.extraPenalty != rules.penalty(extra!!) || delivery.countsAsBall != rules.countsAsBall(extra))
    ) {
        return DeliveryError.EXTRA_NOT_PER_RULES
    }
    val penalty = delivery.extraPenalty
    return when (extra) {
        null -> DeliveryError.EXTRA_RUNS_ON_LEGAL_BALL.takeIf { delivery.extraRuns != 0 }
        ExtraType.WIDE -> DeliveryError.INVALID_WIDE.takeIf { delivery.runsOffBat != 0 || delivery.extraRuns !in penalty..penalty + 4 }
        ExtraType.NO_BALL -> DeliveryError.INVALID_NO_BALL.takeIf { delivery.extraRuns != penalty }
        ExtraType.BYE, ExtraType.LEG_BYE ->
            DeliveryError.INVALID_BYES.takeIf { delivery.runsOffBat != 0 || delivery.extraRuns !in 1..4 }
    } ?: DeliveryError.EXTRA_NOT_PER_RULES.takeIf { !delivery.isWideOrNoBall && delivery.countsAsBall }
}

private fun wicketError(delivery: Delivery, bowling: Set<String>): DeliveryError? {
    val wicket = delivery.wicket ?: return null
    val kind = wicket.kind
    return when {
        wicket.playerOutId != delivery.strikerId && wicket.playerOutId != delivery.nonStrikerId ->
            DeliveryError.OUT_PLAYER_NOT_AT_CREASE
        kind.strikerOnly && wicket.playerOutId != delivery.strikerId -> DeliveryError.ONLY_STRIKER_CAN_BE_OUT
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
    RUNS_OUT_OF_RANGE("Runs off the bat must be between 0 and 7"),
    EXTRA_RUNS_ON_LEGAL_BALL("A legal ball can't have extra runs"),
    INVALID_WIDE("A wide is its penalty plus up to 4 runs, with nothing off the bat"),
    INVALID_NO_BALL("A no-ball carries exactly the match's penalty"),
    EXTRA_NOT_PER_RULES("That extra doesn't match this match's rules"),
    NON_STRIKER_MISSING("Pick a non-striker. Only the last batter can bat alone, and only if the match allows it"),
    INVALID_BYES("Byes and leg byes are 1–4 runs with nothing off the bat"),
    OUT_PLAYER_NOT_AT_CREASE("Only a batter at the crease can be out"),
    ONLY_STRIKER_CAN_BE_OUT("Only the striker can be out that way"),
    DISMISSAL_NOT_POSSIBLE_ON_EXTRA("That dismissal isn't possible on this kind of ball"),
    NO_RUNS_ON_DISMISSAL("No runs count on that dismissal"),
    FIELDER_NOT_ALLOWED("That dismissal doesn't have a fielder"),
    FIELDER_NOT_IN_TEAM("The fielder must be from the fielding side"),
    BATTER_NOT_AT_CREASE("That batter isn't at the crease"),
    BATTERS_MISSING("Pick both batters first"),
    NO_BOWLER_TO_REPLACE("Nobody is bowling yet"),
    PENALTY_OUT_OF_RANGE("Penalty runs must be between 1 and 10"),
    REVISED_OVERS_INVALID("Revised overs must be no more than the match's and no fewer than already bowled"),
    REVISED_TARGET_INVALID("Only the chase has a target, and it needs one"),
    ZONE_WITHOUT_RUNS("Only a shot with runs off the bat goes on the wagon wheel"),

    // Match rules; these apply only as strictly as the scorer set them.
    RETIRED_CANNOT_RETURN("The match rules don't let that retired batter back in yet"),
    MUST_RETIRE("A batter has reached the retirement score and must retire"),
    BOWLER_OVER_LIMIT("That bowler has bowled all their overs"),
    CONSECUTIVE_OVERS("That bowler bowled the previous over"),
    NOT_OUT_ON_FREE_HIT("It's a free hit: the batter can't be out that way"),
}

/** Highest revised target the app accepts; a sanity bound, not a cricket rule. */
const val MAX_TARGET = 999

/** The match row with its denormalised summary, status and result refreshed from [state]. */
fun Match.withScore(state: MatchState): Match = copy(
    scoreSummary = state.scoreSummary,
    status = if (state.isComplete) MatchStatus.COMPLETED else MatchStatus.IN_PROGRESS,
    result = state.result,
    innings = state.innings.map { InningsScore(it.battingSide, it.runs, it.wickets, it.legalBalls, it.target, it.maxBalls) },
)
