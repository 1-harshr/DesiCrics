package com.harsh.desicrics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.harsh.desicrics.auction.AuctionEngine
import com.harsh.desicrics.auction.AuctionSetup
import com.harsh.desicrics.data.api.ApiConfig
import com.harsh.desicrics.designsystem.theme.DesiTheme
import com.harsh.desicrics.model.Auction
import com.harsh.desicrics.model.AuctionDetails
import com.harsh.desicrics.model.AuctionEvent
import com.harsh.desicrics.model.AuctionMode
import com.harsh.desicrics.model.AuctionPlayer
import com.harsh.desicrics.model.AuctionSettings
import com.harsh.desicrics.model.AuctionTeam
import com.harsh.desicrics.model.PlayerRole
import com.harsh.desicrics.model.PlayerSummary
import com.harsh.desicrics.ui.auction.AuctionActions
import com.harsh.desicrics.ui.auction.AuctionCard
import com.harsh.desicrics.ui.auction.AuctionContent
import com.harsh.desicrics.ui.auction.AuctionUiState
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The player auction drawn with sample data and saved to app/shared/screenshots/, like [ScreenshotTest]:
 * setup, the hall's big screen (landscape), an owner's online room, the sold moment and the results.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class AuctionScreenshotTest {

    @Before
    fun setUp() {
        System.setProperty("roborazzi.test.record", "true")
        // Avatars look up the server address to load photos; none of these have one.
        startKoin { modules(module { single { ApiConfig("http://localhost") } }) }
    }

    @After
    fun tearDown() = stopKoin()

    private fun shoot(name: String, dark: Boolean = false, content: @Composable () -> Unit) =
        captureRoboImage("screenshots/$name.png") { DesiTheme(darkTheme = dark, reduceMotion = true) { content() } }

    private val noActions = object : AuctionActions {}

    private val teams = listOf(
        AuctionTeam("t1", "Andheri Strikers", ownerIds = listOf("o1")),
        AuctionTeam("t2", "Powai XI", ownerIds = listOf("o2")),
        AuctionTeam("t3", "Bandra Blasters", ownerIds = listOf("o3")),
        AuctionTeam("t4", "Juhu Kings", ownerIds = listOf("o4")),
    )

    private val names = listOf(
        Triple("Rohit Sawant", "Marquee", PlayerRole.BATTER),
        Triple("Arjun Iyer", "Marquee", PlayerRole.ALL_ROUNDER),
        Triple("Kabir Shaikh", "Batter", PlayerRole.BATTER),
        Triple("Dev Malhotra", "Batter", PlayerRole.BATTER),
        Triple("Ishaan Rao", "Bowler", PlayerRole.BOWLER),
        Triple("Farhan Qureshi", "Bowler", PlayerRole.BOWLER),
        Triple("Gaurav Patil", "All-rounder", PlayerRole.ALL_ROUNDER),
        Triple("Hari Nair", "Wicketkeeper", PlayerRole.WICKET_KEEPER),
        Triple("Esha Menon", "Batter", PlayerRole.BATTER),
        Triple("Nikhil Joshi", "Bowler", PlayerRole.BOWLER),
    )

    private fun pool(retained: Boolean = true): List<AuctionPlayer> = names.mapIndexed { i, (name, category, role) ->
        AuctionPlayer(
            PlayerSummary("p$i", name, city = "Mumbai", role = role, isPlaceholder = i == 9),
            basePrice = if (category == "Marquee") 50 else 10,
            category = category,
            retainedBy = if (retained && i == 1) "t1" else null,
            retainedPrice = if (retained && i == 1) 120 else null,
            addedAt = i.toLong(),
        )
    }

    private fun details(
        settings: AuctionSettings = AuctionSettings(purseLabel = "pts"),
        players: List<AuctionPlayer> = pool(),
        events: List<AuctionEvent> = emptyList(),
        lotEndsAt: Long? = null,
    ): AuctionDetails {
        val state = AuctionEngine.compute(AuctionSetup(settings, teams.map { it.teamId }, players), events)
        return AuctionDetails(
            auction = Auction("a1", "x", "Andheri Premier League", settings, createdBy = "me"),
            teams = teams,
            organiserIds = listOf("me"),
            players = players,
            state = state,
            latest = events.lastOrNull(),
            lotEndsAt = lotEndsAt,
            serverTime = 0,
        )
    }

    /** A few sales, then [current] up with bids from Powai and Bandra; or nobody up when [current] is null. */
    private fun liveEvents(current: String? = "p0", sold: Boolean = false): List<AuctionEvent> {
        val players = pool()
        val order = listOf("p0", "p2", "p3", "p4", "p5", "p6", "p7", "p8", "p9")
        var seq = 0
        val events = mutableListOf<AuctionEvent>(AuctionEvent.Start(seq++, teams.map { it.teamId }, order))
        fun sale(id: String, team: String, price: Int) {
            events += AuctionEvent.NextPlayer(seq++, id)
            events += AuctionEvent.Bid(seq++, team, price)
            events += AuctionEvent.Sold(seq++, id, team, price)
        }
        sale("p2", "t3", 35)
        sale("p4", "t2", 25)
        sale("p5", "t1", 40)
        events += AuctionEvent.NextPlayer(seq++, "p7")
        events += AuctionEvent.Unsold(seq++, "p7")
        if (current != null) {
            val base = players.first { it.profileId == current }.basePrice
            events += AuctionEvent.NextPlayer(seq++, current)
            events += AuctionEvent.Bid(seq++, "t2", base)
            events += AuctionEvent.Bid(seq++, "t3", base + 10)
            events += AuctionEvent.Bid(seq++, "t2", base + 20)
            if (sold) events += AuctionEvent.Sold(seq++, current, "t2", base + 20)
        }
        return events
    }

    @Test
    fun auctionCard() = shoot("auction_card") {
        Column(Modifier.background(DesiTheme.colors.bg).padding(DesiTheme.space.s4), verticalArrangement = Arrangement.spacedBy(DesiTheme.space.s4)) {
            AuctionCard(null, "me", isOrganiser = true, saving = false, onOpen = {}, onAsk = {})
            AuctionCard(details(), "p5", isOrganiser = false, saving = false, onOpen = {}, onAsk = {})
            AuctionCard(details(), "someone", isOrganiser = false, saving = false, onOpen = {}, onAsk = {})
        }
    }

    @Test
    fun auctionSetup() {
        val requests = listOf(
            AuctionPlayer(PlayerSummary("r1", "Manav Kulkarni", "Thane", PlayerRole.BOWLER), basePrice = 10, requested = true, addedAt = 20),
            AuctionPlayer(PlayerSummary("r2", "Om Desai", "Mumbai", PlayerRole.BATTER), basePrice = 10, requested = true, addedAt = 21),
        )
        val state = AuctionUiState(myId = "me", loading = false, details = details(players = pool() + requests))
        shoot("auction_setup") { AuctionContent(state, noActions) }
    }

    @Test
    fun auctionSetupWaiting() {
        val state = AuctionUiState(myId = "someone", loading = false, details = details(settings = AuctionSettings(mode = AuctionMode.ONLINE, purse = 100, purseLabel = "₹ lakh", basePrice = 2)))
        shoot("auction_setup_waiting", dark = true) { AuctionContent(state, noActions) }
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp-xxhdpi")
    fun auctionHall() {
        val state = AuctionUiState(myId = "me", loading = false, details = details(events = liveEvents()))
        shoot("auction_hall") { AuctionContent(state, noActions) }
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp-xxhdpi")
    fun auctionHallBetweenLots() {
        val state = AuctionUiState(myId = "me", loading = false, details = details(events = liveEvents(current = null)))
        shoot("auction_hall_between", dark = true) { AuctionContent(state, noActions) }
    }

    @Test
    fun auctionHallWatching() {
        val state = AuctionUiState(myId = "someone", loading = false, details = details(events = liveEvents()))
        shoot("auction_hall_watching") { AuctionContent(state, noActions) }
    }

    @Test
    fun auctionOnlineOwner() {
        val settings = AuctionSettings(mode = AuctionMode.ONLINE)
        val state = AuctionUiState(myId = "o3", loading = false, details = details(settings = settings, events = liveEvents()))
        shoot("auction_online_owner") { AuctionContent(state, noActions, millisLeft = 7_200) }
    }

    @Test
    fun auctionOnlineOrganiser() {
        val settings = AuctionSettings(mode = AuctionMode.ONLINE)
        val state = AuctionUiState(myId = "me", loading = false, details = details(settings = settings, events = liveEvents()))
        shoot("auction_online_organiser", dark = true) { AuctionContent(state, noActions, millisLeft = 3_100) }
    }

    @Test
    fun auctionSoldMoment() {
        val details = details(events = liveEvents(sold = true))
        val state = AuctionUiState(myId = "someone", loading = false, details = details, soldMoment = details.latest as AuctionEvent.Sold)
        shoot("auction_sold") { AuctionContent(state, noActions) }
    }

    @Test
    fun auctionSummary() {
        val events = liveEvents(sold = true) + AuctionEvent.Finish(100)
        val state = AuctionUiState(myId = "someone", loading = false, details = details(events = events))
        shoot("auction_summary") { AuctionContent(state, noActions) }
    }
}
