package com.harsh.desicrics.tournament

import com.harsh.desicrics.model.MatchEnding
import com.harsh.desicrics.model.AbandonReason
import com.harsh.desicrics.model.FixtureInfo
import com.harsh.desicrics.model.InningsScore
import com.harsh.desicrics.model.MarginUnit
import com.harsh.desicrics.model.Match
import com.harsh.desicrics.model.MatchResult
import com.harsh.desicrics.model.MatchStatus
import com.harsh.desicrics.model.Stage
import com.harsh.desicrics.model.Team
import com.harsh.desicrics.model.TeamSide
import com.harsh.desicrics.model.TossDecision
import com.harsh.desicrics.model.Tournament
import com.harsh.desicrics.model.TournamentFormat
import com.harsh.desicrics.model.TournamentTeam
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TournamentEngineTest {

    private fun tournament(format: TournamentFormat, teams: Int, groups: Int = 1, advance: Int = 0): Tournament {
        val entries = (1..teams).map { TournamentTeam("t$it", "Team $it", seed = it) }
        val placed = if (groups > 1) TournamentEngine.assignGroups(entries, groups) else entries
        return Tournament("x", "Cup", format = format, oversPerInnings = 5, groups = groups, advance = advance, createdBy = "u", teams = placed)
    }

    /**
     * Plays a fixture: [winner] picks the winning team ID (null for a tie). The winner scores 50 to the
     * loser's 40 in full overs, so net run rate has something to work with.
     */
    private fun play(planned: PlannedFixture, winner: (a: String, b: String) -> String?): Match {
        val win = winner(planned.teamA, planned.teamB)
        val side = when (win) {
            planned.teamA -> TeamSide.A
            planned.teamB -> TeamSide.B
            else -> null
        }
        val (runsA, runsB) = when (side) {
            TeamSide.A -> 50 to 40
            TeamSide.B -> 40 to 50
            null -> 45 to 45
        }
        return Match(
            id = "${planned.round}-${planned.number}-${planned.group}",
            createdBy = "u", createdAt = 0,
            teamA = Team(planned.teamA, emptyList(), teamId = planned.teamA),
            teamB = Team(planned.teamB, emptyList(), teamId = planned.teamB),
            oversPerInnings = 5, tossWinner = TeamSide.A, tossDecision = TossDecision.BAT,
            status = MatchStatus.COMPLETED,
            result = if (side == null) MatchResult.Tie else MatchResult(side, 10, MarginUnit.RUNS),
            innings = listOf(InningsScore(TeamSide.A, runsA, 3, 30, maxBalls = 30), InningsScore(TeamSide.B, runsB, 3, 30, maxBalls = 30)),
            fixture = FixtureInfo("x", planned.stage, planned.round, planned.number, planned.group),
        )
    }

    /** Plays the whole tournament with [winner] deciding every match; returns every fixture played. */
    private fun run(tournament: Tournament, winner: (a: String, b: String) -> String?): List<Match> {
        val played = mutableListOf<Match>()
        var next = TournamentEngine.firstRound(tournament)
        var guard = 0
        while (next.isNotEmpty() && guard++ < 100) {
            played += next.map { play(it, winner) }
            next = TournamentEngine.nextRound(tournament, played)
        }
        return played
    }

    /** The lower seed number always wins. */
    private val favourites: (String, String) -> String? = { a, b -> if (a.drop(1).toInt() < b.drop(1).toInt()) a else b }

    @Test
    fun knockoutGivesTopSeedsByes() {
        val cup = tournament(TournamentFormat.KNOCKOUT, teams = 6)
        val first = TournamentEngine.firstRound(cup)
        // 6 teams in an 8 bracket: seeds 1 and 2 skip round 1; 3v6 and 4v5.
        assertEquals(listOf("t3" to "t6", "t4" to "t5"), first.map { it.teamA to it.teamB })
        assertEquals(Stage.QUARTERFINAL, first.first().stage)

        val played = run(cup, favourites)
        assertEquals(listOf(Stage.QUARTERFINAL, Stage.SEMIFINAL, Stage.FINAL), played.map { it.fixture!!.stage }.distinct())
        assertEquals("t1", TournamentEngine.champion(cup, played))
        assertTrue(TournamentEngine.nextRound(cup, played).isEmpty())
    }

    @Test
    fun aTiedKnockoutGoesToTheHigherSeed() {
        val cup = tournament(TournamentFormat.KNOCKOUT, teams = 2)
        val played = run(cup) { _, _ -> null }
        assertEquals("t1", TournamentEngine.champion(cup, played))
    }

    @Test
    fun nextRoundWaitsForEveryFixture() {
        val cup = tournament(TournamentFormat.KNOCKOUT, teams = 4)
        val first = TournamentEngine.firstRound(cup).map { play(it, favourites) }
        val unfinished = listOf(first[0], first[1].copy(status = MatchStatus.IN_PROGRESS))
        assertTrue(TournamentEngine.nextRound(cup, unfinished).isEmpty())
        assertEquals(1, TournamentEngine.nextRound(cup, first).size)
    }

    @Test
    fun roundRobinPlaysEveryPairingOnce() {
        val league = tournament(TournamentFormat.ROUND_ROBIN, teams = 5)
        val fixtures = TournamentEngine.firstRound(league)
        assertEquals(10, fixtures.size)
        assertEquals(10, fixtures.map { setOf(it.teamA, it.teamB) }.toSet().size)
        // Five teams: one sits out each of five matchdays, so nobody plays twice in a round.
        assertEquals(5, fixtures.map { it.round }.distinct().size)
        fixtures.groupBy { it.round }.values.forEach { round ->
            val teams = round.flatMap { listOf(it.teamA, it.teamB) }
            assertEquals(teams.size, teams.toSet().size)
        }

        val played = run(league, favourites)
        assertEquals("t1", TournamentEngine.champion(league, played))
        val table = TournamentEngine.pointsTable(league, played, 0)
        assertEquals(listOf("t1", "t2", "t3", "t4", "t5"), table.map { it.teamId })
        assertEquals(8, table.first().points)
        assertEquals(4, table.first().played)
    }

    @Test
    fun netRunRateBreaksATieOnPoints() {
        val league = tournament(TournamentFormat.ROUND_ROBIN, teams = 3)
        // A beats B, B beats C, C beats A: everyone on 2 points.
        val results = mapOf(setOf("t1", "t2") to "t1", setOf("t2", "t3") to "t2", setOf("t1", "t3") to "t3")
        val played = TournamentEngine.firstRound(league).map { planned ->
            val match = play(planned) { a, b -> results.getValue(setOf(a, b)) }
            // t3 thrashes t1: 100 to 20, with t1 bowled out in 2 overs.
            if (setOf(planned.teamA, planned.teamB) == setOf("t1", "t3")) {
                val t1Side = if (planned.teamA == "t1") TeamSide.A else TeamSide.B
                match.copy(
                    innings = listOf(
                        InningsScore(t1Side.other, 100, 2, 30, maxBalls = 30),
                        InningsScore(t1Side, 20, 9, 12, maxBalls = 30, allOut = true),
                    ),
                )
            } else match
        }
        val table = TournamentEngine.pointsTable(league, played, 0)
        assertTrue(table.all { it.points == 2 })
        assertEquals("t3", table.first().teamId)
        assertEquals("t1", table.last().teamId)
        // All out in 2 overs still counts as 5 overs faced: 50 + 20 in 10 overs, against 40 + 100 in 10.
        val t1 = table.single { it.teamId == "t1" }
        assertEquals(60, t1.ballsFaced)
        assertEquals((70.0 / 10) - (140.0 / 10), t1.netRunRate, 0.0001)
    }

    @Test
    fun groupsThenKnockouts() {
        val cup = tournament(TournamentFormat.GROUPS_KNOCKOUT, teams = 8, groups = 2, advance = 2)
        // Snake seeding: 1,4,5,8 in group 0; 2,3,6,7 in group 1.
        assertEquals(listOf("t1", "t4", "t5", "t8"), cup.teams.filter { it.group == 0 }.map { it.teamId }.sorted())
        val played = run(cup, favourites)
        val semis = played.filter { it.fixture!!.stage == Stage.SEMIFINAL }
        // Group winners meet runners-up from the other group.
        assertEquals(setOf(setOf("t1", "t3"), setOf("t2", "t4")), semis.map { setOf(it.teamA.teamId, it.teamB.teamId) }.toSet())
        assertEquals("t1", TournamentEngine.champion(cup, played))
    }

    @Test
    fun boxLeagueSendsTheTopFourToSemis() {
        val box = tournament(TournamentFormat.BOX_LEAGUE, teams = 6)
        val played = run(box, favourites)
        assertEquals(2, played.count { it.fixture!!.stage == Stage.SEMIFINAL })
        assertEquals(1, played.count { it.fixture!!.stage == Stage.FINAL })
        assertEquals("t1", TournamentEngine.champion(box, played))
    }

    @Test
    fun doubleOutKnocksTeamsOutAfterTwoLosses() {
        val cup = tournament(TournamentFormat.DOUBLE_OUT, teams = 6)
        val played = run(cup, favourites)
        val losses = played.mapNotNull { match ->
            val a = match.teamA.teamId!!
            val b = match.teamB.teamId!!
            if (favourites(a, b) == a) b else a
        }.groupingBy { it }.eachCount()
        assertEquals("t1", TournamentEngine.champion(cup, played))
        assertEquals(0, losses["t1"] ?: 0)
        // Everyone else lost twice, except the losing finalist who may stop at one or two.
        assertTrue((losses - "t1").values.all { it in 1..2 })
        assertEquals(1, played.count { it.fixture!!.stage == Stage.FINAL })
    }

    @Test
    fun bestOfThreeStopsAtTwoWins() {
        val series = tournament(TournamentFormat.BEST_OF_THREE, teams = 2)
        assertEquals(2, run(series, favourites).size)
        var game = 0
        val split = run(series) { a, b -> if (game++ == 0) b else a }
        assertEquals(3, split.size)
        assertEquals("t1", TournamentEngine.champion(series, split))
    }

    @Test
    fun customTournamentsAreUpToTheOrganiser() {
        val custom = tournament(TournamentFormat.CUSTOM, teams = 3)
        assertTrue(TournamentEngine.firstRound(custom).isEmpty())
        assertNull(TournamentEngine.champion(custom, emptyList()))
    }

    @Test
    fun anAbandonedLeagueFixtureIsAPointEachAndLeavesNetRunRateAlone() {
        val league = tournament(TournamentFormat.ROUND_ROBIN, teams = 2)
        val planned = TournamentEngine.firstRound(league).single()
        val abandoned = play(planned) { _, _ -> null }.copy(
            result = MatchResult(null, ending = MatchEnding.ABANDONED, reason = AbandonReason.RAIN),
        )
        val table = TournamentEngine.pointsTable(league, listOf(abandoned), 0)
        assertTrue(table.all { it.points == 1 && it.played == 1 && it.noResult == 1 && it.tied == 0 })
        assertTrue(table.all { it.ballsFaced == 0 && it.netRunRate == 0.0 })
    }

    @Test
    fun aWalkoverIsAWinWithoutRuns() {
        val league = tournament(TournamentFormat.ROUND_ROBIN, teams = 2)
        val planned = TournamentEngine.firstRound(league).single()
        val walkover = play(planned) { a, _ -> a }.copy(
            result = MatchResult(TeamSide.A, ending = MatchEnding.WALKOVER),
            innings = emptyList(),
        )
        val table = TournamentEngine.pointsTable(league, listOf(walkover), 0)
        assertEquals(planned.teamA, table.first().teamId)
        assertEquals(2, table.first().points)
        assertEquals(1, table.last().lost)
    }

    @Test
    fun anAbandonedKnockoutSendsTheHigherSeedThrough() {
        val cup = tournament(TournamentFormat.KNOCKOUT, teams = 2)
        val final = play(TournamentEngine.firstRound(cup).single()) { _, _ -> null }.copy(
            result = MatchResult(null, ending = MatchEnding.ABANDONED, reason = AbandonReason.BAD_LIGHT),
        )
        assertEquals("t1", TournamentEngine.winner(cup, final).teamId)
    }
}
