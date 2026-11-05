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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScoringEngineTest {

    private val match = Match(
        id = "m1",
        createdBy = "u1",
        createdAt = 0,
        teamA = Team("Strikers", listOf(Player("a1", "Asha"), Player("a2", "Bilal"), Player("a3", "Chetan"))),
        teamB = Team("Royals", listOf(Player("b1", "Dev"), Player("b2", "Esha"), Player("b3", "Farhan"))),
        oversPerInnings = 2,
        tossWinner = TeamSide.A,
        tossDecision = TossDecision.BAT,
    )

    private val deliveries = mutableListOf<Delivery>()

    private fun ball(
        striker: String,
        nonStriker: String,
        bowler: String,
        runs: Int = 0,
        extra: ExtraType? = null,
        extraRuns: Int = 0,
        wicket: Wicket? = null,
        innings: Int = 1,
    ) {
        deliveries += Delivery(deliveries.size, innings, striker, nonStriker, bowler, runs, extra, extraRuns, wicket)
    }

    private fun state() = ScoringEngine.compute(match, deliveries)

    @Test
    fun oddRunsSwapStrike() {
        ball("a1", "a2", "b1", runs = 1)
        val innings = state().current
        assertEquals(1, innings.runs)
        assertEquals("a2", innings.strikerId)
        assertEquals("a1", innings.nonStrikerId)
        assertEquals("b1", innings.bowlerId)
    }

    @Test
    fun overEndSwapsEndsAndNeedsNewBowler() {
        repeat(6) { ball("a1", "a2", "b1") }
        val innings = state().current
        assertEquals("1.0", innings.oversText)
        assertEquals("a2", innings.strikerId)
        assertNull(innings.bowlerId)
        assertEquals("b1", innings.previousOverBowlerId)
        assertEquals(1, innings.bowlers.single().maidens)
    }

    @Test
    fun widesAndNoBallsDontCountAsLegalBalls() {
        ball("a1", "a2", "b1", extra = ExtraType.WIDE, extraRuns = 1)
        ball("a1", "a2", "b1", runs = 4, extra = ExtraType.NO_BALL, extraRuns = 1)
        ball("a1", "a2", "b1", extra = ExtraType.LEG_BYE, extraRuns = 1)
        val innings = state().current
        assertEquals(7, innings.runs)
        assertEquals(1, innings.legalBalls)
        assertEquals(3, innings.extras.total)
        assertEquals(6, innings.bowlers.single().runs)
        val batter = innings.batters.first()
        assertEquals(4, batter.runs)
        assertEquals(2, batter.balls)
        assertEquals("a2", innings.strikerId)
    }

    @Test
    fun wicketVacatesTheDismissedBattersEnd() {
        ball("a1", "a2", "b1", wicket = Wicket(DismissalKind.BOWLED, "a1"))
        val innings = state().current
        assertEquals(1, innings.wickets)
        assertNull(innings.strikerId)
        assertEquals("a2", innings.nonStrikerId)
        assertEquals("b Dev", innings.batters.first().dismissal)
        assertEquals(1, innings.bowlers.single().wickets)
        assertEquals(listOf("a3"), innings.yetToBat.map { it.id })
    }

    @Test
    fun allOutEndsInningsAndChaseDecidesResult() {
        ball("a1", "a2", "b1", runs = 4)
        ball("a1", "a2", "b1", wicket = Wicket(DismissalKind.CAUGHT, "a1"))
        ball("a3", "a2", "b1", wicket = Wicket(DismissalKind.RUN_OUT, "a2"))
        val afterFirst = state()
        assertTrue(afterFirst.innings.first().isComplete)
        assertEquals(5, afterFirst.current.target)
        assertEquals(TeamSide.B, afterFirst.current.battingSide)
        assertFalse(afterFirst.isComplete)

        ball("b1", "b2", "a1", runs = 6, innings = 2)
        val done = state()
        assertTrue(done.isComplete)
        assertEquals("Royals won by 2 wickets", done.result)
        assertEquals("Strikers 4/2 (0.3) · Royals 6/0 (0.1)", done.scoreSummary)
    }

    @Test
    fun defendedTotalWinsByRuns() {
        repeat(12) { i -> ball("a1", "a2", if (i < 6) "b1" else "b2", runs = 2) }
        repeat(12) { i -> ball("b1", "b2", if (i < 6) "a1" else "a2", innings = 2) }
        val done = state()
        assertTrue(done.isComplete)
        assertEquals("Strikers won by 24 runs", done.result)
    }
}
