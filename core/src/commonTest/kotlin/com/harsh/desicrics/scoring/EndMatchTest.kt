package com.harsh.desicrics.scoring

import com.harsh.desicrics.model.AbandonReason
import com.harsh.desicrics.model.Delivery
import com.harsh.desicrics.model.Match
import com.harsh.desicrics.model.MatchEnding
import com.harsh.desicrics.model.MatchEvent
import com.harsh.desicrics.model.MatchStatus
import com.harsh.desicrics.model.Player
import com.harsh.desicrics.model.Team
import com.harsh.desicrics.model.TeamSide
import com.harsh.desicrics.model.TossDecision
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EndMatchTest {

    private val match = Match(
        id = "m1",
        createdBy = "u1",
        createdAt = 0,
        teamA = Team("Strikers", (1..4).map { Player("a$it", "A$it") }),
        teamB = Team("Royals", (1..4).map { Player("b$it", "B$it") }),
        oversPerInnings = 5,
        tossWinner = TeamSide.A,
        tossDecision = TossDecision.BAT,
        status = MatchStatus.IN_PROGRESS,
    )

    private val deliveries = mutableListOf<Delivery>()
    private val events = mutableListOf<MatchEvent>()
    private val nextSeq get() = deliveries.size + events.size

    private fun state() = ScoringEngine.compute(match, deliveries, events)

    private fun balls(count: Int, runs: Int = 1) = repeat(count) {
        val current = state().current
        val d = Delivery(
            nextSeq, current.number,
            strikerId = current.strikerId ?: "a1",
            nonStrikerId = current.nonStrikerId ?: "a2",
            bowlerId = "b${current.legalBalls / 6 % 2 + 1}",
            runsOffBat = runs,
        )
        assertNull(state().validate(d, d.seq), "ball $d")
        deliveries += d
    }

    private fun end(ending: MatchEnding, winner: TeamSide? = null, reason: AbandonReason? = null, beforeStart: Boolean = false): DeliveryError? {
        val e = MatchEvent.EndMatch(nextSeq, state().current.number, ending, winner, reason, beforeStart)
        val error = state().validate(e, e.seq)
        if (error == null) events += e
        return error
    }

    @Test
    fun rainAbandonsAMatchMidInningsWithNoResult() {
        balls(8)
        assertNull(end(MatchEnding.ABANDONED, reason = AbandonReason.RAIN))
        val state = state()
        assertTrue(state.isComplete)
        val result = state.result!!
        assertTrue(result.isNoResult)
        assertFalse(result.isTie)
        assertNull(result.winner)
        assertEquals(AbandonReason.RAIN, result.reason)
        // The runs scored before the rain stay on the card.
        assertEquals(8, state.innings.first().runs)
        val stored = match.withScore(state)
        assertEquals(MatchStatus.COMPLETED, stored.status)
        assertEquals(1, stored.innings.size)
    }

    @Test
    fun aConcessionGivesTheOtherSideTheWin() {
        balls(6)
        assertEquals(DeliveryError.END_INVALID, end(MatchEnding.CONCEDED))
        assertNull(end(MatchEnding.CONCEDED, winner = TeamSide.B))
        assertEquals(TeamSide.B, state().result!!.winner)
        assertEquals(MatchEnding.CONCEDED, state().result!!.ending)
    }

    @Test
    fun nothingCanBeRecordedAfterTheEnd() {
        balls(3)
        assertNull(end(MatchEnding.ABANDONED, reason = AbandonReason.BAD_LIGHT))
        assertEquals(DeliveryError.MATCH_OVER, end(MatchEnding.ABANDONED))
    }

    @Test
    fun aWalkoverOnlyHappensBeforeABallIsBowled() {
        assertEquals(DeliveryError.END_INVALID, end(MatchEnding.WALKOVER, winner = TeamSide.A))
        assertNull(end(MatchEnding.WALKOVER, winner = TeamSide.A, beforeStart = true))
        val stored = match.withScore(state())
        assertEquals(TeamSide.A, stored.result!!.winner)
        // No innings was played, so there's no score to show.
        assertTrue(stored.innings.isEmpty())
    }

    @Test
    fun aStartedMatchCanNotBeWalkedOverOrCalledOff() {
        balls(1)
        assertEquals(DeliveryError.END_INVALID, end(MatchEnding.WALKOVER, winner = TeamSide.B, beforeStart = true))
        assertEquals(DeliveryError.END_INVALID, end(MatchEnding.ABANDONED, beforeStart = true))
    }

    @Test
    fun anAbandonmentHasNoWinner() {
        assertEquals(DeliveryError.END_INVALID, end(MatchEnding.ABANDONED, winner = TeamSide.A, beforeStart = true))
        assertNull(end(MatchEnding.ABANDONED, reason = AbandonReason.GROUND, beforeStart = true))
    }

    @Test
    fun undoingTheEndCarriesOnFromWhereItStopped() {
        balls(4)
        assertNull(end(MatchEnding.ABANDONED, reason = AbandonReason.RAIN))
        events.removeLast()
        assertFalse(state().isComplete)
        balls(2)
        assertEquals(6, state().current.legalBalls)
    }
}
