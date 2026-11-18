package com.harsh.desicrics.scoring

import com.harsh.desicrics.model.Delivery
import com.harsh.desicrics.model.DismissalKind
import com.harsh.desicrics.model.ExtraType
import com.harsh.desicrics.model.Match
import com.harsh.desicrics.model.MatchEvent
import com.harsh.desicrics.model.MatchRules
import com.harsh.desicrics.model.Player
import com.harsh.desicrics.model.RetiredReturn
import com.harsh.desicrics.model.RuleMode
import com.harsh.desicrics.model.Team
import com.harsh.desicrics.model.TeamSide
import com.harsh.desicrics.model.TossDecision
import com.harsh.desicrics.model.Wicket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MatchRulesTest {

    private fun match(rules: MatchRules, battingPlayers: Int = 4, overs: Int = 5) = Match(
        id = "m1",
        createdBy = "u1",
        createdAt = 0,
        teamA = Team("Strikers", (1..battingPlayers).map { Player("a$it", "A$it") }),
        teamB = Team("Royals", (1..3).map { Player("b$it", "B$it") }),
        oversPerInnings = overs,
        tossWinner = TeamSide.A,
        tossDecision = TossDecision.BAT,
        rules = rules,
    )

    private var match = match(MatchRules())
    private val deliveries = mutableListOf<Delivery>()
    private val events = mutableListOf<MatchEvent>()
    private val nextSeq get() = deliveries.size + events.size

    private fun delivery(
        striker: String,
        nonStriker: String?,
        bowler: String = "b1",
        runs: Int = 0,
        extra: ExtraType? = null,
        extraRuns: Int = 0,
        wicket: Wicket? = null,
    ) = Delivery(
        nextSeq, 1, striker, nonStriker, bowler, runs, extra, extraRuns, wicket,
        extraPenalty = extra?.let { match.rules.penalty(it) } ?: 1,
        countsAsBall = extra != null && (extra == ExtraType.WIDE || extra == ExtraType.NO_BALL) && match.rules.countsAsBall(extra),
    )

    /** Records the ball after checking it's allowed, like the server does. */
    private fun ball(
        striker: String,
        nonStriker: String?,
        bowler: String = "b1",
        runs: Int = 0,
        extra: ExtraType? = null,
        extraRuns: Int = 0,
        wicket: Wicket? = null,
    ) {
        val d = delivery(striker, nonStriker, bowler, runs, extra, extraRuns, wicket)
        assertNull(state().validate(d, d.seq), "ball $d")
        deliveries += d
    }

    private fun retire(player: String, out: Boolean = false) {
        events += MatchEvent.Retire(nextSeq, 1, player, out)
    }

    private fun state() = ScoringEngine.compute(match, deliveries, events)

    private fun error(d: Delivery) = state().validate(d, d.seq)

    @Test
    fun withoutLastManStandsTheInningsEndsWithOneBatterLeft() {
        match = match(MatchRules(), battingPlayers = 3)
        ball("a1", "a2", wicket = Wicket(DismissalKind.BOWLED, "a1"))
        ball("a3", "a2", wicket = Wicket(DismissalKind.BOWLED, "a3"))
        assertTrue(state().innings.first().isComplete)
    }

    @Test
    fun lastManBatsAloneAndKeepsTheStrike() {
        match = match(MatchRules(lastManStands = true), battingPlayers = 3)
        ball("a1", "a2", wicket = Wicket(DismissalKind.BOWLED, "a1"))
        // Not alone while someone can still come in.
        assertEquals(DeliveryError.NON_STRIKER_MISSING, error(delivery("a2", null)))
        ball("a3", "a2", wicket = Wicket(DismissalKind.BOWLED, "a3"))
        assertFalse(state().current.isComplete)

        ball("a2", null, runs = 1)
        ball("a2", null, runs = 3)
        ball("a2", null)
        ball("a2", null)
        // Over ends: still on strike, still alone.
        val innings = state().current
        assertEquals("a2", innings.strikerId)
        assertNull(innings.nonStrikerId)
        assertEquals(4, innings.batters.first { it.player.id == "a2" }.runs)

        ball("a2", null, bowler = "b2", wicket = Wicket(DismissalKind.CAUGHT, "a2"))
        assertTrue(state().innings.first().isComplete)
        assertEquals(3, state().innings.first().allOutWickets)
    }

    @Test
    fun lastManStandsIsOffByDefault() {
        match = match(MatchRules(), battingPlayers = 3)
        assertEquals(DeliveryError.NON_STRIKER_MISSING, error(delivery("a1", null)))
    }

    @Test
    fun enforcedAfterWicketKeepsARetiredBatterOutUntilAWicketFalls() {
        match = match(MatchRules(retiredReturn = RetiredReturn.AFTER_WICKET, retiredReturnMode = RuleMode.ENFORCE))
        ball("a1", "a2")
        retire("a1")
        assertEquals(listOf("a3", "a4"), state().current.canComeIn.map { it.id })
        assertEquals(DeliveryError.RETIRED_CANNOT_RETURN, error(delivery("a1", "a2")))

        ball("a3", "a2", wicket = Wicket(DismissalKind.BOWLED, "a3"))
        assertEquals(listOf("a1", "a4"), state().current.canComeIn.map { it.id })
        ball("a1", "a2")
    }

    @Test
    fun warnedRuleLetsTheBallThroughButReportsIt() {
        match = match(MatchRules(retiredReturn = RetiredReturn.AFTER_WICKET, retiredReturnMode = RuleMode.WARN))
        ball("a1", "a2")
        retire("a1")
        assertTrue(state().current.canComeIn.any { it.id == "a1" })
        val back = delivery("a1", "a2")
        assertNull(error(back))
        assertEquals(listOf(DeliveryError.RETIRED_CANNOT_RETURN), state().warnings(back))
    }

    @Test
    fun atEndLetsRetiredBattersBackOnceEveryoneElseHasBatted() {
        match = match(MatchRules(retiredReturn = RetiredReturn.AT_END, retiredReturnMode = RuleMode.ENFORCE), battingPlayers = 3)
        ball("a1", "a2")
        retire("a1")
        assertEquals(listOf("a3"), state().current.canComeIn.map { it.id })
        ball("a3", "a2")
        assertEquals(listOf("a1"), state().current.canComeIn.map { it.id })
    }

    @Test
    fun inningsEndsWhenTheRulesKeepTheOnlyBattersLeftOut() {
        match = match(MatchRules(retiredReturn = RetiredReturn.NEVER, retiredReturnMode = RuleMode.ENFORCE), battingPlayers = 2)
        ball("a1", "a2")
        retire("a1")
        assertTrue(state().innings.first().isComplete)
    }

    @Test
    fun batterOnTheRetirementScoreMustRetireThenCanComeBack() {
        match = match(
            MatchRules(
                retireAtRuns = 10, retireAtMode = RuleMode.ENFORCE,
                retiredReturn = RetiredReturn.AT_END, retiredReturnMode = RuleMode.ENFORCE,
            ),
            battingPlayers = 3,
        )
        ball("a1", "a2", runs = 6)
        ball("a1", "a2", runs = 4)
        assertEquals(DeliveryError.MUST_RETIRE, error(delivery("a1", "a2")))

        retire("a1")
        assertEquals(Retirement.NOT_OUT, state().current.batters.first { it.player.id == "a1" }.retired)
        ball("a3", "a2", wicket = Wicket(DismissalKind.BOWLED, "a3"))
        // Everyone else has batted, so a1 returns and isn't asked to retire again.
        ball("a1", "a2", runs = 2)
        assertEquals(12, state().current.batters.first { it.player.id == "a1" }.runs)
    }

    @Test
    fun evenShareOverLimit() {
        assertEquals(1, MatchRules().overLimit(5))
        assertEquals(4, MatchRules().overLimit(20))
        assertEquals(2, MatchRules().overLimit(6))
        assertEquals(3, MatchRules(bowlerOverLimit = 3).overLimit(20))
    }

    @Test
    fun bowlerCantBowlPastTheirLimit() {
        match = match(MatchRules(bowlerOverLimit = 1, bowlerOverLimitMode = RuleMode.ENFORCE))
        repeat(6) { ball("a1", "a2") }
        ball("a2", "a1", bowler = "b2")
        assertEquals(RuleBreak(DeliveryError.BOWLER_OVER_LIMIT, RuleMode.ENFORCE), state().bowlerRuleBreak("b1"))
        repeat(5) { ball("a2", "a1", bowler = "b2") }
        assertEquals(DeliveryError.BOWLER_OVER_LIMIT, error(delivery("a1", "a2", bowler = "b1")))
    }

    @Test
    fun consecutiveOversAreAllowedByDefault() {
        repeat(6) { ball("a1", "a2") }
        ball("a2", "a1", bowler = "b1")
    }

    @Test
    fun consecutiveOversCanBeEnforced() {
        match = match(MatchRules(consecutiveOversMode = RuleMode.ENFORCE))
        repeat(6) { ball("a1", "a2") }
        assertEquals(DeliveryError.CONSECUTIVE_OVERS, error(delivery("a2", "a1", bowler = "b1")))
        ball("a2", "a1", bowler = "b2")
    }

    @Test
    fun wideThatIsNotRebowledCountsAsABallAndUsesTheMatchPenalty() {
        match = match(MatchRules(widePenalty = 2, wideRebowled = false))
        // Penalty 2, plus one run taken.
        ball("a1", "a2", extra = ExtraType.WIDE, extraRuns = 3)
        val innings = state().current
        assertEquals(3, innings.runs)
        assertEquals(1, innings.legalBalls)
        assertEquals("a2", innings.strikerId)
        assertEquals(3, innings.extras.wides)
    }

    @Test
    fun extrasMustMatchTheRules() {
        match = match(MatchRules(noBallPenalty = 2))
        val lawsNoBall = Delivery(0, 1, "a1", "a2", "b1", extra = ExtraType.NO_BALL, extraRuns = 1)
        assertEquals(DeliveryError.EXTRA_NOT_PER_RULES, error(lawsNoBall))
        ball("a1", "a2", runs = 1, extra = ExtraType.NO_BALL, extraRuns = 2)
        assertEquals(3, state().current.runs)
        assertEquals("a2", state().current.strikerId)
    }

    @Test
    fun freeHitFollowsANoBallAndCarriesOverAWide() {
        match = match(MatchRules(freeHitMode = RuleMode.ENFORCE))
        ball("a1", "a2", extra = ExtraType.NO_BALL, extraRuns = 1)
        assertTrue(state().current.isFreeHit)
        assertEquals(DeliveryError.NOT_OUT_ON_FREE_HIT, error(delivery("a1", "a2", wicket = Wicket(DismissalKind.BOWLED, "a1"))))

        ball("a1", "a2", extra = ExtraType.WIDE, extraRuns = 1)
        assertTrue(state().current.isFreeHit)
        // Run outs still count on a free hit.
        ball("a1", "a2", wicket = Wicket(DismissalKind.RUN_OUT, "a2"))
        assertFalse(state().current.isFreeHit)
    }

    @Test
    fun noFreeHitWhenTheRuleIsOff() {
        ball("a1", "a2", extra = ExtraType.NO_BALL, extraRuns = 1)
        assertFalse(state().current.isFreeHit)
        ball("a1", "a2", wicket = Wicket(DismissalKind.BOWLED, "a1"))
    }

    @Test
    fun presetsAreValid() {
        listOf(MatchRules(), MatchRules.Standard, MatchRules.Club, MatchRules.Gully).forEach { assertTrue(it.isValid()) }
        assertFalse(MatchRules(widePenalty = 3).isValid())
    }
}
