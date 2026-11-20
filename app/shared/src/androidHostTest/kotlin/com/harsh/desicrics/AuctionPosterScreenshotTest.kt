package com.harsh.desicrics

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
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
import com.harsh.desicrics.model.AuctionPlayer
import com.harsh.desicrics.model.AuctionSale
import com.harsh.desicrics.model.AuctionSettings
import com.harsh.desicrics.model.AuctionTeam
import com.harsh.desicrics.model.PlayerRole
import com.harsh.desicrics.model.PlayerSummary
import com.harsh.desicrics.ui.auction.AuctionPoster
import com.harsh.desicrics.ui.share.ShareContent
import com.harsh.desicrics.ui.share.ShareFormat
import com.harsh.desicrics.ui.share.ShareLook
import com.harsh.desicrics.ui.share.TemplatePreview
import com.harsh.desicrics.ui.share.auctionShareContents
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

/**
 * The auction posters ("Sold!" and "Team after auction") in every template and format, drawn at their
 * design size and saved to app/shared/screenshots/share/ like the other posters.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class AuctionPosterScreenshotTest {

    @Before
    fun setUp() {
        System.setProperty("roborazzi.test.record", "true")
        startKoin { modules(module { single { ApiConfig("http://localhost") } }) }
    }

    @After
    fun tearDown() = stopKoin()

    private fun shoot(name: String, content: @Composable () -> Unit) =
        captureRoboImage("screenshots/share/$name.png") { DesiTheme(darkTheme = false, reduceMotion = true) { content() } }

    private fun shootTemplates(name: String, content: ShareContent) = ShareFormat.entries.forEach { format ->
        content.templates.filter { format in it.formats }.forEach { template ->
            val pages = template.pages(format)
            repeat(pages) { page ->
                val suffix = if (pages > 1) "_p${page + 1}" else ""
                shoot("${name}_${template.key}_${format.name.lowercase()}$suffix") {
                    Box(Modifier.width(ShareFormat.DESIGN_WIDTH)) { TemplatePreview(template, format, ShareLook.Default, page = page) }
                }
            }
        }
    }

    private val teams = listOf(AuctionTeam("t1", "Andheri Strikers"), AuctionTeam("t2", "Powai XI"))
    private val names = listOf(
        "Rohit Sawant", "Arjun Iyer", "Kabir Shaikh", "Dev Malhotra", "Ishaan Rao", "Farhan Qureshi", "Gaurav Patil", "Hari Nair",
        "Esha Menon", "Nikhil Joshi", "Manav Kulkarni", "Om Desai", "Pranav Shetty", "Rehan Khan", "Sahil Gupta", "Tanmay Bhide",
    )
    private val categories = listOf("Marquee", "Batter", "Bowler", "All-rounder", "Wicketkeeper")
    private val players = names.mapIndexed { i, name ->
        AuctionPlayer(
            PlayerSummary("p$i", name, "Mumbai", PlayerRole.entries[i % 3]),
            basePrice = if (i < 2) 50 else 10,
            category = categories[if (i < 2) 0 else 1 + i % 4],
            retainedBy = if (i == 1) "t1" else null,
            retainedPrice = if (i == 1) 120 else null,
            addedAt = i.toLong(),
        )
    }

    /** Strikers keep Arjun and buy most of the pool; Powai buy Rohit for 70. */
    private val finished: AuctionDetails = run {
        val settings = AuctionSettings(purseLabel = "₹ lakh", purse = 1000, maxSquad = 18, minSquad = 11)
        var seq = 0
        val order = players.map { it.profileId }.filter { it != "p1" }
        val events = mutableListOf<AuctionEvent>(AuctionEvent.Start(seq++, listOf("t1", "t2"), order))
        order.forEachIndexed { i, id ->
            val team = if (id == "p0") "t2" else "t1"
            val price = if (id == "p0") 70 else 10 + (i * 5) % 35
            events += AuctionEvent.NextPlayer(seq++, id)
            events += AuctionEvent.Bid(seq++, team, price)
            events += AuctionEvent.Sold(seq++, id, team, price)
        }
        events += AuctionEvent.Finish(seq)
        val state = AuctionEngine.compute(AuctionSetup(settings, listOf("t1", "t2"), players), events)
        AuctionDetails(Auction("a1", "x", "Andheri Premier League", settings, "me"), teams, listOf("me"), players, state, events.last())
    }

    @Test
    fun sold() {
        val sale = finished.state.sales.first { it.profileId == "p0" }
        assertEquals(AuctionSale("p0", "t2", 70), sale)
        shootTemplates("auction_sold", auctionShareContents(AuctionPoster.Sold(finished, sale)).single())
    }

    @Test
    fun teamAfterAuction() {
        val content = auctionShareContents(AuctionPoster.TeamAfterAuction(finished, "t1")).single()
        // Fifteen players: two story pages, two post pages.
        assertEquals(2, content.templates.single().pages(ShareFormat.STORY))
        shootTemplates("auction_team", content)
    }
}
