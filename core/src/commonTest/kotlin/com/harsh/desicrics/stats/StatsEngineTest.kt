package com.harsh.desicrics.stats

import com.harsh.desicrics.api.MatchDetails
import com.harsh.desicrics.model.Badge
import com.harsh.desicrics.model.Delivery
import com.harsh.desicrics.model.DismissalKind
import com.harsh.desicrics.model.ExtraType
import com.harsh.desicrics.model.Match
import com.harsh.desicrics.model.Player
import com.harsh.desicrics.model.Team
import com.harsh.desicrics.model.TeamSide
import com.harsh.desicrics.model.TossDecision
import com.harsh.desicrics.model.Wicket
import com.harsh.desicrics.scoring.ScoringEngine
import com.harsh.desicrics.scoring.validate
import com.harsh.desicrics.scoring.withScore
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StatsEngineTest {

    /** Six-a-side, 5 overs, with "me" opening for A and bowling for B's innings... and a guest at 6. */
    private fun match(id: String, at: Long) = Match(
        id = id, createdBy = "u", createdAt = at,
        teamA = Team("Strikers", listOf(Player("$id-a1", "Me", "me")) + (2..5).map { Player("$id-a$it", "A$it", "pa$it") } + Player("$id-a6", "Guest")),
        teamB = Team("Royals", (1..6).map { Player("$id-b$it", "B$it", "pb$it") }),
        oversPerInnings = 5, tossWinner = TeamSide.A, tossDecision = TossDecision.BAT,
    )

    /** Plays a seeded random match to the end through validation, as the app would. */
    private fun play(id: String, seed: Int): MatchDetails {
        val random = Random(seed)
        var match = match(id, at = seed * 1000L)
        val deliveries = mutableListOf<Delivery>()
        fun state() = ScoringEngine.compute(match, deliveries)
        while (!state().isComplete) {
            val s = state()
            val innings = s.current
            val available = innings.canComeIn.map { it.id }
            val striker = innings.strikerId ?: available.first()
            val nonStriker = innings.nonStrikerId ?: available.first { it != striker }
            val bowlers = innings.bowlingTeam.players.map { it.id }
            val bowler = innings.bowlerId ?: bowlers.filter { it != innings.previousOverBowlerId }[(innings.legalBalls / 6) % (bowlers.size - 1)]
            val fielder = innings.bowlingTeam.players.random(random).id
            val roll = random.nextInt(100)
            val d = when {
                roll < 6 -> Delivery(deliveries.size, innings.number, striker, nonStriker, bowler, extra = ExtraType.WIDE, extraRuns = 1)
                roll < 16 -> Delivery(deliveries.size, innings.number, striker, nonStriker, bowler, wicket = Wicket(DismissalKind.CAUGHT, striker, fielder))
                roll < 19 -> Delivery(deliveries.size, innings.number, striker, nonStriker, bowler, wicket = Wicket(DismissalKind.RUN_OUT, nonStriker, fielder))
                else -> Delivery(deliveries.size, innings.number, striker, nonStriker, bowler, runsOffBat = listOf(0, 1, 1, 2, 4, 6).random(random))
            }
            assertEquals(null, s.validate(d, d.seq))
            deliveries += d
        }
        match = match.withScore(state())
        return MatchDetails(match, deliveries)
    }

    @Test
    fun careerStatsOverTenMatchesMatchARecount() {
        val matches = (1..10).map { play("m$it", seed = it) }
        val career = StatsEngine.career("me", matches.flatMap { StatsEngine.lines(it) }.filter { it.profileId == "me" })

        // The recount: straight from each scorecard, the way someone would with a pen.
        var runs = 0; var balls = 0; var fours = 0; var sixes = 0; var notOuts = 0; var innings = 0
        var wickets = 0; var conceded = 0; var bowled = 0; var catches = 0; var best = 0
        for (details in matches) {
            val state = ScoringEngine.compute(details.match, details.deliveries)
            val myId = details.match.teamA.players.first().id
            state.innings.flatMap { it.batters }.filter { it.player.id == myId }.forEach {
                innings++; runs += it.runs; balls += it.balls; fours += it.fours; sixes += it.sixes
                if (!it.isOut) notOuts++
                best = maxOf(best, it.runs)
            }
            state.innings.flatMap { it.bowlers }.filter { it.player.id == myId }.forEach {
                wickets += it.wickets; conceded += it.runs; bowled += it.legalBalls
            }
            catches += state.innings.flatMap { it.batters }.count { it.dismissal?.kind == DismissalKind.CAUGHT && it.dismissal.fielder?.id == myId }
        }
        assertEquals(10, career.matches)
        assertEquals(innings, career.batting.innings)
        assertEquals(runs, career.batting.runs)
        assertEquals(balls, career.batting.balls)
        assertEquals(fours, career.batting.fours)
        assertEquals(sixes, career.batting.sixes)
        assertEquals(notOuts, career.batting.notOuts)
        assertEquals(best, career.batting.highest)
        assertEquals(wickets, career.bowling.wickets)
        assertEquals(conceded, career.bowling.runs)
        assertEquals(bowled, career.bowling.balls)
        assertEquals(catches, career.fielding.catches)
        assertEquals(10, career.form.size)
        assertEquals(career.form.sortedBy { it.playedAt }, career.form)
    }

    @Test
    fun guestsGetNoLinesAndUnfinishedMatchesCountForNothing() {
        val details = play("g", seed = 3)
        val lines = StatsEngine.lines(details)
        assertTrue(lines.none { it.profileId.isEmpty() })
        assertEquals(11, lines.size)
        assertEquals(1, lines.count { it.playerOfTheMatch })
        assertTrue(StatsEngine.lines(details.copy(match = details.match.copy(status = com.harsh.desicrics.model.MatchStatus.IN_PROGRESS))).isEmpty())
    }

    @Test
    fun mvpFormula() {
        // Six fours then two sixes from a1.
        val lines = (1..6).map { Delivery(it - 1, 1, "a1", "a2", "b1", runsOffBat = 4) } +
            (7..8).map { Delivery(it - 1, 1, "a1", "a2", "b2", runsOffBat = 6) }
        val match = Match(
            "x", "u", 0,
            Team("A", listOf(Player("a1", "A1"), Player("a2", "A2"))),
            Team("B", listOf(Player("b1", "B1"), Player("b2", "B2"))),
            oversPerInnings = 5, tossWinner = TeamSide.A, tossDecision = TossDecision.BAT,
        )
        val points = MvpPoints.forMatch(ScoringEngine.compute(match, lines))
        // 36 runs in 8 balls: 36 + 6 + 4 = 46, under 10 balls so no strike-rate term.
        assertEquals(460, points["a1"])
        // b1 bowled 6 balls for 24: no wickets, under 2 overs, so nothing.
        assertEquals(0, points["b1"])
    }

    @Test
    fun badges() {
        val big = com.harsh.desicrics.model.PlayerMatchLine("me", "m", 0, "A", "B", 20, batted = true, runs = 104, balls = 60, wickets = 5, bowledBalls = 24)
        val career = StatsEngine.career("me", listOf(big))
        assertTrue(Badge.CENTURY in career.badges)
        assertTrue(Badge.FIRST_FIFTY in career.badges)
        assertTrue(Badge.FIVE_WICKET_HAUL in career.badges)
    }
}
