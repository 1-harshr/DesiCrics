package com.harsh.desicrics.scoring

import com.harsh.desicrics.model.Delivery
import com.harsh.desicrics.model.DismissalKind
import com.harsh.desicrics.model.ExtraType
import com.harsh.desicrics.model.MarginUnit
import com.harsh.desicrics.model.Match
import com.harsh.desicrics.model.MatchEvent
import com.harsh.desicrics.model.MatchResult
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
    private val events = mutableListOf<MatchEvent>()
    private val nextSeq get() = deliveries.size + events.size

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
        deliveries += Delivery(nextSeq, innings, striker, nonStriker, bowler, runs, extra, extraRuns, wicket)
    }

    private fun event(make: (seq: Int) -> MatchEvent) {
        events += make(nextSeq)
    }

    private fun state() = ScoringEngine.compute(match, deliveries, events)

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
        assertEquals(Dismissal(DismissalKind.BOWLED, bowler = Player("b1", "Dev"), fielder = null), innings.batters.first().dismissal)
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
        assertEquals(MatchResult(TeamSide.B, 2, MarginUnit.WICKETS), done.result)
        assertEquals("Strikers 4/2 (0.3) · Royals 6/0 (0.1)", done.scoreSummary)
    }

    @Test
    fun defendedTotalWinsByRuns() {
        repeat(12) { i -> ball("a1", "a2", if (i < 6) "b1" else "b2", runs = 2) }
        repeat(12) { i -> ball("b1", "b2", if (i < 6) "a1" else "a2", innings = 2) }
        val done = state()
        assertTrue(done.isComplete)
        assertEquals(MatchResult(TeamSide.A, 24, MarginUnit.RUNS), done.result)
    }

    @Test
    fun levelScoresTie() {
        repeat(6) { ball("a1", "a2", "b1", runs = 1) }
        repeat(6) { ball("a2", "a1", "b2") }
        repeat(6) { ball("b1", "b2", "a1", runs = 1, innings = 2) }
        repeat(6) { ball("b2", "b1", "a2", innings = 2) }
        assertEquals(MatchResult.Tie, state().result)
    }

    @Test
    fun runOutKeepsCompletedRunsAndNamesTheFielder() {
        // One run completed, then the non-striker is run out at the striker's end.
        ball("a1", "a2", "b1", runs = 1, wicket = Wicket(DismissalKind.RUN_OUT, "a2", fielderId = "b3"))
        val innings = state().current
        assertEquals(1, innings.runs)
        assertEquals(1, innings.batters.first { it.player.id == "a1" }.runs)
        assertNull(innings.strikerId)
        assertEquals("a1", innings.nonStrikerId)
        assertEquals(Dismissal(DismissalKind.RUN_OUT, bowler = null, fielder = Player("b3", "Farhan")), innings.batters.first { it.player.id == "a2" }.dismissal)
        assertEquals(0, innings.bowlers.single().wickets)
    }

    @Test
    fun stumpedOffAWideCountsTheWideButNotABall() {
        ball("a1", "a2", "b1", extra = ExtraType.WIDE, extraRuns = 1, wicket = Wicket(DismissalKind.STUMPED, "a1", "b2"))
        val innings = state().current
        assertEquals(1, innings.runs)
        assertEquals(0, innings.legalBalls)
        assertEquals(1, innings.wickets)
        assertEquals(0, innings.batters.first().balls)
        assertEquals(1, innings.bowlers.single().wickets)
    }

    @Test
    fun wicketOnTheLastBallOfAnOverLeavesTheNewBatterAtTheNonStrikersEnd() {
        repeat(5) { ball("a1", "a2", "b1") }
        ball("a1", "a2", "b1", wicket = Wicket(DismissalKind.BOWLED, "a1"))
        val innings = state().current
        assertEquals("a2", innings.strikerId)
        assertNull(innings.nonStrikerId)
        assertNull(innings.bowlerId)
    }

    @Test
    fun oversListEachBallWithItsNumber() {
        ball("a1", "a2", "b1", runs = 4)
        ball("a1", "a2", "b1", extra = ExtraType.WIDE, extraRuns = 1)
        ball("a1", "a2", "b1", runs = 1)
        repeat(4) { ball("a2", "a1", "b1") }
        ball("a1", "a2", "b2", wicket = Wicket(DismissalKind.CAUGHT, "a1", fielderId = "b3"))
        val overs = state().current.overs

        assertEquals(2, overs.size)
        val first = overs.first()
        assertEquals(1, first.number)
        assertEquals("Dev", first.bowler.name)
        // The wide shares "0.2" with the ball bowled after it.
        assertEquals(listOf("0.1", "0.2", "0.2", "0.3", "0.4", "0.5", "0.6"), first.balls.map { it.ballNumber })
        assertEquals(6, first.runs)
        assertEquals("6/0", first.scoreAfter)

        val second = overs.last()
        assertEquals("Esha", second.bowler.name)
        assertEquals("1.1", second.balls.single().ballNumber)
        assertEquals(1, second.wickets)
        assertEquals("6/1", second.scoreAfter)
        val wicket = second.balls.single()
        assertEquals("Asha", wicket.playerOut?.name)
        assertEquals("Farhan", wicket.fielder?.name)
        assertEquals("Asha", wicket.striker.name)
    }

    @Test
    fun retiredHurtLeavesTheCreaseAndCanComeBack() {
        ball("a1", "a2", "b1", runs = 2)
        event { MatchEvent.Retire(it, 1, "a1", out = false) }
        var innings = state().current
        assertNull(innings.strikerId)
        assertEquals(0, innings.wickets)
        assertEquals(Retirement.HURT, innings.batters.first { it.player.id == "a1" }.retired)
        assertEquals(listOf("a1", "a3"), innings.canComeIn.map { it.id })

        // a3 comes in, then a2 is out and a1 returns with the runs already scored.
        ball("a3", "a2", "b1", wicket = Wicket(DismissalKind.BOWLED, "a3"))
        ball("a1", "a2", "b1", runs = 1)
        innings = state().current
        val returned = innings.batters.first { it.player.id == "a1" }
        assertNull(returned.retired)
        assertEquals(3, returned.runs)
        assertFalse(innings.isComplete)
    }

    @Test
    fun retiredOutCountsAsAWicket() {
        ball("a1", "a2", "b1")
        event { MatchEvent.Retire(it, 1, "a2", out = true) }
        val innings = state().current
        assertEquals(1, innings.wickets)
        assertTrue(innings.batters.first { it.player.id == "a2" }.isOut)
        assertEquals(0, innings.bowlers.single().wickets)
        assertNull(innings.nonStrikerId)
    }

    @Test
    fun swapEndsAndReplaceBowlerMidOver() {
        ball("a1", "a2", "b1")
        event { MatchEvent.SwapEnds(it, 1) }
        event { MatchEvent.ReplaceBowler(it, 1) }
        var innings = state().current
        assertEquals("a2", innings.strikerId)
        assertNull(innings.bowlerId)

        // The new bowler finishes the over, which still ends after six legal balls.
        repeat(5) { ball("a2", "a1", "b2") }
        innings = state().current
        assertEquals("1.0", innings.oversText)
        assertEquals("b2", innings.previousOverBowlerId)
        assertEquals(listOf(1, 5), innings.bowlers.map { it.legalBalls })
    }

    @Test
    fun undoingTheBallThatEndsAnOverKeepsTheBowler() {
        repeat(6) { ball("a1", "a2", "b1") }
        repeat(6) { ball("a2", "a1", "b2") }
        deliveries.removeLast()
        val innings = state().current
        assertEquals("1.5", innings.oversText)
        assertEquals("b2", innings.bowlerId)
    }

    @Test
    fun undoingTheBallThatEndsAnOverKeepsTheReplacementBowler() {
        repeat(6) { ball("a1", "a2", "b1") }
        repeat(5) { ball("a2", "a1", "b2") }
        // Changed after the fifth ball, and the same bowler picked again to finish the over.
        event { MatchEvent.ReplaceBowler(it, 1, bowlerId = "b2") }
        assertEquals("b2", state().current.bowlerId)
        ball("a2", "a1", "b2")
        assertNull(state().current.bowlerId)

        deliveries.removeLast()
        val innings = state().current
        assertEquals("1.5", innings.oversText)
        assertEquals("b2", innings.bowlerId)
    }

    @Test
    fun undoingTheBallAfterABowlerChangeKeepsTheNewBowler() {
        repeat(2) { ball("a1", "a2", "b1") }
        event { MatchEvent.ReplaceBowler(it, 1, bowlerId = "b3") }
        ball("a1", "a2", "b3")
        deliveries.removeLast()
        assertEquals("b3", state().current.bowlerId)
    }

    @Test
    fun eventsAreChecked() {
        ball("a1", "a2", "b1")
        val state = state()
        assertEquals(DeliveryError.BATTER_NOT_AT_CREASE, state.validate(MatchEvent.Retire(1, 1, "a3", out = false), nextSeq = 1))
        assertEquals(DeliveryError.STALE, state.validate(MatchEvent.SwapEnds(5, 1), nextSeq = 1))
        assertNull(state.validate(MatchEvent.ReplaceBowler(1, 1), nextSeq = 1))
        assertNull(state.validate(MatchEvent.ReplaceBowler(1, 1, bowlerId = "b2"), nextSeq = 1))
        assertEquals(DeliveryError.BOWLER_NOT_IN_TEAM, state.validate(MatchEvent.ReplaceBowler(1, 1, bowlerId = "a3"), nextSeq = 1))
    }

    @Test
    fun penaltiesGoToTheRightInnings() {
        ball("a1", "a2", "b1", runs = 1)
        // To the batting side: on this innings, as extras.
        event { MatchEvent.Penalty(it, 1, toBattingSide = true, runs = 5) }
        // To the fielding side (Royals): on their own innings, which they haven't batted yet.
        event { MatchEvent.Penalty(it, 1, toBattingSide = false, runs = 5) }
        val first = state().current
        assertEquals(6, first.runs)
        assertEquals(5, first.extras.penalties)

        repeat(11) { i -> ball(if (i % 2 == 0) "a2" else "a1", if (i % 2 == 0) "a1" else "a2", if (i < 5) "b1" else "b2") }
        val second = state().current
        assertEquals(2, second.number)
        assertEquals(7, second.target)
        // Royals start their chase with the 5 runs they were awarded.
        assertEquals(5, second.runs)
        assertEquals(2, second.runsNeeded)
    }

    @Test
    fun penaltyToTheSideThatBattedFirstRaisesTheTarget() {
        repeat(12) { i -> ball("a1", "a2", if (i < 6) "b1" else "b2") }
        event { MatchEvent.Penalty(it, 2, toBattingSide = false, runs = 5) }
        val state = state()
        assertEquals(5, state.innings.first().runs)
        assertEquals(6, state.current.target)
    }
}
