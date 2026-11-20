package com.harsh.desicrics.tournament

import com.harsh.desicrics.model.InningsScore
import com.harsh.desicrics.model.Match
import com.harsh.desicrics.model.MatchStatus
import com.harsh.desicrics.model.PointsRow
import com.harsh.desicrics.model.Stage
import com.harsh.desicrics.model.TeamSide
import com.harsh.desicrics.model.Tournament
import com.harsh.desicrics.model.TournamentFormat
import com.harsh.desicrics.model.TournamentTeam

/** A fixture to create: who plays whom, and where it sits. Team IDs are saved teams. */
data class PlannedFixture(
    val stage: Stage,
    val round: Int,
    val number: Int,
    val teamA: String,
    val teamB: String,
    val group: Int? = null,
)

/**
 * Tournament structure, as pure functions of the teams and the fixtures played so far, so the app and the
 * server agree and every format is easy to test.
 *
 * Fixtures are made a round at a time: [firstRound] when the organiser starts the tournament, then
 * [nextRound] whenever a fixture finishes, which returns the next round once the current one is complete.
 * A tied knockout match goes to the higher seed, since there's no super over yet.
 */
object TournamentEngine {

    /** Snake order (1-2-3-3-2-1…) so every group gets a fair spread of seeds. */
    fun assignGroups(teams: List<TournamentTeam>, groups: Int): List<TournamentTeam> =
        teams.sortedBy { it.seed }.mapIndexed { i, team ->
            val pass = i / groups
            val slot = i % groups
            team.copy(group = if (pass % 2 == 0) slot else groups - 1 - slot)
        }

    fun firstRound(tournament: Tournament): List<PlannedFixture> {
        val teams = tournament.confirmedTeams.sortedBy { it.seed }
        return when (tournament.format) {
            TournamentFormat.KNOCKOUT -> knockoutRound(round = 1, entrants = teams.drop(byeCount(teams.size)), field = teams.size)
            TournamentFormat.ROUND_ROBIN, TournamentFormat.BOX_LEAGUE -> league(teams.map { it.copy(group = 0) })
            TournamentFormat.GROUPS_KNOCKOUT -> league(teams)
            TournamentFormat.DOUBLE_OUT -> pairUp(round = 1, teams, Stage.ROUND)
            TournamentFormat.BEST_OF_THREE -> listOf(PlannedFixture(Stage.MATCH, 1, 1, teams[0].teamId, teams[1].teamId))
            TournamentFormat.CUSTOM -> emptyList()
        }
    }

    /** The next round, once every fixture so far has finished; empty while one is still to play or when it's over. */
    fun nextRound(tournament: Tournament, fixtures: List<Match>): List<PlannedFixture> {
        if (fixtures.isEmpty() || fixtures.any { it.status != MatchStatus.COMPLETED }) return emptyList()
        if (champion(tournament, fixtures) != null) return emptyList()
        val teams = tournament.confirmedTeams.sortedBy { it.seed }
        val round = fixtures.maxOf { it.fixture!!.round }
        val latest = fixtures.filter { it.fixture!!.round == round }
        return when (tournament.format) {
            TournamentFormat.KNOCKOUT -> {
                // Top seeds who had byes join the winners of round 1.
                val byes = if (round == 1) teams.take(byeCount(teams.size)) else emptyList()
                knockoutRound(round + 1, (byes + latest.map { winner(tournament, it) }).sortedBy { it.seed })
            }
            TournamentFormat.ROUND_ROBIN, TournamentFormat.GROUPS_KNOCKOUT, TournamentFormat.BOX_LEAGUE -> {
                val advance = advanceCount(tournament)
                if (latest.first().fixture!!.stage == Stage.LEAGUE) {
                    if (advance == 0) emptyList() else knockoutRound(round + 1, playoffSeeds(tournament, fixtures, advance))
                } else {
                    knockoutRound(round + 1, latest.map { winner(tournament, it) }.sortedBy { it.seed })
                }
            }
            TournamentFormat.DOUBLE_OUT -> {
                val losses = lossCounts(tournament, fixtures)
                val alive = teams.filter { (losses[it.teamId] ?: 0) < 2 }
                if (alive.size == 2) listOf(PlannedFixture(Stage.FINAL, round + 1, 1, alive[0].teamId, alive[1].teamId))
                else {
                    val (clean, once) = alive.partition { (losses[it.teamId] ?: 0) == 0 }
                    doubleOutRound(round + 1, clean, once)
                }
            }
            TournamentFormat.BEST_OF_THREE ->
                if (fixtures.size >= 3) emptyList()
                else listOf(PlannedFixture(Stage.MATCH, round + 1, 1, teams[0].teamId, teams[1].teamId))
            TournamentFormat.CUSTOM -> emptyList()
        }
    }

