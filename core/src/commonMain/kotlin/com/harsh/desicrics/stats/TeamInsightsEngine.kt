package com.harsh.desicrics.stats

import com.harsh.desicrics.api.MatchDetails
import com.harsh.desicrics.model.DismissalKind
import com.harsh.desicrics.model.InningsPhase
import com.harsh.desicrics.model.MatchStatus
import com.harsh.desicrics.model.Performer
import com.harsh.desicrics.model.PhaseStat
import com.harsh.desicrics.model.Player
import com.harsh.desicrics.model.TeamInsights
import com.harsh.desicrics.model.TeamSide
import com.harsh.desicrics.model.WinRecord
import com.harsh.desicrics.scoring.ScoringEngine
import kotlin.math.roundToInt

/** Adds up a saved team's completed matches into [TeamInsights]. */
object TeamInsightsEngine {
    private const val TOP = 3

    /**
     * The phase over [over] (1-based) falls in, splitting [overs] into thirds rounded up, so a 10-over
     * innings is overs 1–4, 5–6 and 7–10.
     */
    fun phaseOf(over: Int, overs: Int): InningsPhase {
        val third = (overs + 2) / 3
        return when {
            over <= third -> InningsPhase.START
            over > overs - third -> InningsPhase.FINISH
            else -> InningsPhase.MIDDLE
        }
    }

    fun compute(teamId: String, matches: List<MatchDetails>): TeamInsights {
        var won = 0
        var lost = 0
        var tied = 0
        var first = WinRecord()
        var chasing = WinRecord()
        var toss = WinRecord()
        val firstTotals = mutableListOf<Int>()
        val batting = InningsPhase.entries.associateWith { PhaseStat(it) }.toMutableMap()
        val bowling = InningsPhase.entries.associateWith { PhaseStat(it) }.toMutableMap()
        val howOut = mutableMapOf<DismissalKind, Int>()
        val runs = Tally()
        val wickets = Tally()

        for (details in matches) {
            val match = details.match
            if (match.status != MatchStatus.COMPLETED) continue
            val side = when (teamId) {
                match.teamA.teamId -> TeamSide.A
                match.teamB.teamId -> TeamSide.B
                else -> continue
            }
            val result = match.result ?: continue
            val state = ScoringEngine.compute(match, details.deliveries, details.events)
            val win = result.winner == side
            when (result.winner) {
                null -> tied++
                side -> won++
                else -> lost++
            }
            val wonIt = if (win) 1 else 0
            if (match.battingFirst == side) {
                first = WinRecord(first.played + 1, first.won + wonIt)
                state.innings.firstOrNull()?.let { firstTotals += it.runs }
            } else {
                chasing = WinRecord(chasing.played + 1, chasing.won + wonIt)
            }
            if (match.tossWinner == side) toss = WinRecord(toss.played + 1, toss.won + wonIt)

            val ours = mutableSetOf<String>()
            val theirs = mutableSetOf<String>()
            for (innings in state.innings) {
                val phases = if (innings.battingSide == side) batting else bowling
                for (over in innings.overs) {
                    val phase = phaseOf(over.number, match.oversPerInnings)
                    val stat = phases.getValue(phase)
                    phases[phase] = stat.copy(
                        runs = stat.runs + over.runs,
                        balls = stat.balls + over.balls.count { it.delivery.isLegal },
                        wickets = stat.wickets + over.wickets,
                    )
                }
                if (innings.battingSide == side) {
                    for (card in innings.batters) {
                        card.dismissal?.let { howOut.merge(it.kind, 1, Int::plus) }
                        runs.add(card.player, card.runs, ours)
                    }
                } else {
                    for (card in innings.bowlers) wickets.add(card.player, card.wickets, theirs)
                }
            }
        }

        return TeamInsights(
            teamId = teamId,
            played = won + lost + tied,
            won = won,
            lost = lost,
            tied = tied,
            battingFirst = first,
            chasing = chasing,
            tossWon = toss,
            averageFirstInnings = firstTotals.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
            batting = InningsPhase.entries.map { batting.getValue(it) },
            bowling = InningsPhase.entries.map { bowling.getValue(it) },
            howOut = howOut.entries.sortedByDescending { it.value }.associate { it.toPair() },
            topRunScorers = runs.top(),
            topWicketTakers = wickets.top(),
        )
    }

    /** Totals per person: by profile when linked, else by name, since a typed-in name is all there is. */
    private class Tally {
        private val names = mutableMapOf<String, Player>()
        private val values = mutableMapOf<String, Int>()
        private val matches = mutableMapOf<String, Int>()

        /** [seen] holds who's already counted in this match, so a second innings isn't a second match. */
        fun add(player: Player, value: Int, seen: MutableSet<String>) {
            val key = player.profileId ?: "name:${player.name.trim().lowercase()}"
            names[key] = player
            values.merge(key, value, Int::plus)
            if (seen.add(key)) matches.merge(key, 1, Int::plus)
        }

        fun top(): List<Performer> = values.entries
            .filter { it.value > 0 }
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { matches[it.key] })
            .take(TOP)
            .map { (key, value) ->
                val player = names.getValue(key)
                Performer(player.name, player.profileId, value, matches[key] ?: 0)
            }
    }
}
