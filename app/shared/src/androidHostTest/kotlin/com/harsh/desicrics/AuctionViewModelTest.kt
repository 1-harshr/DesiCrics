package com.harsh.desicrics

import com.harsh.desicrics.api.AuctionPlayerRequest
import com.harsh.desicrics.api.BidRequest
import com.harsh.desicrics.auction.AuctionEngine
import com.harsh.desicrics.auction.AuctionSetup
import com.harsh.desicrics.data.Analytics
import com.harsh.desicrics.data.AuctionRepository
import com.harsh.desicrics.data.TeamRepository
import com.harsh.desicrics.data.api.ApiConfig
import com.harsh.desicrics.data.api.DesiCricsApi
import com.harsh.desicrics.model.Auction
import com.harsh.desicrics.model.AuctionDetails
import com.harsh.desicrics.model.AuctionEvent
import com.harsh.desicrics.model.AuctionMode
import com.harsh.desicrics.model.AuctionPlayer
import com.harsh.desicrics.model.AuctionSettings
import com.harsh.desicrics.model.AuctionTeam
import com.harsh.desicrics.model.PlayerSummary
import com.harsh.desicrics.ui.auction.AuctionUiState
import com.harsh.desicrics.ui.auction.AuctionViewModel
import androidx.lifecycle.viewModelScope
import io.ktor.client.HttpClient
import kotlinx.coroutines.cancel
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The auction room against a fake server: what it sends for each action, and how it takes in updates. */
@OptIn(ExperimentalCoroutinesApi::class)
class AuctionViewModelTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    /** Each view model keeps watching the room; stopped before Main goes away. */
    private val created = mutableListOf<AuctionViewModel>()

    @AfterTest
    fun tearDown() {
        created.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val teams = listOf(AuctionTeam("t1", "Strikers", ownerIds = listOf("o1")), AuctionTeam("t2", "Powai XI", ownerIds = listOf("o2")))
    private val players = (0..3).map { AuctionPlayer(PlayerSummary("p$it", "Player $it"), basePrice = 10, addedAt = it.toLong()) } +
        AuctionPlayer(PlayerSummary("r1", "Asker"), basePrice = 10, requested = true, addedAt = 9)

    private fun details(events: List<AuctionEvent>, mode: AuctionMode = AuctionMode.HALL): AuctionDetails {
        val settings = AuctionSettings(mode = mode)
        val state = AuctionEngine.compute(AuctionSetup(settings, teams.map { it.teamId }, players), events)
        return AuctionDetails(Auction("a1", "x", "League", settings, "me"), teams, listOf("me"), players, state, events.lastOrNull())
    }

    private val start = AuctionEvent.Start(0, listOf("t1", "t2"), listOf("p0", "p1", "p2", "p3"))
    private val upWithBid = listOf(start, AuctionEvent.NextPlayer(1, "p0"), AuctionEvent.Bid(2, "t1", 10))

    /** A view model whose server always answers [answer], with every request it got kept in [requests]. */
    private fun viewModel(answer: AuctionDetails, myId: String = "me", requests: MutableList<HttpRequestData> = mutableListOf()): AuctionViewModel {
        val http = HttpClient(MockEngine { request ->
            requests += request
            respond(json.encodeToString(AuctionDetails.serializer(), answer), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }) { install(ContentNegotiation) { json(json) } }
        val config = ApiConfig("http://test")
        val api = DesiCricsApi(http, { null }, config)
        return AuctionViewModel("a1", AuctionRepository(api), TeamRepository(api, config, Analytics.None), myId).also { created += it }
    }

    private suspend fun AuctionViewModel.awaitState(check: (AuctionUiState) -> Boolean) =
        withContext(Dispatchers.Default) { withTimeout(5_000) { state.first(check) } }

    private fun HttpRequestData.text() = (body as TextContent).text

    @Test
    fun aSaleOnScreenWhenTheRoomOpensDoesNotPlayAgain() = runTest {
        val sold = details(upWithBid + AuctionEvent.Sold(3, "p0", "t1", 10))
        val vm = viewModel(sold)
        vm.awaitState { it.details != null }
        assertNull(vm.state.value.soldMoment)
    }

    @Test
    fun aNewSalePlaysTheSoldMomentAndAnOlderCopyIsIgnored() = runTest {
        val live = details(upWithBid)
        val vm = viewModel(live)
        vm.awaitState { it.details != null }
        val sold = details(upWithBid + AuctionEvent.Sold(3, "p0", "t1", 10))
        vm.receive(sold)
        assertEquals("p0", vm.state.value.soldMoment?.profileId)
        // A feed message from before the sale arrives late: the room stays on the sale.
        vm.receive(live)
        assertEquals(3, vm.state.value.details?.state?.lastSeq)
        vm.dismissSoldMoment()
        assertNull(vm.state.value.soldMoment)
    }

    @Test
    fun theHallTapsATeamWithoutAnAmount() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val vm = viewModel(details(upWithBid), requests = requests)
        vm.awaitState { it.details != null }
        vm.bid("t2")
        vm.awaitState { !it.saving }
        val bid = requests.last { it.url.encodedPath == "/auctions/a1/bids" }
        assertEquals(BidRequest("t2"), json.decodeFromString(BidRequest.serializer(), bid.text()))
    }

    @Test
    fun anOwnerBidsTheAmountTheySaw() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val vm = viewModel(details(upWithBid, AuctionMode.ONLINE), myId = "o2", requests = requests)
        vm.awaitState { it.details != null }
        assertEquals(listOf("t2"), vm.state.value.myTeams.map { it.teamId })
        vm.bid("t2", 15)
        vm.awaitState { !it.saving }
        val bid = requests.last { it.url.encodedPath == "/auctions/a1/bids" }
        assertEquals(BidRequest("t2", 15), json.decodeFromString(BidRequest.serializer(), bid.text()))
    }

    @Test
    fun aBidTheRulesRefuseIsNeverSent() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val vm = viewModel(details(upWithBid), requests = requests)
        vm.awaitState { it.details != null }
        // Strikers already lead.
        assertNotNull(vm.state.value.bidError("t1", 15))
        vm.bid("t1")
        assertTrue(requests.none { it.url.encodedPath == "/auctions/a1/bids" })
    }

    @Test
    fun acceptingARequestKeepsTheirPrice() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val vm = viewModel(details(emptyList()), requests = requests)
        vm.awaitState { it.details != null }
        assertNull(vm.state.value.startError)
        vm.accept("r1")
        vm.awaitState { !it.saving }
        val put = requests.last { it.method == HttpMethod.Put }
        assertEquals("/auctions/a1/players/r1", put.url.encodedPath)
        assertEquals(AuctionPlayerRequest(10), json.decodeFromString(AuctionPlayerRequest.serializer(), put.text()))
    }
}