    /** The winner's team ID once the tournament is decided, else null. Custom tournaments have none. */
    fun champion(tournament: Tournament, fixtures: List<Match>): String? {
        val teams = tournament.confirmedTeams.sortedBy { it.seed }
        val done = fixtures.filter { it.status == MatchStatus.COMPLETED }
        return when (tournament.format) {
            TournamentFormat.KNOCKOUT, TournamentFormat.BOX_LEAGUE, TournamentFormat.GROUPS_KNOCKOUT ->
                done.firstOrNull { it.fixture?.stage == Stage.FINAL }?.let { winner(tournament, it).teamId }
            TournamentFormat.ROUND_ROBIN ->
                if (advanceCount(tournament) == 0) {
                    val league = fixtures.filter { it.fixture?.stage == Stage.LEAGUE }
                    if (league.isNotEmpty() && league.all { it.status == MatchStatus.COMPLETED }) pointsTable(tournament, fixtures, 0).first().teamId
                    else null
                } else done.firstOrNull { it.fixture?.stage == Stage.FINAL }?.let { winner(tournament, it).teamId }
            TournamentFormat.DOUBLE_OUT -> {
                val losses = lossCounts(tournament, done)
                val alive = teams.filter { (losses[it.teamId] ?: 0) < 2 }
                done.firstOrNull { it.fixture?.stage == Stage.FINAL }?.let { winner(tournament, it).teamId }
                    ?: alive.singleOrNull()?.teamId
            }
            TournamentFormat.BEST_OF_THREE -> {
                val wins = done.mapNotNull { match -> match.result?.winner?.let { match.team(it).teamId } }.groupingBy { it }.eachCount()
                wins.entries.firstOrNull { it.value >= 2 }?.key
                    ?: if (done.size >= 3) teams.maxBy { wins[it.teamId] ?: 0 }.teamId else null
            }
            TournamentFormat.CUSTOM -> null
        }
    }

    /**
     * A group's table from its completed league fixtures, best first: points, then net run rate, then wins.
     * An all-out side counts as having batted its full overs, as in the playing conditions NRR comes from.
     * Only matches played to a finish count towards NRR; abandoned, conceded and walked-over ones don't.
     */
    fun pointsTable(tournament: Tournament, fixtures: List<Match>, group: Int): List<PointsRow> {
        val members = tournament.confirmedTeams.filter { (it.group ?: 0) == group }
        val rows = members.associate { it.teamId to PointsRow(it.teamId, it.name) }.toMutableMap()
        val league = fixtures.filter { it.fixture?.stage == Stage.LEAGUE && it.fixture.group == group && it.status == MatchStatus.COMPLETED }
        for (match in league) {
            val result = match.result ?: continue
            for (side in listOf(match.teamA, match.teamB)) {
                val id = side.teamId ?: continue
                val row = rows[id] ?: continue
                val mySide = if (side === match.teamA) TeamSide.A else TeamSide.B
                val batting = match.innings.firstOrNull { it.battingSide == mySide }
                val bowling = match.innings.firstOrNull { it.battingSide != mySide }
                fun faced(score: InningsScore?) =
                    score?.let { if (it.allOut) it.maxBalls ?: (match.oversPerInnings * 6) else it.legalBalls } ?: 0
                val forNrr = result.ending == null
                rows[id] = if (!forNrr) row.copy(
                    played = row.played + 1,
                    won = row.won + if (result.winner == mySide) 1 else 0,
                    lost = row.lost + if (result.winner == mySide.other) 1 else 0,
                    noResult = row.noResult + if (result.isNoResult) 1 else 0,
                ) else row.copy(
                    played = row.played + 1,
                    won = row.won + if (result.winner == mySide) 1 else 0,
                    lost = row.lost + if (result.winner == mySide.other) 1 else 0,
                    tied = row.tied + if (result.isTie) 1 else 0,
                    runsFor = row.runsFor + (batting?.runs ?: 0),
                    ballsFaced = row.ballsFaced + faced(batting),
                    runsAgainst = row.runsAgainst + (bowling?.runs ?: 0),
                    ballsBowled = row.ballsBowled + faced(bowling),
                )
            }
        }
        val seeds = members.associate { it.teamId to it.seed }
        return rows.values.sortedWith(
            compareByDescending<PointsRow> { it.points }.thenByDescending { it.netRunRate }.thenByDescending { it.won }.thenBy { seeds[it.teamId] },
        )
    }

    /** How many teams a bracket of [teams] leaves without a first-round opponent. */
    private fun byeCount(teams: Int): Int {
        var size = 1
        while (size < teams) size *= 2
        return size - teams
    }

