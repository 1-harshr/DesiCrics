package com.harsh.desicrics.web

import com.harsh.desicrics.api.MatchDetails
import com.harsh.desicrics.model.Delivery
import com.harsh.desicrics.model.Match
import com.harsh.desicrics.model.MatchStatus
import com.harsh.desicrics.model.Player
import com.harsh.desicrics.model.Team
import com.harsh.desicrics.model.TeamSide
import com.harsh.desicrics.model.TossDecision
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class WebCoreTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun scorecardRoundTripsThroughJson() {
        val match = Match(
            id = "m1",
            createdBy = "u1",
            createdAt = 0,
            teamA = Team("Strikers", (1..3).map { Player("a$it", "A$it") }),
            teamB = Team("Royals", (1..3).map { Player("b$it", "B$it") }),
            oversPerInnings = 2,
            tossWinner = TeamSide.A,
            tossDecision = TossDecision.BAT,
            status = MatchStatus.IN_PROGRESS,
        )
        val balls = listOf(4, 1, 0, 6).mapIndexed { seq, runs ->
            val striker = if (seq in 2..3) "a2" else "a1"
            Delivery(seq, 1, strikerId = striker, nonStrikerId = if (striker == "a1") "a2" else "a1", bowlerId = "b1", runsOffBat = runs)
        }
        val input = json.encodeToString(MatchDetails(match, balls))

        val view = json.decodeFromString<ScorecardView>(WebCore.scorecard(input))

        val innings = view.innings.single()
        assertEquals("11/0", innings.scoreText)
        assertEquals("0.4", innings.oversText)
        assertEquals(listOf(5, 6), innings.batters.map { it.runs })
        assertEquals(4, innings.currentOver.size)
    }
}
