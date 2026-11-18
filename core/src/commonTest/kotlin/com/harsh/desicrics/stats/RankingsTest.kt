package com.harsh.desicrics.stats

import com.harsh.desicrics.model.MarginUnit
import com.harsh.desicrics.model.Match
import com.harsh.desicrics.model.MatchResult
import com.harsh.desicrics.model.MatchStatus
import com.harsh.desicrics.model.PlayerMatchLine
import com.harsh.desicrics.model.Team
import com.harsh.desicrics.model.TeamSide
import com.harsh.desicrics.model.TossDecision
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RankingsTest {

    private var clock = 0L

    private fun result(a: String, b: String, winner: TeamSide?) = Match(
        "m${clock}", "u", clock++, Team(a, emptyList(), teamId = a), Team(b, emptyList(), teamId = b),
        5, TeamSide.A, TossDecision.BAT, status = MatchStatus.COMPLETED,
        result = winner?.let { MatchResult(it, 1, MarginUnit.RUNS) } ?: MatchResult.Tie,
    )

    @Test
    fun evenTeamsMoveSixteenPointsAWin() {
        val ratings = Rankings.teams(listOf(result("x", "y", TeamSide.A)))
        assertEquals(TeamRating(1016, 1), ratings["x"])
        assertEquals(TeamRating(984, 1), ratings["y"])
    }

    @Test
    fun beatingAStrongSideCountsForMore() {
        // "strong" beats three others first, then "underdog" and "other" each play it once.
        val history = listOf(
            result("strong", "a", TeamSide.A), result("strong", "b", TeamSide.A), result("strong", "c", TeamSide.A),
            result("underdog", "strong", TeamSide.A),
            result("other", "a", TeamSide.A),
        )
        val ratings = Rankings.teams(history)
        // Both started at 1000 and won once, but the win over the strong side earns more.
        assertTrue(ratings.getValue("underdog").rating > ratings.getValue("other").rating)
    }

    @Test
    fun tiesAndUnsavedTeamsAreHandled() {
        val ratings = Rankings.teams(listOf(result("x", "y", null), result("x", "y", TeamSide.B).copy(teamB = Team("Guests", emptyList()))))
        assertEquals(1000, ratings.getValue("x").rating)
        assertEquals(1, ratings.getValue("x").played)
    }

    @Test
    fun playerScoresWeighStrongerOpponentsMore() {
        val ratings = mapOf("strong" to TeamRating(1200, 5), "weak" to TeamRating(800, 5))
        fun line(opponent: String?) = PlayerMatchLine("p", "m", 0, "Me", "Them", opponentTeamId = opponent, oversPerInnings = 5, mvpTenths = 500)
        assertEquals(60, Rankings.playerScore(listOf(line("strong")), ratings))
        assertEquals(40, Rankings.playerScore(listOf(line("weak")), ratings))
        assertEquals(50, Rankings.playerScore(listOf(line(null)), ratings))
    }
}
