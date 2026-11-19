package com.harsh.desicrics.stats

import com.harsh.desicrics.api.MatchDetails
import com.harsh.desicrics.model.Delivery
import com.harsh.desicrics.model.DismissalKind
import com.harsh.desicrics.model.InningsPhase
import com.harsh.desicrics.model.MarginUnit
import com.harsh.desicrics.model.Match
import com.harsh.desicrics.model.MatchResult
import com.harsh.desicrics.model.MatchStatus
import com.harsh.desicrics.model.Player
import com.harsh.desicrics.model.Team
import com.harsh.desicrics.model.TeamSide
import com.harsh.desicrics.model.TossDecision
import com.harsh.desicrics.model.WinRecord
import com.harsh.desicrics.model.Wicket
import kotlin.test.Test
import kotlin.test.assertEquals

class TeamInsightsEngineTest {

    private val a1 = Player("a1", "Asha", profileId = "asha")
    private val a2 = Player("a2", "Dev")
    private val b1 = Player("b1", "Ravi")
    private val b2 = Player("b2", "Sam")

    /** "strikers" (A) win the toss, bat and make 4/1 in a 3-over match; "royals" (B) are bowled out for 1. */
    private fun win(id: String, strikersAre: TeamSide = TeamSide.A): MatchDetails {
        val strikers = Team("Strikers", listOf(a1, a2), teamId = "strikers")
        val royals = Team("Royals", listOf(b1, b2), teamId = "royals")
        val (teamA, teamB) = if (strikersAre == TeamSide.A) strikers to royals else royals to strikers
        val match = Match(
            id, "u", 0, teamA, teamB, 3, strikersAre, TossDecision.BAT,
            status = MatchStatus.COMPLETED, result = MatchResult(strikersAre, 3, MarginUnit.RUNS),
        )
        return MatchDetails(
            match,
            listOf(
                Delivery(0, 1, a1.id, a2.id, b1.id, runsOffBat = 4),
                Delivery(1, 1, a1.id, a2.id, b1.id, wicket = Wicket(DismissalKind.BOWLED, a1.id)),
                Delivery(2, 2, b1.id, b2.id, a1.id, runsOffBat = 1),
                Delivery(3, 2, b2.id, b1.id, a1.id, wicket = Wicket(DismissalKind.BOWLED, b2.id)),
            ),
        )
    }

    @Test
    fun inningsSplitsIntoThirds() {
        assertEquals(listOf(InningsPhase.START, InningsPhase.MIDDLE, InningsPhase.FINISH), (1..3).map { TeamInsightsEngine.phaseOf(it, 3) })
        // 10 overs: 1–4, 5–6, 7–10.
        assertEquals(
            listOf(4, 2, 4),
            InningsPhase.entries.map { phase -> (1..10).count { TeamInsightsEngine.phaseOf(it, 10) == phase } },
        )
    }

    @Test
    fun addsUpATeamsMatches() {
        val insights = TeamInsightsEngine.compute("strikers", listOf(win("m1"), win("m2", strikersAre = TeamSide.B)))
        assertEquals(2, insights.played)
        assertEquals(2, insights.won)
        assertEquals(WinRecord(2, 2), insights.battingFirst)
        assertEquals(WinRecord(0, 0), insights.chasing)
        assertEquals(WinRecord(2, 2), insights.tossWon)
        assertEquals(4, insights.averageFirstInnings)

        val start = insights.batting.first { it.phase == InningsPhase.START }
        assertEquals(8, start.runs)
        assertEquals(4, start.balls)
        assertEquals(2, start.wickets)
        assertEquals(2, insights.bowling.first { it.phase == InningsPhase.START }.runs)
        assertEquals(mapOf(DismissalKind.BOWLED to 2), insights.howOut)

        assertEquals("Asha", insights.topRunScorers.single().name)
        assertEquals(8, insights.topRunScorers.single().value)
        assertEquals(2, insights.topRunScorers.single().matches)
        assertEquals(2, insights.topWicketTakers.single().value)
    }

    @Test
    fun theOpponentsViewIsTheMirror() {
        val insights = TeamInsightsEngine.compute("royals", listOf(win("m1")))
        assertEquals(1, insights.lost)
        assertEquals(WinRecord(1, 0), insights.chasing)
        assertEquals(null, insights.averageFirstInnings)
        assertEquals(listOf("Ravi"), insights.topWicketTakers.map { it.name })
    }
}