    /**
     * Pairs [entrants] (in seed order) strongest against weakest. The stage is named by [field], everyone
     * still in it, so a first round with byes is still a quarter-final when eight places are left.
     */
    private fun knockoutRound(round: Int, entrants: List<TournamentTeam>, field: Int = entrants.size): List<PlannedFixture> {
        if (entrants.size < 2) return emptyList()
        val stage = when (field) {
            2 -> Stage.FINAL
            in 3..4 -> Stage.SEMIFINAL
            in 5..8 -> Stage.QUARTERFINAL
            else -> Stage.ROUND
        }
        return (0 until entrants.size / 2).map { i ->
            PlannedFixture(stage, round, i + 1, entrants[i].teamId, entrants[entrants.size - 1 - i].teamId)
        }
    }

    /** Every team plays every other in its group once, by the circle method, one matchday per round. */
    private fun league(teams: List<TournamentTeam>): List<PlannedFixture> {
        val fixtures = mutableListOf<PlannedFixture>()
        for ((group, members) in teams.groupBy { it.group ?: 0 }.toSortedMap()) {
            val ids: MutableList<String?> = members.sortedBy { it.seed }.map { it.teamId }.toMutableList()
            if (ids.size % 2 == 1) ids += null // a bye each matchday
            val rounds = ids.size - 1
            for (r in 0 until rounds) {
                var number = fixtures.count { it.round == r + 1 }
                for (i in 0 until ids.size / 2) {
                    val home = ids[i]
                    val away = ids[ids.size - 1 - i]
                    if (home != null && away != null) fixtures += PlannedFixture(Stage.LEAGUE, r + 1, ++number, home, away, group)
                }
                // Keep the first team fixed and rotate the rest.
                ids.add(1, ids.removeAt(ids.size - 1))
            }
        }
        return fixtures
    }

    /** Group winners first, then runners-up, and so on; within each place, better record first. */
    private fun playoffSeeds(tournament: Tournament, fixtures: List<Match>, advance: Int): List<TournamentTeam> {
        val byId = tournament.confirmedTeams.associateBy { it.teamId }
        val tables = (0 until tournament.groups).map { pointsTable(tournament, fixtures, it) }
        val qualifiers = (0 until advance).flatMap { place ->
            tables.mapNotNull { it.getOrNull(place) }.sortedWith(compareByDescending<PointsRow> { it.points }.thenByDescending { it.netRunRate })
        }
        // Re-seed by finishing place so the knockout pairs 1st against last.
        return qualifiers.mapIndexed { i, row -> byId.getValue(row.teamId).copy(seed = i + 1) }
    }

    private fun advanceCount(tournament: Tournament): Int = when (tournament.format) {
        TournamentFormat.BOX_LEAGUE -> 4
        else -> tournament.advance
    }

    /** Pairs teams in order; with an odd count, the last sits the round out. */
    private fun pairUp(round: Int, teams: List<TournamentTeam>, stage: Stage): List<PlannedFixture> =
        teams.chunked(2).filter { it.size == 2 }.mapIndexed { i, (a, b) -> PlannedFixture(stage, round, i + 1, a.teamId, b.teamId) }

    /**
     * Unbeaten teams play each other, as do teams with one loss. If both groups have a team left over, those
     * two play each other; if only one does, that team sits the round out.
     */
    private fun doubleOutRound(round: Int, clean: List<TournamentTeam>, once: List<TournamentTeam>): List<PlannedFixture> {
        val pairs = clean.chunked(2).filter { it.size == 2 } + once.chunked(2).filter { it.size == 2 }
        val leftovers = listOfNotNull(clean.takeIf { it.size % 2 == 1 }?.last(), once.takeIf { it.size % 2 == 1 }?.last())
        val all = if (leftovers.size == 2) pairs + listOf(leftovers) else pairs
        return all.mapIndexed { i, (a, b) -> PlannedFixture(Stage.ROUND, round, i + 1, a.teamId, b.teamId) }
    }

    private fun lossCounts(tournament: Tournament, fixtures: List<Match>): Map<String, Int> =
        fixtures.filter { it.status == MatchStatus.COMPLETED }.mapNotNull { loser(tournament, it)?.teamId }.groupingBy { it }.eachCount()

    /** Who goes through: the winner, or on a tie or no result the higher seed. */
    fun winner(tournament: Tournament, match: Match): TournamentTeam {
        val teams = tournament.confirmedTeams.associateBy { it.teamId }
        val a = teams.getValue(match.teamA.teamId!!)
        val b = teams.getValue(match.teamB.teamId!!)
        return when (match.result?.winner) {
            TeamSide.A -> a
            TeamSide.B -> b
            null -> if (a.seed <= b.seed) a else b
        }
    }

    private fun loser(tournament: Tournament, match: Match): TournamentTeam? {
        if (match.result == null) return null
        val win = winner(tournament, match)
        val teams = tournament.confirmedTeams.associateBy { it.teamId }
        return listOfNotNull(teams[match.teamA.teamId], teams[match.teamB.teamId]).firstOrNull { it.teamId != win.teamId }
    }
}
