package com.harsh.desicrics.scoring

import com.harsh.desicrics.model.Delivery
import com.harsh.desicrics.model.ExtraType
import com.harsh.desicrics.model.MarginUnit
import com.harsh.desicrics.model.Match
import com.harsh.desicrics.model.MatchEvent
import com.harsh.desicrics.model.MatchResult
import com.harsh.desicrics.model.Player
import com.harsh.desicrics.model.RetiredReturn
import com.harsh.desicrics.model.RuleMode
import com.harsh.desicrics.model.Team
import com.harsh.desicrics.model.TeamSide

/**
 * Replays a match's deliveries into a full scorecard. Pure and deterministic so the app,
 * the server and tests all agree on the score.
 *
 * The match's [com.harsh.desicrics.model.MatchRules] decide when an innings ends and who may come in;
 * whether a ball was allowed is [validate]'s job. Simplifications: no byes off a no-ball, and a run out
 * removes the dismissed batter from whichever end they finished at after the completed runs.
 */
object ScoringEngine {

    fun compute(match: Match, deliveries: List<Delivery>, events: List<MatchEvent> = emptyList()): MatchState {
        // Balls and between-ball events replay together, in the order they were recorded.
        val timeline = (deliveries.map { it.seq to it } + events.map { it.seq to it }).sortedBy { it.first }.map { it.second }
        fun inningsItems(number: Int) = timeline.filter { (it as? Delivery)?.innings == number || (it as? MatchEvent)?.innings == number }
        // Penalties to the fielding side land on that side's own innings, so each innings collects the ones
        // awarded to it from both.
        val penalties = events.filterIsInstance<MatchEvent.Penalty>()
        fun penaltiesFor(innings: Int) = penalties.filter {
            (it.innings == innings && it.toBattingSide) || (it.innings != innings && !it.toBattingSide)
        }.sumOf { it.runs }
        // A revision in the first innings shortens both; one in the second only the chase.
        val revisions = events.filterIsInstance<MatchEvent.ReviseTarget>().sortedBy { it.seq }
        fun revisionFor(innings: Int) = revisions.lastOrNull { it.innings <= innings }
        val first = computeInnings(
            match, 1, match.battingFirst, inningsItems(1), target = null, penaltyRuns = penaltiesFor(1), revision = revisionFor(1),
        )
        if (!first.isComplete) {
            return MatchState(match, listOf(first), isComplete = false, result = null)
        }
        val chaseRevision = revisionFor(2)
        val second = computeInnings(
            match, 2, match.battingFirst.other, inningsItems(2),
            target = chaseRevision?.target ?: (first.runs + 1),
            penaltyRuns = penaltiesFor(2),
            revision = chaseRevision,
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
        /** [Delivery] and [MatchEvent] items in sequence order. */
        timeline: List<Any>,
        target: Int?,
        /** Penalty runs this innings is credited with, from either innings. */
        penaltyRuns: Int,
        /** The latest rain revision that applies to this innings, if any. */
        revision: MatchEvent.ReviseTarget?,
    ): InningsState {
        val battingTeam = match.team(battingSide)
        val bowlingTeam = match.team(battingSide.other)
        val maxBalls = (revision?.overs ?: match.oversPerInnings) * 6
        val rules = match.rules

        val batters = LinkedHashMap<String, BatterCard>()
        val bowlers = LinkedHashMap<String, BowlerCard>()
        var runs = penaltyRuns
        var wickets = 0
        var legalBalls = 0
        var extras = Extras(penalties = penaltyRuns)
        var striker: String? = null
        var nonStriker: String? = null
        var bowler: String? = null
        var previousOverBowler: String? = null
        val currentOver = mutableListOf<Delivery>()
        val overs = mutableListOf<OverSummary>()
        var overJustCompleted = false
        var overRuns = 0
        var freeHit = false
        var declared = false

        for (item in timeline) {
            if (item is MatchEvent) {
                when (item) {
                    is MatchEvent.Retire -> {
                        batters[item.playerId]?.let { card ->
                            val retirement = when {
                                item.out -> Retirement.OUT
                                rules.retireAtMode != RuleMode.OFF && card.runs >= rules.retireAtRuns -> Retirement.NOT_OUT
                                else -> Retirement.HURT
                            }
                            batters[item.playerId] = card.copy(retired = retirement, retiredAtWickets = wickets)
                        }
                        if (item.out) wickets++
                        if (striker == item.playerId) striker = null
                        if (nonStriker == item.playerId) nonStriker = null
                    }
                    is MatchEvent.SwapEnds -> striker = nonStriker.also { nonStriker = striker }
                    // The over carries on; the scorer picks who bowls the rest of it.
                    is MatchEvent.ReplaceBowler -> bowler = null
                    // Counted up front through penaltyRuns, since some belong to the other innings.
                    is MatchEvent.Penalty -> Unit
                    is MatchEvent.Declare -> declared = true
                    // Applied up front through revision, since a first-innings one shortens the chase too.
                    is MatchEvent.ReviseTarget -> Unit
                }
                continue
            }
            val d = item as Delivery
            if (overJustCompleted) {
                currentOver.clear()
                overJustCompleted = false
            }

            // A retired batter back at the crease is batting again.
            for (id in listOfNotNull(d.strikerId, d.nonStrikerId)) {
                val card = batters.getOrPut(id) { BatterCard(battingTeam.player(id)) }
                if (card.retired?.canReturn == true) batters[id] = card.copy(retired = null)
            }
            // A last batter on their own keeps the strike whatever they run.
            val alone = d.nonStrikerId == null
            val strikerCard = batters.getValue(d.strikerId)
            val bowlerCard = bowlers.getOrPut(d.bowlerId) { BowlerCard(bowlingTeam.player(d.bowlerId)) }

            // Before counting this ball, so a wide in the third slot is "x.3", like the ball that follows it.
            val ballNumber = "${legalBalls / 6}.${legalBalls % 6 + 1}"
            val overNumber = legalBalls / 6 + 1
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
            // A free hit carries over a wide, and a no-ball always gives a new one.
            freeHit = rules.freeHitMode != RuleMode.OFF &&
                (d.extra == ExtraType.NO_BALL || (freeHit && d.extra == ExtraType.WIDE))
            if (!alone && d.runsRun % 2 == 1) {
                striker = nonStriker.also { nonStriker = striker }
            }

            val event = BallEvent(
                ballNumber = ballNumber,
                delivery = d,
                striker = strikerCard.player,
                bowler = bowlerCard.player,
                playerOut = d.wicket?.let { battingTeam.player(it.playerOutId) },
                fielder = d.wicket?.fielderId?.let { bowlingTeam.player(it) },
            )
            val wicketsAfter = wickets + if (d.wicket != null) 1 else 0
            val over = overs.lastOrNull()?.takeIf { it.number == overNumber }
            val updated = OverSummary(
                number = overNumber,
                bowler = bowlerCard.player,
                balls = (over?.balls ?: emptyList()) + event,
                runs = (over?.runs ?: 0) + d.totalRuns,
                wickets = (over?.wickets ?: 0) + if (d.wicket != null) 1 else 0,
                scoreAfter = "$runs/$wicketsAfter",
            )
            if (over == null) overs += updated else overs[overs.lastIndex] = updated

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
                if (!alone) striker = nonStriker.also { nonStriker = striker }
                previousOverBowler = bowler
                bowler = null
                overRuns = 0
                overJustCompleted = true
            }
        }

        val atCrease = setOfNotNull(striker, nonStriker)
        val notOut = battingTeam.players.filter { player ->
            val card = batters[player.id]
            player.id !in atCrease && (card == null || !card.isOut)
        }
        val othersLeft = notOut.any { batters[it.id]?.retired == null }
        // Retired batters the rules don't let back in yet, whether the rule warns or enforces.
        val returnBlocked = if (rules.retiredReturnMode == RuleMode.OFF) emptySet() else notOut.mapNotNull { player ->
            val card = batters[player.id]?.takeIf { it.retired != null } ?: return@mapNotNull null
            val since = card.retiredAtWickets ?: return@mapNotNull null
            val allowed = when (rules.retiredReturn) {
                RetiredReturn.ANY_TIME -> true
                RetiredReturn.AFTER_WICKET -> wickets > since
                RetiredReturn.AT_END -> !othersLeft
                RetiredReturn.NEVER -> false
            }
            player.id.takeUnless { allowed }
        }.toSet()
        val canComeIn = if (rules.retiredReturnMode == RuleMode.ENFORCE) notOut.filter { it.id !in returnBlocked } else notOut

        // The last batter on their own faces every ball, whichever end they were at.
        if (rules.lastManStands && atCrease.size == 1 && canComeIn.isEmpty()) {
            striker = atCrease.single()
            nonStriker = null
        }

        val allOutWickets = battingTeam.players.size - if (rules.lastManStands) 0 else 1
        // Also over when nobody is left to fill the crease, e.g. retired batters the rules keep out.
        val battersNeeded = if (rules.lastManStands) 1 else 2
        // A fixture without squads yet hasn't started, so it can't be over.
        val isComplete = battingTeam.players.isNotEmpty() && (declared || wickets >= allOutWickets) ||
            atCrease.size + canComeIn.size < battersNeeded ||
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
            overs = overs.toList(),
            canComeIn = canComeIn,
            allOutWickets = allOutWickets,
            returnBlocked = returnBlocked,
            isFreeHit = freeHit,
            declared = declared,
            isRevised = revision != null,
            powerplayBalls = (rules.powerplayOvers * 6).coerceAtMost(maxBalls),
        )
    }

    /** Against the target, so a revised chase is judged on its par score (target − 1), as DLS does. */
    private fun result(first: InningsState, second: InningsState): MatchResult {
        val par = second.target!! - 1
        return when {
            second.runs > par -> MatchResult(second.battingSide, second.allOutWickets - second.wickets, MarginUnit.WICKETS)
            second.runs == par -> MatchResult.Tie
            else -> MatchResult(first.battingSide, par - second.runs, MarginUnit.RUNS)
        }
    }

    private fun Team.player(id: String): Player = players.firstOrNull { it.id == id } ?: Player(id, "Unknown")
}
