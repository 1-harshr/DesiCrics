package com.harsh.desicrics.stats

import com.harsh.desicrics.api.MatchDetails
import com.harsh.desicrics.model.Badge
import com.harsh.desicrics.model.BattingStats
import com.harsh.desicrics.model.BowlingStats
import com.harsh.desicrics.model.CareerStats
import com.harsh.desicrics.model.DismissalKind
import com.harsh.desicrics.model.FieldZone
import com.harsh.desicrics.model.FieldingStats
import com.harsh.desicrics.model.FormEntry
import com.harsh.desicrics.model.MatchStatus
import com.harsh.desicrics.model.PlayerMatchLine
import com.harsh.desicrics.model.TeamSide
import com.harsh.desicrics.scoring.InningsState
import com.harsh.desicrics.scoring.MatchState
import com.harsh.desicrics.scoring.ScoringEngine
import kotlin.math.roundToInt

/**
 * Most Valuable Player points: one documented formula that ranks everyone in a match from its deliveries.
 *
 * - **Batting:** 1 per run, +1 per four, +2 per six, +10 for a fifty or +25 for a hundred, and from 10
 *   balls faced, (strike rate − 100) ÷ 10, kept between −5 and +10.
 * - **Bowling:** 20 per wicket, 4 per maiden, +5 for three wickets or +15 for five, and from 2 overs
 *   bowled, (6 − economy) × 2, kept between −10 and +10.
 * - **Fielding:** 8 per catch, 10 per run out or stumping.
 */
object MvpPoints {
    /** Tenths of a point for each match player ID who did anything. */
    fun forMatch(state: MatchState): Map<String, Int> {
        val points = mutableMapOf<String, Double>()
        for (innings in state.innings) {
            for (card in innings.batters) {
                var p = card.runs + card.fours + card.sixes * 2.0
                p += if (card.runs >= 100) 25.0 else if (card.runs >= 50) 10.0 else 0.0
                if (card.balls >= 10) p += ((card.strikeRate - 100) / 10).coerceIn(-5.0, 10.0)
                points.merge(card.player.id, p, Double::plus)
            }
            for (card in innings.bowlers) {
                var p = card.wickets * 20.0 + card.maidens * 4.0
                p += if (card.wickets >= 5) 15.0 else if (card.wickets >= 3) 5.0 else 0.0
                if (card.legalBalls >= 12) p += ((6 - card.economy) * 2).coerceIn(-10.0, 10.0)
                points.merge(card.player.id, p, Double::plus)
            }
            for ((fielder, kind) in fielding(innings)) {
                points.merge(fielder, if (kind == DismissalKind.CAUGHT) 8.0 else 10.0, Double::plus)
            }
        }
        return points.mapValues { (it.value * 10).roundToInt() }
    }

    /** The fielder behind each dismissal that had one: catches, run outs and stumpings. */
    internal fun fielding(innings: InningsState): List<Pair<String, DismissalKind>> =
        innings.batters.mapNotNull { card ->
            val dismissal = card.dismissal ?: return@mapNotNull null
            val fielder = dismissal.fielder ?: return@mapNotNull null
            if (dismissal.kind in setOf(DismissalKind.CAUGHT, DismissalKind.RUN_OUT, DismissalKind.STUMPED)) fielder.id to dismissal.kind else null
        }
}

/** Career stats as a projection: match lines from finished matches, then sums over lines. */
object StatsEngine {

    /** One line per linked player in a completed match; guests (no profile) don't get one. */
    fun lines(details: MatchDetails): List<PlayerMatchLine> {
        val match = details.match
        if (match.status != MatchStatus.COMPLETED) return emptyList()
        val state = ScoringEngine.compute(match, details.deliveries, details.events)
        // A match called off or walked over before a ball was bowled has nothing to add to anyone's career.
        if (details.deliveries.isEmpty()) return emptyList()
        val mvp = MvpPoints.forMatch(state)
        // No player of the match when it didn't finish.
        val best = mvp.maxByOrNull { it.value }?.key.takeIf { match.result?.ending == null }
        val lines = mutableListOf<PlayerMatchLine>()
        for (side in listOf(match.teamA, match.teamB)) {
            val opponent = if (side === match.teamA) match.teamB else match.teamA
            val mySide = if (side === match.teamA) TeamSide.A else TeamSide.B
            for (player in side.players) {
                val profile = player.profileId ?: continue
                val batting = state.innings.firstOrNull { it.battingSide == mySide }
                val bowling = state.innings.firstOrNull { it.battingSide != mySide }
                val bat = batting?.batters?.firstOrNull { it.player.id == player.id }
                val bowl = bowling?.bowlers?.firstOrNull { it.player.id == player.id }
                val fielding = bowling?.let { MvpPoints.fielding(it) }.orEmpty().filter { it.first == player.id }
                lines += PlayerMatchLine(
                    profileId = profile,
                    matchId = match.id,
                    playedAt = match.createdAt,
                    team = side.name,
                    opponent = opponent.name,
                    opponentTeamId = opponent.teamId,
                    oversPerInnings = match.oversPerInnings,
                    tournamentId = match.fixture?.tournamentId,
                    batted = bat != null,
                    runs = bat?.runs ?: 0,
                    balls = bat?.balls ?: 0,
                    fours = bat?.fours ?: 0,
                    sixes = bat?.sixes ?: 0,
                    notOut = bat != null && !bat.isOut,
                    bowledBalls = bowl?.legalBalls ?: 0,
                    runsConceded = bowl?.runs ?: 0,
                    wickets = bowl?.wickets ?: 0,
                    maidens = bowl?.maidens ?: 0,
                    catches = fielding.count { it.second == DismissalKind.CAUGHT },
                    runOuts = fielding.count { it.second == DismissalKind.RUN_OUT },
                    stumpings = fielding.count { it.second == DismissalKind.STUMPED },
                    mvpTenths = mvp[player.id] ?: 0,
                    playerOfTheMatch = player.id == best,
                    zones = batting?.zoneRuns(player.id).orEmpty(),
                    won = match.result?.winner == mySide,
                )
            }
        }
        return lines
    }

