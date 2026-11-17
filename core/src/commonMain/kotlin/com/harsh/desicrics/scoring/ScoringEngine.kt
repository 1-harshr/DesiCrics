package com.harsh.desicrics.scoring

import com.harsh.desicrics.model.Delivery
import com.harsh.desicrics.model.ExtraType
import com.harsh.desicrics.model.MarginUnit
import com.harsh.desicrics.model.Match
import com.harsh.desicrics.model.MatchResult
import com.harsh.desicrics.model.Player
import com.harsh.desicrics.model.Team
import com.harsh.desicrics.model.TeamSide

/**
 * Replays a match's deliveries into a full scorecard. Pure and deterministic so the app,
 * the server and tests all agree on the score.
 *
 * Simplifications for the MVP: no retirements, no penalty runs, no byes off a no-ball,
 * no per-bowler over limits, and a run out removes the dismissed batter from whichever end
 * they finished at after the completed runs.
 */
object ScoringEngine {

    fun compute(match: Match, deliveries: List<Delivery>): MatchState {
        val sorted = deliveries.sortedBy { it.seq }
        val first = computeInnings(match, 1, match.battingFirst, sorted.filter { it.innings == 1 }, target = null)
        if (!first.isComplete) {
            return MatchState(match, listOf(first), isComplete = false, result = null)
        }
        val second = computeInnings(
            match, 2, match.battingFirst.other, sorted.filter { it.innings == 2 }, target = first.runs + 1,
        )
        return MatchState(
            match = match,
            innings = listOf(first, second),
            isComplete = second.isComplete,
            result = if (second.isComplete) result(first, second) else null,
        )
    }

    private fun computeInnings(
        match: Match,
        number: Int,
        battingSide: TeamSide,
        deliveries: List<Delivery>,
        target: Int?,
    ): InningsState {
        val battingTeam = match.team(battingSide)
        val bowlingTeam = match.team(battingSide.other)
        val maxBalls = match.oversPerInnings * 6

        val batters = LinkedHashMap<String, BatterCard>()
        val bowlers = LinkedHashMap<String, BowlerCard>()
        var runs = 0
        var wickets = 0
        var legalBalls = 0
        var extras = Extras()
        var striker: String? = null
        var nonStriker: String? = null
        var bowler: String? = null
        var previousOverBowler: String? = null
        val currentOver = mutableListOf<Delivery>()
        var overJustCompleted = false
        var overRuns = 0

        for (d in deliveries) {
            if (overJustCompleted) {
                currentOver.clear()
                overJustCompleted = false
            }

            val strikerCard = batters.getOrPut(d.strikerId) { BatterCard(battingTeam.player(d.strikerId)) }
            batters.getOrPut(d.nonStrikerId) { BatterCard(battingTeam.player(d.nonStrikerId)) }
            val bowlerCard = bowlers.getOrPut(d.bowlerId) { BowlerCard(bowlingTeam.player(d.bowlerId)) }

            runs += d.totalRuns
            if (d.isLegal) legalBalls++
            extras += d
            overRuns += d.bowlerRuns
            currentOver += d

            batters[d.strikerId] = strikerCard.copy(
                runs = strikerCard.runs + d.runsOffBat,
                balls = strikerCard.balls + if (d.extra == ExtraType.WIDE) 0 else 1,
                fours = strikerCard.fours + if (d.runsOffBat == 4) 1 else 0,
                sixes = strikerCard.sixes + if (d.runsOffBat == 6) 1 else 0,
            )
            bowlers[d.bowlerId] = bowlerCard.copy(
                legalBalls = bowlerCard.legalBalls + if (d.isLegal) 1 else 0,
                runs = bowlerCard.runs + d.bowlerRuns,
                wickets = bowlerCard.wickets + if (d.wicket?.kind?.creditedToBowler == true) 1 else 0,
            )

            striker = d.strikerId
            nonStriker = d.nonStrikerId
            bowler = d.bowlerId
            if (d.runsRun % 2 == 1) {
                striker = nonStriker.also { nonStriker = striker }
            }

            d.wicket?.let { wicket ->
                wickets++
                batters[wicket.playerOutId]?.let { out ->
                    batters[wicket.playerOutId] = out.copy(
                        dismissal = Dismissal(
                            kind = wicket.kind,
                            bowler = bowlerCard.player.takeIf { wicket.kind.creditedToBowler },
                            fielder = wicket.fielderId?.let { bowlingTeam.player(it) },
                        ),
                    )
                }
                if (striker == wicket.playerOutId) striker = null
                if (nonStriker == wicket.playerOutId) nonStriker = null
            }

            if (d.isLegal && legalBalls % 6 == 0) {
                if (overRuns == 0) {
                    bowlers.getValue(d.bowlerId).let { bowlers[d.bowlerId] = it.copy(maidens = it.maidens + 1) }
                }
                striker = nonStriker.also { nonStriker = striker }
                previousOverBowler = bowler
                bowler = null
                overRuns = 0
                overJustCompleted = true
            }
        }

        val isComplete = wickets >= battingTeam.players.size - 1 ||
            legalBalls >= maxBalls ||
            (target != null && runs >= target)

        return InningsState(
            number = number,
            battingSide = battingSide,
            battingTeam = battingTeam,
            bowlingTeam = bowlingTeam,
            runs = runs,
            wickets = wickets,
            legalBalls = legalBalls,
            maxBalls = maxBalls,
            extras = extras,
            batters = batters.values.toList(),
            bowlers = bowlers.values.toList(),
            currentOver = currentOver.toList(),
            strikerId = striker,
            nonStrikerId = nonStriker,
            bowlerId = bowler,
            previousOverBowlerId = previousOverBowler,
            target = target,
            isComplete = isComplete,
        )
    }

    private fun result(first: InningsState, second: InningsState): MatchResult = when {
        second.runs > first.runs ->
            MatchResult(second.battingSide, second.allOutWickets - second.wickets, MarginUnit.WICKETS)
        second.runs == first.runs -> MatchResult.Tie
        else -> MatchResult(first.battingSide, first.runs - second.runs, MarginUnit.RUNS)
    }

    private fun Team.player(id: String): Player = players.firstOrNull { it.id == id } ?: Player(id, "Unknown")
}
