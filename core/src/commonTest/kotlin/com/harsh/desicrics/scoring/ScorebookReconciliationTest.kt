package com.harsh.desicrics.scoring

import com.harsh.desicrics.model.Delivery
import com.harsh.desicrics.model.DismissalKind
import com.harsh.desicrics.model.ExtraType
import com.harsh.desicrics.model.Match
import com.harsh.desicrics.model.MatchEvent
import com.harsh.desicrics.model.MatchRules
import com.harsh.desicrics.model.Player
import com.harsh.desicrics.model.Team
import com.harsh.desicrics.model.TeamSide
import com.harsh.desicrics.model.TossDecision
import com.harsh.desicrics.model.Wicket
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Phase 1 exit check: a 20-over match scored ball by ball must reconcile the way a paper scorebook
 * does. Seeded random matches go through the same validation as the app, then every innings is checked
 * against the scorer's cross-checks: batting plus extras equals the total, bowling plus byes, leg byes and
 * penalties equals the total, and the bowlers' balls add up to the innings'.
 */
class ScorebookReconciliationTest {

    private fun team(prefix: String, name: String) = Team(name, (1..11).map { Player("$prefix$it", "$name $it") })

    private fun match(rules: MatchRules) = Match(
        id = "m", createdBy = "u", createdAt = 0,
        teamA = team("a", "Strikers"), teamB = team("b", "Royals"),
        oversPerInnings = 20, tossWinner = TeamSide.A, tossDecision = TossDecision.BAT,
        rules = rules,
    )

    /** Plays a whole match with [seed]; every ball must pass validation, as it would in the app. */
    private fun play(seed: Int, rules: MatchRules = MatchRules()): MatchState {
        val random = Random(seed)
        val match = match(rules)
        val deliveries = mutableListOf<Delivery>()
        val events = mutableListOf<MatchEvent>()
        fun state() = ScoringEngine.compute(match, deliveries, events)
        var guard = 0

        while (!state().isComplete && guard++ < 2000) {
            val s = state()
            val innings = s.current
            val nextSeq = deliveries.size + events.size
            val available = innings.canComeIn.map { it.id }
            val striker = innings.strikerId ?: available.first()
            // Null once the last batter is alone, where the rules allow it.
            val nonStriker = innings.nonStrikerId ?: available.firstOrNull { it != striker }
            // Bowlers from the tail up, skipping anyone the rules stop.
            val bowler = innings.bowlerId ?: innings.bowlingTeam.players.asReversed()
                .first { s.bowlerRuleBreak(it.id) == null && it.id != innings.previousOverBowlerId }.id

            if (random.nextInt(100) == 0) {
                events += MatchEvent.Penalty(nextSeq, innings.number, toBattingSide = random.nextBoolean(), runs = 5)
                continue
            }
            val roll = random.nextInt(100)
            fun ball(runs: Int = 0, extra: ExtraType? = null, extraRuns: Int = 0, wicket: Wicket? = null) = Delivery(
                nextSeq, innings.number, striker, nonStriker, bowler, runs, extra, extraRuns, wicket,
                extraPenalty = extra?.let { rules.penalty(it) } ?: 1,
                countsAsBall = (extra == ExtraType.WIDE || extra == ExtraType.NO_BALL) && rules.countsAsBall(extra),
            )
            val d = when {
                roll < 5 -> ball(extra = ExtraType.WIDE, extraRuns = rules.widePenalty + random.nextInt(2))
                roll < 8 -> ball(runs = listOf(0, 1, 4).random(random), extra = ExtraType.NO_BALL, extraRuns = rules.noBallPenalty)
                roll < 11 -> ball(extra = ExtraType.BYE, extraRuns = 1 + random.nextInt(4))
                roll < 13 -> ball(extra = ExtraType.LEG_BYE, extraRuns = 1 + random.nextInt(2))
                roll < 18 && !innings.isFreeHit -> ball(wicket = Wicket(DismissalKind.CAUGHT, striker, fielderId = bowler))
                roll < 20 -> ball(runs = random.nextInt(3), wicket = Wicket(DismissalKind.RUN_OUT, listOfNotNull(striker, nonStriker).random(random)))
                else -> ball(runs = listOf(0, 0, 0, 1, 1, 1, 2, 2, 3, 4, 4, 6).random(random))
            }
            assertNull(s.validate(d, nextSeq), "seed $seed, ball $d")
            deliveries += d
        }
        return state()
    }

    private fun assertReconciles(seed: Int, state: MatchState) {
        assertTrue(state.isComplete, "seed $seed didn't finish")
        assertNotNull(state.result)
        for (innings in state.innings) {
            val context = "seed $seed, innings ${innings.number}"
            val extras = innings.extras
            assertEquals(innings.runs, innings.batters.sumOf { it.runs } + extras.total, "$context: batting + extras")
            assertEquals(
                innings.runs,
                innings.bowlers.sumOf { it.runs } + extras.byes + extras.legByes + extras.penalties,
                "$context: bowling + byes + leg byes + penalties",
            )
            assertEquals(innings.legalBalls, innings.bowlers.sumOf { it.legalBalls }, "$context: balls")
            assertEquals(innings.wickets, innings.batters.count { it.isOut }, "$context: wickets")
            assertTrue(innings.bowlers.sumOf { it.wickets } <= innings.wickets, "$context: bowlers' wickets")
            assertTrue(innings.legalBalls <= 120, "$context: 20 overs at most")
            assertEquals(innings.runs, innings.overs.sumOf { it.runs } + extras.penalties, "$context: over by over")
        }
        val (first, second) = state.innings
        assertEquals(first.runs + 1, second.target)
    }

    @Test
    fun twentyOverMatchesReconcileWithTheLawsRules() {
        repeat(25) { seed -> assertReconciles(seed, play(seed, MatchRules.Standard)) }
    }

    @Test
    fun twentyOverMatchesReconcileWithLocalRules() {
        val rules = MatchRules.Gully.copy(widePenalty = 2, wideRebowled = false, noBallPenalty = 2)
        repeat(25) { seed -> assertReconciles(seed, play(100 + seed, rules)) }
    }
}
