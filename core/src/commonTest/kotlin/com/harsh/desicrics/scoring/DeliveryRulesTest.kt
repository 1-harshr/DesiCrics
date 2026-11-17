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
        ScoringEngine.compute(match, history).rejectionReason(next, nextSeq = history.size)

    @Test
    fun acceptsAValidBall() {
        assertNull(reason(emptyList(), ball(0, runs = 4)))
    }

    @Test
    fun rejectsStaleSequenceNumbers() {
        assertEquals("The scorecard changed. Refresh and try again", reason(listOf(ball(0)), ball(0)))
    }

    @Test
    fun rejectsPlayersFromTheWrongTeam() {
        assertEquals("Both batters must be from Strikers", reason(emptyList(), ball(0, striker = "b2")))
        assertEquals("The bowler must be from Royals", reason(emptyList(), ball(0, bowler = "a3")))
    }

    @Test
    fun rejectsTheWrongStrikerAfterRotation() {
        // After a single the batters have crossed, so a1 can't still be on strike.
        assertEquals("Wrong striker for this ball", reason(listOf(ball(0, runs = 1)), ball(1, striker = "a1", nonStriker = "a2")))
    }

    @Test
    fun rejectsTheSameBowlerForConsecutiveOvers() {
        val over = (0 until 6).map { ball(it) }
        val next = ball(6, striker = "a2", nonStriker = "a1", bowler = "b1")
        assertEquals("A bowler can't bowl two overs in a row", reason(over, next))
    }

    @Test
    fun rejectsDismissedBattersAndImpossibleWickets() {
        val bowled = ball(0).copy(wicket = Wicket(DismissalKind.BOWLED, "a1"))
        assertEquals("That batter is already out", reason(listOf(bowled), ball(1, striker = "a1", nonStriker = "a2")))
        val stumpedNonStriker = ball(0).copy(wicket = Wicket(DismissalKind.STUMPED, "a2"))
        assertEquals("Only the striker can be out stumped", reason(emptyList(), stumpedNonStriker))
    }

    @Test
    fun rejectsMalformedExtras() {
        val wideWithBatRuns = ball(0, runs = 2).copy(extra = ExtraType.WIDE, extraRuns = 1)
        assertEquals("A wide is 1–5 runs with nothing off the bat", reason(emptyList(), wideWithBatRuns))
        assertEquals("A legal ball can't have extra runs", reason(emptyList(), ball(0).copy(extraRuns = 3)))
    }
}
