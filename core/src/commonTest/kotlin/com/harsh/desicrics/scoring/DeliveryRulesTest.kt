package com.harsh.desicrics.scoring

import com.harsh.desicrics.model.Delivery
import com.harsh.desicrics.model.DismissalKind
import com.harsh.desicrics.model.ExtraType
import com.harsh.desicrics.model.Match
import com.harsh.desicrics.model.Player
import com.harsh.desicrics.model.Team
import com.harsh.desicrics.model.TeamSide
import com.harsh.desicrics.model.TossDecision
import com.harsh.desicrics.model.Wicket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeliveryRulesTest {

    private val match = Match(
        id = "m1",
        createdBy = "u1",
        createdAt = 0,
        teamA = Team("Strikers", listOf(Player("a1", "Asha"), Player("a2", "Bilal"), Player("a3", "Chetan"))),
        teamB = Team("Royals", listOf(Player("b1", "Dev"), Player("b2", "Esha"))),
        oversPerInnings = 2,
        tossWinner = TeamSide.A,
        tossDecision = TossDecision.BAT,
    )

    private fun ball(seq: Int, striker: String = "a1", nonStriker: String = "a2", bowler: String = "b1", runs: Int = 0) =
        Delivery(seq, innings = 1, strikerId = striker, nonStrikerId = nonStriker, bowlerId = bowler, runsOffBat = runs)

    private fun reason(history: List<Delivery>, next: Delivery) =
        ScoringEngine.compute(match, history).validate(next, nextSeq = history.size)

    @Test
    fun acceptsAValidBall() {
        assertNull(reason(emptyList(), ball(0, runs = 4)))
    }

    @Test
    fun rejectsStaleSequenceNumbers() {
        assertEquals(DeliveryError.STALE, reason(listOf(ball(0)), ball(0)))
    }

    @Test
    fun rejectsPlayersFromTheWrongTeam() {
        assertEquals(DeliveryError.BATTER_NOT_IN_TEAM, reason(emptyList(), ball(0, striker = "b2")))
        assertEquals(DeliveryError.BOWLER_NOT_IN_TEAM, reason(emptyList(), ball(0, bowler = "a3")))
    }

    @Test
    fun rejectsTheWrongStrikerAfterRotation() {
        // After a single the batters have crossed, so a1 can't still be on strike.
        assertEquals(DeliveryError.WRONG_STRIKER, reason(listOf(ball(0, runs = 1)), ball(1, striker = "a1", nonStriker = "a2")))
    }

    @Test
    fun rejectsTheSameBowlerForConsecutiveOvers() {
        val over = (0 until 6).map { ball(it) }
        val next = ball(6, striker = "a2", nonStriker = "a1", bowler = "b1")
        assertEquals(DeliveryError.CONSECUTIVE_OVERS, reason(over, next))
    }

    @Test
    fun rejectsDismissedBattersAndImpossibleWickets() {
        val bowled = ball(0).copy(wicket = Wicket(DismissalKind.BOWLED, "a1"))
        assertEquals(DeliveryError.BATTER_ALREADY_OUT, reason(listOf(bowled), ball(1, striker = "a1", nonStriker = "a2")))
        val stumpedNonStriker = ball(0).copy(wicket = Wicket(DismissalKind.STUMPED, "a2"))
        assertEquals(DeliveryError.ONLY_STRIKER_CAN_BE_OUT, reason(emptyList(), stumpedNonStriker))
    }

    @Test
    fun rejectsMalformedExtras() {
        val wideWithBatRuns = ball(0, runs = 2).copy(extra = ExtraType.WIDE, extraRuns = 1)
        assertEquals(DeliveryError.INVALID_WIDE, reason(emptyList(), wideWithBatRuns))
        assertEquals(DeliveryError.EXTRA_RUNS_ON_LEGAL_BALL, reason(emptyList(), ball(0).copy(extraRuns = 3)))
    }

    @Test
    fun allowsRunOutsOffAnyBallAndStumpingsOffAWide() {
        val runOutOffNoBall = ball(0, runs = 1).copy(
            extra = ExtraType.NO_BALL, extraRuns = 1, wicket = Wicket(DismissalKind.RUN_OUT, "a2", fielderId = "b2"),
        )
        assertNull(reason(emptyList(), runOutOffNoBall))
        val runOutOffBye = ball(0).copy(extra = ExtraType.BYE, extraRuns = 1, wicket = Wicket(DismissalKind.RUN_OUT, "a1"))
        assertNull(reason(emptyList(), runOutOffBye))
        val stumpedOffWide = ball(0).copy(extra = ExtraType.WIDE, extraRuns = 1, wicket = Wicket(DismissalKind.STUMPED, "a1", "b2"))
        assertNull(reason(emptyList(), stumpedOffWide))
    }

    @Test
    fun rejectsDismissalsThatCantHappenOnExtras() {
        val bowledOffNoBall = ball(0).copy(extra = ExtraType.NO_BALL, extraRuns = 1, wicket = Wicket(DismissalKind.BOWLED, "a1"))
        assertEquals(DeliveryError.DISMISSAL_NOT_POSSIBLE_ON_EXTRA, reason(emptyList(), bowledOffNoBall))
        val caughtOffWide = ball(0).copy(extra = ExtraType.WIDE, extraRuns = 1, wicket = Wicket(DismissalKind.CAUGHT, "a1"))
        assertEquals(DeliveryError.DISMISSAL_NOT_POSSIBLE_ON_EXTRA, reason(emptyList(), caughtOffWide))
        val stumpedOffBye = ball(0).copy(extra = ExtraType.BYE, extraRuns = 1, wicket = Wicket(DismissalKind.STUMPED, "a1"))
        assertEquals(DeliveryError.DISMISSAL_NOT_POSSIBLE_ON_EXTRA, reason(emptyList(), stumpedOffBye))
    }

    @Test
    fun onlyRunOutsKeepCompletedRuns() {
        val caughtForTwo = ball(0, runs = 2).copy(wicket = Wicket(DismissalKind.CAUGHT, "a1", "b2"))
        assertEquals(DeliveryError.NO_RUNS_ON_DISMISSAL, reason(emptyList(), caughtForTwo))
        val runOutAfterOne = ball(0, runs = 1).copy(wicket = Wicket(DismissalKind.RUN_OUT, "a1"))
        assertNull(reason(emptyList(), runOutAfterOne))
    }

    @Test
    fun checksTheFielder() {
        val caughtByBatter = ball(0).copy(wicket = Wicket(DismissalKind.CAUGHT, "a1", fielderId = "a3"))
        assertEquals(DeliveryError.FIELDER_NOT_IN_TEAM, reason(emptyList(), caughtByBatter))
        val bowledWithFielder = ball(0).copy(wicket = Wicket(DismissalKind.BOWLED, "a1", fielderId = "b2"))
        assertEquals(DeliveryError.FIELDER_NOT_ALLOWED, reason(emptyList(), bowledWithFielder))
        val caughtAndBowled = ball(0).copy(wicket = Wicket(DismissalKind.CAUGHT, "a1", fielderId = "b1"))
        assertNull(reason(emptyList(), caughtAndBowled))
    }

    @Test
    fun rejectsBallsAfterTheMatchEnds() {
        val firstInnings = (0 until 12).map { ball(it, bowler = if (it < 6) "b1" else "b2") }
        val chase = ball(12, striker = "b1", nonStriker = "b2", bowler = "a1", runs = 1).copy(innings = 2)
        val done = ScoringEngine.compute(match, firstInnings + chase)
        assertEquals(DeliveryError.MATCH_OVER, done.validate(chase.copy(seq = 13), nextSeq = 13))
    }
}
