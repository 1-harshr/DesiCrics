package com.harsh.desicrics.auction

import com.harsh.desicrics.model.AuctionBid
import com.harsh.desicrics.model.AuctionError
import com.harsh.desicrics.model.AuctionEvent
import com.harsh.desicrics.model.AuctionEvent.Bid
import com.harsh.desicrics.model.AuctionEvent.Finish
import com.harsh.desicrics.model.AuctionEvent.NextPlayer
import com.harsh.desicrics.model.AuctionEvent.Pause
import com.harsh.desicrics.model.AuctionEvent.Resume
import com.harsh.desicrics.model.AuctionEvent.ReverseSale
import com.harsh.desicrics.model.AuctionEvent.Sold
import com.harsh.desicrics.model.AuctionEvent.Start
import com.harsh.desicrics.model.AuctionEvent.Undo
import com.harsh.desicrics.model.AuctionEvent.Unsold
import com.harsh.desicrics.model.AuctionOrder
import com.harsh.desicrics.model.AuctionPlayer
import com.harsh.desicrics.model.AuctionPlayerStatus
import com.harsh.desicrics.model.AuctionSale
import com.harsh.desicrics.model.AuctionSettings
import com.harsh.desicrics.model.AuctionState
import com.harsh.desicrics.model.AuctionStatus
import com.harsh.desicrics.model.BidStep
import com.harsh.desicrics.model.PlayerSummary
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuctionEngineTest {

    // Purse 100, base 10, squads of 2 to 3, +5 up to 30 then +10.
    private val settings = AuctionSettings(
        purse = 100, basePrice = 10, minSquad = 2, maxSquad = 3,
        bidSteps = listOf(BidStep(upTo = 30, step = 5), BidStep(step = 10)),
    )

    private fun player(id: String, category: String? = null, base: Int = 10, at: Long = 0, requested: Boolean = false, retainedBy: String? = null, retainedPrice: Int? = null) =
        AuctionPlayer(PlayerSummary(id, id.uppercase()), base, category, requested, retainedBy, retainedPrice, at)

    private val pool = listOf(player("p1", at = 1), player("p2", at = 2), player("p3", at = 3), player("p4", at = 4))
    private val setup = AuctionSetup(settings, listOf("a", "b"), pool)

    /** Plays [commands] in turn, each numbered after the last and checked first; fails on the first rejected one. */
    private fun play(setup: AuctionSetup = this.setup, vararg commands: (Int) -> AuctionEvent): Pair<List<AuctionEvent>, AuctionState> {
        val events = mutableListOf<AuctionEvent>()
        var state = AuctionEngine.compute(setup, events)
        for (command in commands) {
            val event = command(state.lastSeq + 1)
            assertNull(AuctionEngine.check(setup, state, event), "Rejected $event")
            events += event
            state = AuctionEngine.compute(setup, events)
        }
        return events to state
    }

    private fun start(order: List<String> = listOf("p1", "p2", "p3", "p4")) = { seq: Int -> Start(seq, listOf("a", "b"), order) }
    private fun next(id: String) = { seq: Int -> NextPlayer(seq, id) }
    private fun bid(team: String, amount: Int) = { seq: Int -> Bid(seq, team, amount) }
    private fun sold(id: String, team: String, price: Int) = { seq: Int -> Sold(seq, id, team, price) }
    private fun unsold(id: String) = { seq: Int -> Unsold(seq, id) }
    private val undo = { seq: Int -> Undo(seq) }

    private fun rejects(state: AuctionState, event: AuctionEvent, setup: AuctionSetup = this.setup) = AuctionEngine.check(setup, state, event)

    @Test
    fun beforeTheStartEveryTeamHasItsFullPurse() {
        val state = AuctionEngine.compute(setup, emptyList())
        assertEquals(AuctionStatus.SETUP, state.status)
        assertEquals(listOf(100, 100), state.teams.map { it.purseLeft })
        // Keeps 10 for the second player it still needs.
        assertEquals(90, state.teams[0].maxBid)
        assertEquals(2, state.teams[0].needed)
        assertNull(state.upNext)
        assertEquals(AuctionError.NOT_STARTED, rejects(state, NextPlayer(0, "p1")))
    }

    @Test
    fun aPlayerIsSoldToTheHighestBidder() {
        val (_, state) = play(setup, start(), next("p1"), bid("a", 10), bid("b", 15), bid("a", 20), sold("p1", "a", 20))
        assertEquals(AuctionStatus.LIVE, state.status)
        assertNull(state.lot)
        assertEquals(listOf(AuctionSale("p1", "a", 20)), state.sales)
        val a = state.team("a")!!
        assertEquals(80, a.purseLeft)
        assertEquals(20, a.spent)
        assertEquals(listOf("p1"), a.bought)
        assertEquals(1, a.needed)
        // One more needed, and this purchase would be it: nothing to keep aside.
        assertEquals(80, a.maxBid)
        assertEquals(AuctionPlayerStatus.SOLD, state.players["p1"])
        assertEquals("p2", state.upNext)
    }

    @Test
    fun bidsGoUpByTheStepForTheirRange() {
        val (_, opened) = play(setup, start(), next("p1"))
        assertEquals(10, opened.lot!!.nextBid)
        val (_, state) = play(setup, start(), next("p1"), bid("a", 10), bid("b", 15), bid("a", 25))
        // Below 30 the step is 5.
        assertEquals(30, state.lot!!.nextBid)
        val (_, higher) = play(setup, start(), next("p1"), bid("a", 30))
        // From 30 on it's 10.
        assertEquals(40, higher.lot!!.nextBid)
        assertEquals(AuctionError.BID_TOO_LOW, rejects(higher, Bid(higher.lastSeq + 1, "b", 35)))
        assertNull(rejects(higher, Bid(higher.lastSeq + 1, "b", 50)), "Jumping above the step is fine")
    }

    @Test
    fun theFirstBidIsAtLeastThePlayersBasePrice() {
        val pricey = setup.copy(players = listOf(player("p1", base = 25)))
        val (_, state) = play(pricey, { Start(it, listOf("a", "b"), listOf("p1")) }, next("p1"))
        assertEquals(25, state.lot!!.nextBid)
        assertEquals(AuctionError.BID_TOO_LOW, rejects(state, Bid(state.lastSeq + 1, "a", 20), pricey))
    }

    @Test
    fun aTeamKeepsEnoughToFillItsMinimumSquad() {
        val big = setup.copy(settings = settings.copy(minSquad = 3, maxSquad = 4))
        val (_, state) = play(big, start(), next("p1"))
        // 100 − (3 − 0 − 1) × 10.
        assertEquals(80, state.team("a")!!.maxBid)
        assertEquals(AuctionError.BID_TOO_HIGH, rejects(state, Bid(state.lastSeq + 1, "a", 85), big))
        assertNull(rejects(state, Bid(state.lastSeq + 1, "a", 80), big))
    }

    @Test
    fun aFullTeamCantBid() {
        val (_, state) = play(
            setup, start(),
            next("p1"), bid("a", 10), sold("p1", "a", 10),
            next("p2"), bid("a", 10), sold("p2", "a", 10),
            next("p3"), bid("a", 10), sold("p3", "a", 10),
            next("p4"),
        )
        val a = state.team("a")!!
        assertTrue(a.full)
        assertEquals(0, a.maxBid)
        assertEquals(AuctionError.TEAM_FULL, rejects(state, Bid(state.lastSeq + 1, "a", 10)))
    }

    @Test
    fun aTeamCantOutbidItselfOrBidForAnotherAuction() {
        val (_, state) = play(setup, start(), next("p1"), bid("a", 10))
        assertEquals(AuctionError.ALREADY_LEADING, rejects(state, Bid(state.lastSeq + 1, "a", 15)))
        assertEquals(AuctionError.TEAM_UNKNOWN, rejects(state, Bid(state.lastSeq + 1, "z", 15)))
    }

    @Test
    fun soldNeedsABidAndMustMatchTheHighest() {
        val (_, open) = play(setup, start(), next("p1"))
        assertEquals(AuctionError.NO_BIDS, rejects(open, Sold(open.lastSeq + 1, "p1", "a", 10)))
        val (_, bidding) = play(setup, start(), next("p1"), bid("a", 10), bid("b", 15))
        assertEquals(AuctionError.NOT_HIGHEST_BID, rejects(bidding, Sold(bidding.lastSeq + 1, "p1", "a", 10)))
        assertEquals(AuctionError.WRONG_PLAYER, rejects(bidding, Sold(bidding.lastSeq + 1, "p2", "b", 15)))
        assertEquals(AuctionError.LOT_OPEN, rejects(bidding, NextPlayer(bidding.lastSeq + 1, "p2")))
        assertEquals(AuctionError.LOT_OPEN, rejects(bidding, Finish(bidding.lastSeq + 1)))
    }

    @Test
    fun undoTakesBackTheLastBidThenTheSale() {
        val (events, sold) = play(setup, start(), next("p1"), bid("a", 10), bid("b", 15), sold("p1", "b", 15))
        assertTrue(sold.canUndo)
        // Undo the sale: the lot is open again with both bids.
        val reopened = AuctionEngine.compute(setup, events + Undo(sold.lastSeq + 1))
        assertEquals(listOf("a", "b"), reopened.lot!!.bids.map { it.teamId })
        assertEquals(100, reopened.team("b")!!.purseLeft)
        assertEquals(AuctionPlayerStatus.UP, reopened.players["p1"])
        // And again: b's bid is gone.
        val twice = AuctionEngine.compute(setup, events + Undo(sold.lastSeq + 1) + Undo(sold.lastSeq + 2))
        assertEquals(AuctionBid("a", 10), twice.lot!!.leading)
        assertEquals(15, twice.lot!!.nextBid)
    }

    @Test
    fun undoSkipsPausesAndStopsAtTheStart() {
        val (_, state) = play(setup, start(), next("p1"), { Pause(it) }, undo)
        // The NextPlayer went, the pause stayed.
        assertNull(state.lot)
        assertEquals(AuctionStatus.PAUSED, state.status)
        assertEquals(AuctionPlayerStatus.WAITING, state.players["p1"])
        assertFalse(state.canUndo)
        assertEquals(AuctionError.NOTHING_TO_UNDO, rejects(state, Undo(state.lastSeq + 1)))
    }

    @Test
    fun nothingHappensWhilePaused() {
        val (_, paused) = play(setup, start(), next("p1"), bid("a", 10), { Pause(it) })
        assertEquals(AuctionStatus.PAUSED, paused.status)
        assertEquals(AuctionError.PAUSED, rejects(paused, Bid(paused.lastSeq + 1, "b", 15)))
        assertEquals(AuctionError.PAUSED, rejects(paused, Sold(paused.lastSeq + 1, "p1", "a", 10)))
        assertEquals(AuctionError.PAUSED, rejects(paused, Pause(paused.lastSeq + 1)))
        val (_, resumed) = play(setup, start(), next("p1"), bid("a", 10), { Pause(it) }, { Resume(it) }, bid("b", 15))
        assertEquals(AuctionStatus.LIVE, resumed.status)
        assertEquals(AuctionError.NOT_PAUSED, rejects(resumed, Resume(resumed.lastSeq + 1)))
    }

    @Test
    fun unsoldPlayersComeBackOnceInTheUnsoldRound() {
        val two = setup.copy(players = pool.take(2))
        val (_, state) = play(two, start(listOf("p1", "p2")), next("p1"), unsold("p1"), next("p2"), bid("a", 10), sold("p2", "a", 10))
        assertEquals("p1", state.upNext)
        assertEquals(2, state.round)
        val (_, again) = play(two, start(listOf("p1", "p2")), next("p1"), unsold("p1"), next("p2"), unsold("p2"), next("p1"))
        assertEquals(2, again.lot!!.round)
        val (_, done) = play(two, start(listOf("p1", "p2")), next("p1"), unsold("p1"), next("p2"), unsold("p2"), next("p1"), unsold("p1"), next("p2"), unsold("p2"))
        // Each has had their second chance.
        assertNull(done.upNext)
        // The organiser can still bring one back by hand.
        assertNull(rejects(done, NextPlayer(done.lastSeq + 1, "p1"), two))
    }

    @Test
    fun withoutAnUnsoldRoundNobodyComesBack() {
        val noRound = AuctionSetup(settings.copy(unsoldRound = false), listOf("a", "b"), pool.take(1))
        val (_, state) = play(noRound, start(listOf("p1")), next("p1"), unsold("p1"))
        assertNull(state.upNext)
    }

    @Test
    fun aSoldPlayerCantComeUpAgain() {
        val (_, state) = play(setup, start(), next("p1"), bid("a", 10), sold("p1", "a", 10))
        assertEquals(AuctionError.NOT_AVAILABLE, rejects(state, NextPlayer(state.lastSeq + 1, "p1")))
        assertEquals(AuctionError.NOT_AVAILABLE, rejects(state, NextPlayer(state.lastSeq + 1, "nobody")))
    }

    @Test
    fun reversingASaleRefundsTheTeamAndPutsThePlayerBack() {
        val (_, state) = play(setup, start(), next("p1"), bid("a", 10), bid("b", 40), sold("p1", "b", 40), next("p2"), { ReverseSale(it, "p1") })
        assertEquals(100, state.team("b")!!.purseLeft)
        assertTrue(state.sales.isEmpty())
        assertEquals(AuctionPlayerStatus.UNSOLD, state.players["p1"])
        // The lot that was open stays open.
        assertEquals("p2", state.lot!!.profileId)
        assertEquals(AuctionError.NOT_SOLD, rejects(state, ReverseSale(state.lastSeq + 1, "p1")))
    }

    @Test
    fun retainedPlayersComeOffThePurseAndDontComeUp() {
        val kept = setup.copy(players = pool + player("r1", retainedBy = "a", retainedPrice = 30))
        val state = AuctionEngine.compute(kept, emptyList())
        val a = state.team("a")!!
        assertEquals(70, a.purseLeft)
        assertEquals(listOf("r1"), a.retained)
        assertEquals(1, a.needed)
        assertEquals(AuctionPlayerStatus.RETAINED, state.players["r1"])
        assertFalse("r1" in AuctionEngine.lotOrder(kept, Random(1)))
        val (_, started) = play(kept, start())
        assertFalse("r1" in started.order)
    }

    @Test
    fun retainedPlayersMustFitThePurseAndSquad() {
        val tooDear = setup.copy(players = pool + player("r1", retainedBy = "a", retainedPrice = 101))
        assertEquals(AuctionError.RETAINED_INVALID, AuctionEngine.retainedError(tooDear))
        val tooMany = setup.copy(players = pool + (1..4).map { player("r$it", retainedBy = "a", retainedPrice = 1) })
        assertEquals(AuctionError.RETAINED_INVALID, AuctionEngine.retainedError(tooMany))
        assertEquals(
            AuctionError.RETAINED_INVALID,
            AuctionEngine.check(tooMany, AuctionEngine.compute(tooMany, emptyList()), Start(0, listOf("a", "b"), listOf("p1"))),
        )
    }

    @Test
    fun requestedPlayersWaitForTheOrganiser() {
        val asked = setup.copy(players = pool + player("q1", requested = true))
        assertFalse("q1" in AuctionEngine.lotOrder(asked, Random(1)))
        val (_, state) = play(asked, start())
        assertNull(state.players["q1"])
        assertEquals(AuctionError.NOT_AVAILABLE, rejects(state, NextPlayer(state.lastSeq + 1, "q1"), asked))
    }

    @Test
    fun playersAddedAfterTheStartComeUpLast() {
        val (events, _) = play(setup, start(listOf("p1", "p2")))
        val later = setup.copy(players = pool + player("late", at = 99))
        val state = AuctionEngine.compute(later, events)
        assertEquals(listOf("p1", "p2", "p3", "p4", "late"), state.order)
    }

    @Test
    fun categoryOrderGroupsPlayersAndShufflesWithin() {
        val mixed = AuctionSetup(
            settings.copy(order = AuctionOrder.CATEGORY, categories = listOf("Marquee", "Bowler")),
            listOf("a", "b"),
            listOf(player("b1", "Bowler"), player("x1"), player("m1", "marquee"), player("b2", "Bowler"), player("m2", "Marquee"), player("o1", "Other")),
        )
        repeat(5) { seed ->
            val order = AuctionEngine.lotOrder(mixed, Random(seed))
            assertEquals(setOf("m1", "m2"), order.take(2).toSet())
            assertEquals(setOf("b1", "b2"), order.subList(2, 4).toSet())
            assertEquals(setOf("x1", "o1"), order.drop(4).toSet())
        }
        val random = mixed.copy(settings = mixed.settings.copy(order = AuctionOrder.RANDOM))
        assertEquals(6, AuctionEngine.lotOrder(random, Random(3)).toSet().size)
    }

    @Test
    fun startNeedsTwoTeamsAndPlayers() {
        val state = AuctionEngine.compute(setup, emptyList())
        assertEquals(AuctionError.TOO_FEW_TEAMS, rejects(state, Start(0, listOf("a"), listOf("p1"))))
        assertEquals(AuctionError.NO_PLAYERS, rejects(state, Start(0, listOf("a", "b"), emptyList())))
        val (_, started) = play(setup, start())
        assertEquals(AuctionError.ALREADY_STARTED, rejects(started, Start(started.lastSeq + 1, listOf("a", "b"), listOf("p1"))))
    }

    @Test
    fun theStartFixesTheTeams() {
        val (events, _) = play(setup, start())
        // A third team confirmed in the tournament later isn't in this auction.
        val state = AuctionEngine.compute(setup.copy(teamIds = listOf("a", "b", "c")), events)
        assertEquals(listOf("a", "b"), state.teams.map { it.teamId })
    }

    @Test
    fun finishingEndsEverything() {
        val (_, state) = play(setup, start(), next("p1"), bid("a", 10), sold("p1", "a", 10), { Finish(it) })
        assertEquals(AuctionStatus.FINISHED, state.status)
        assertNull(state.upNext)
        assertFalse(state.canUndo)
        assertEquals(AuctionError.FINISHED, rejects(state, NextPlayer(state.lastSeq + 1, "p2")))
        assertEquals(AuctionError.FINISHED, rejects(state, Undo(state.lastSeq + 1)))
    }

    @Test
    fun aWholeAuctionAddsUp() {
        val (_, state) = play(
            setup, start(),
            next("p1"), bid("a", 10), bid("b", 15), bid("a", 30), bid("b", 40), sold("p1", "b", 40),
            next("p2"), unsold("p2"),
            next("p3"), bid("a", 10), sold("p3", "a", 10),
            next("p4"), bid("b", 10), bid("a", 15), bid("b", 20), undo, sold("p4", "a", 15),
            next("p2"), bid("b", 10), sold("p2", "b", 10),
            { Finish(it) },
        )
        assertEquals(listOf(25, 50), state.teams.map { it.spent })
        assertEquals(listOf(75, 50), state.teams.map { it.purseLeft })
        assertEquals(listOf(listOf("p3", "p4"), listOf("p1", "p2")), state.teams.map { it.bought })
        assertEquals(listOf(0, 0), state.teams.map { it.needed })
    }

    @Test
    fun settingsAreValidated() {
        assertNull(AuctionSettings().validate())
        assertEquals(AuctionError.PURSE_INVALID, AuctionSettings(purse = 0).validate())
        assertEquals(AuctionError.LABEL_INVALID, AuctionSettings(purseLabel = " ").validate())
        assertEquals(AuctionError.BASE_PRICE_INVALID, AuctionSettings(purse = 100, basePrice = 101, minSquad = 0).validate())
        assertEquals(AuctionError.SQUAD_INVALID, AuctionSettings(minSquad = 16, maxSquad = 15).validate())
        assertEquals(AuctionError.PURSE_TOO_SMALL, AuctionSettings(purse = 100, basePrice = 10, minSquad = 11).validate())
        assertEquals(AuctionError.STEPS_INVALID, AuctionSettings(bidSteps = listOf(BidStep(upTo = 50, step = 5))).validate())
        assertEquals(AuctionError.STEPS_INVALID, AuctionSettings(bidSteps = listOf(BidStep(50, 5), BidStep(40, 5), BidStep(step = 10))).validate())
        assertEquals(AuctionError.CATEGORIES_INVALID, AuctionSettings(categories = listOf("Bat", "bat")).validate())
        assertEquals(AuctionError.TIMER_INVALID, AuctionSettings(lotSeconds = 2).validate())
        assertNotNull(AuctionSettings(bidSteps = emptyList()).validate())
    }
}