    /** Adds up [lines] (one player's) into career stats. */
    fun career(profileId: String, lines: List<PlayerMatchLine>): CareerStats {
        val ordered = lines.sortedBy { it.playedAt }
        val batted = ordered.filter { it.batted }
        val bowled = ordered.filter { it.bowledBalls > 0 }
        val top = batted.maxWithOrNull(compareBy<PlayerMatchLine> { it.runs }.thenBy { it.notOut })
        val bestBowling = bowled.maxWithOrNull(compareBy<PlayerMatchLine> { it.wickets }.thenByDescending { it.runsConceded })
        val batting = BattingStats(
            innings = batted.size,
            notOuts = batted.count { it.notOut },
            runs = batted.sumOf { it.runs },
            balls = batted.sumOf { it.balls },
            fours = batted.sumOf { it.fours },
            sixes = batted.sumOf { it.sixes },
            highest = top?.runs ?: 0,
            highestNotOut = top?.notOut ?: false,
            fifties = batted.count { it.runs in 50..99 },
            hundreds = batted.count { it.runs >= 100 },
            ducks = batted.count { it.runs == 0 && !it.notOut },
        )
        val bowling = BowlingStats(
            innings = bowled.size,
            balls = bowled.sumOf { it.bowledBalls },
            runs = bowled.sumOf { it.runsConceded },
            wickets = bowled.sumOf { it.wickets },
            maidens = bowled.sumOf { it.maidens },
            bestWickets = bestBowling?.wickets ?: 0,
            bestRuns = bestBowling?.runsConceded ?: 0,
            threeWickets = bowled.count { it.wickets in 3..4 },
            fiveWickets = bowled.count { it.wickets >= 5 },
        )
        val zones = mutableMapOf<FieldZone, Int>()
        ordered.forEach { line -> line.zones.forEach { (zone, runs) -> zones.merge(zone, runs, Int::plus) } }
        val stats = CareerStats(
            profileId = profileId,
            matches = ordered.size,
            won = ordered.count { it.won },
            batting = batting,
            bowling = bowling,
            fielding = FieldingStats(ordered.sumOf { it.catches }, ordered.sumOf { it.runOuts }, ordered.sumOf { it.stumpings }),
            mvpTenths = ordered.sumOf { it.mvpTenths },
            playerOfTheMatch = ordered.count { it.playerOfTheMatch },
            form = ordered.takeLast(FORM_SIZE).map { FormEntry(it.matchId, it.playedAt, it.runs, it.notOut, it.wickets, it.batted) },
            zones = zones,
        )
        return stats.copy(badges = badges(stats))
    }

    private fun badges(stats: CareerStats): List<Badge> = buildList {
        if (stats.batting.fifties + stats.batting.hundreds > 0) add(Badge.FIRST_FIFTY)
        if (stats.batting.hundreds > 0) add(Badge.CENTURY)
        if (stats.bowling.fiveWickets > 0) add(Badge.FIVE_WICKET_HAUL)
        if (stats.batting.runs >= 1000) add(Badge.RUNS_1000) else if (stats.batting.runs >= 500) add(Badge.RUNS_500)
        if (stats.bowling.wickets >= 50) add(Badge.WICKETS_50) else if (stats.bowling.wickets >= 25) add(Badge.WICKETS_25)
        if (stats.matches >= 50) add(Badge.MATCHES_50) else if (stats.matches >= 25) add(Badge.MATCHES_25)
        if (stats.playerOfTheMatch > 0) add(Badge.PLAYER_OF_THE_MATCH)
    }

    private const val FORM_SIZE = 10
}
