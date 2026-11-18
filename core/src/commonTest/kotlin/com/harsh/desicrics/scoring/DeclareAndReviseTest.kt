package com.harsh.desicrics.scoring

import com.harsh.desicrics.model.Delivery
import com.harsh.desicrics.model.MarginUnit
import com.harsh.desicrics.model.Match
import com.harsh.desicrics.model.MatchEvent
import com.harsh.desicrics.model.MatchResult
import com.harsh.desicrics.model.Player
import com.harsh.desicrics.model.Team
import com.harsh.desicrics.model.TeamSide
import com.harsh.desicrics.model.TossDecision
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeclareAndReviseTest {

    private val match = Match(
        id = "m1",
        createdBy = "u1",
        createdAt = 0,
        teamA = Team("Strikers", (1..4).map { Player("a$it", "A$it") }),
        teamB = Team("Royals", (1..4).map { Player("b$it", "B$it") }),
        oversPerInnings = 5,
        tossWinner = TeamSide.A,
        tossDecision = TossDecision.BAT,
    )

    private val deliveries = mutableListOf<Delivery>()
    private val events = mutableListOf<MatchEvent>()
    private val nextSeq get() = deliveries.size + events.size

    private fun state() = ScoringEngine.compute(match, deliveries, events)

    /** [count] balls of [runs] each in the current innings, with batters and bowlers as the engine expects. */
    private fun balls(count: Int, runs: Int = 0) = repeat(count) {
        val current = state().current
        val batting = if (current.number == 1) "a" else "b"
        val bowler = "${if (current.number == 1) "b" else "a"}${current.legalBalls / 6 % 2 + 1}"
        val d = Delivery(
            nextSeq, current.number,
            strikerId = current.strikerId ?: "${batting}1",
            nonStrikerId = current.nonStrikerId ?: "${batting}2",
            bowlerId = bowler,
            runsOffBat = runs,
        )
        assertNull(state().validate(d, d.seq), "ball $d")
        deliveries += d
    }

    private fun event(make: (seq: Int, innings: Int) -> MatchEvent): DeliveryError? {
        val e = make(nextSeq, state().current.number)
        val error = state().validate(e, e.seq)
        if (error == null) events += e
        return error
    }

    @Test
    fun declaringEndsTheFirstInningsAndSetsTheTarget() {
        balls(6, runs = 2)
        assertNull(event { seq, innings -> MatchEvent.Declare(seq, innings) })
        val state = state()
        assertTrue(state.innings.first().declared)
        assertEquals(2, state.innings.size)
        assertEquals(13, state.current.target)
    }

    @Test
    fun declaringInTheChaseConcedesIt() {
        balls(6, runs = 2)
        balls(24)
        balls(6, runs = 1)
        assertNull(event { seq, innings -> MatchEvent.Declare(seq, innings) })
        assertEquals(MatchResult(TeamSide.A, 6, MarginUnit.RUNS), state().result)
    }

    @Test
    fun firstInningsRevisionShortensBothInnings() {
        balls(12, runs = 1)
        assertNull(event { seq, innings -> MatchEvent.ReviseTarget(seq, innings, overs = 3) })
        assertEquals(18, state().current.maxBalls)
        assertFalse(state().current.isComplete)
        balls(6, runs = 1)
        val chase = state().current
        assertEquals(2, chase.number)
        assertEquals(18, chase.maxBalls)
        assertEquals(19, chase.target)
    }

    @Test
    fun revisingToTheOversAlreadyBowledEndsTheInnings() {
        balls(12)
        assertNull(event { seq, innings -> MatchEvent.ReviseTarget(seq, innings, overs = 2) })
        assertEquals(2, state().innings.size)
    }

    @Test
    fun chaseRevisionSetsOversAndTargetAndTheResultUsesPar() {
        balls(30, runs = 1)
        // Rain: Royals now need 20 from 3 overs, so par is 19.
        assertNull(event { seq, innings -> MatchEvent.ReviseTarget(seq, innings, overs = 3, target = 20) })
        val chase = state().current
        assertEquals(20, chase.target)
        assertEquals(18, chase.maxBalls)
        assertTrue(chase.isRevised)

        balls(18, runs = 1)
        // 18 off 18 balls: one short of par.
        assertEquals(MatchResult(TeamSide.A, 1, MarginUnit.RUNS), state().result)
    }

    @Test
    fun chaseReachingTheRevisedTargetWins() {
        balls(30, runs = 1)
        assertNull(event { seq, innings -> MatchEvent.ReviseTarget(seq, innings, overs = 3, target = 12) })
        balls(6, runs = 2)
        assertEquals(MatchResult(TeamSide.B, 3, MarginUnit.WICKETS), state().result)
    }

    @Test
    fun tieOnPar() {
        balls(30, runs = 1)
        assertNull(event { seq, innings -> MatchEvent.ReviseTarget(seq, innings, overs = 2, target = 13) })
        balls(12, runs = 1)
        assertEquals(MatchResult.Tie, state().result)
    }

    @Test
    fun invalidRevisionsAreRefused() {
        balls(12)
        assertEquals(DeliveryError.REVISED_OVERS_INVALID, event { seq, innings -> MatchEvent.ReviseTarget(seq, innings, overs = 1) })
        assertEquals(DeliveryError.REVISED_OVERS_INVALID, event { seq, innings -> MatchEvent.ReviseTarget(seq, innings, overs = 6) })
        // No target in the first innings.
        assertEquals(DeliveryError.REVISED_TARGET_INVALID, event { seq, innings -> MatchEvent.ReviseTarget(seq, innings, overs = 4, target = 30) })
        balls(18)
        // The chase needs one.
        assertEquals(DeliveryError.REVISED_TARGET_INVALID, event { seq, innings -> MatchEvent.ReviseTarget(seq, innings, overs = 4) })
    }
}
