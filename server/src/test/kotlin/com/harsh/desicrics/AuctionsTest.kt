package com.harsh.desicrics

import com.harsh.desicrics.api.AddAuctionPlayerRequest
import com.harsh.desicrics.api.ApiError
import com.harsh.desicrics.api.AuctionPlayerRequest
import com.harsh.desicrics.api.BidRequest
import com.harsh.desicrics.api.CreateAuctionRequest
import com.harsh.desicrics.api.CreateTournamentRequest
import com.harsh.desicrics.api.EnterTeamRequest
import com.harsh.desicrics.api.NextPlayerRequest
import com.harsh.desicrics.api.ReverseSaleRequest
import com.harsh.desicrics.api.TeamRequest
import com.harsh.desicrics.model.AuctionDetails
import com.harsh.desicrics.model.AuctionEvent
import com.harsh.desicrics.model.AuctionMode
import com.harsh.desicrics.model.AuctionPlayerStatus
import com.harsh.desicrics.model.AuctionSale
import com.harsh.desicrics.model.AuctionSettings
import com.harsh.desicrics.model.AuctionStatus
import com.harsh.desicrics.model.SavedTeam
import com.harsh.desicrics.model.TournamentDetails
import com.harsh.desicrics.model.TournamentFormat
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.websocket.receiveDeserialized
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.delay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuctionsTest {

    /** Purse 100, base 10, squads of 1 to 3. */
    private val small = AuctionSettings(purse = 100, purseLabel = "pts", basePrice = 10, minSquad = 1, maxSquad = 3)

    private class Room(val tournamentId: String, val teamA: SavedTeam, val teamB: SavedTeam)

    /** Asha organises a tournament with Bilal's and Chetan's teams confirmed; Dev and Esha are on the app. */
    private suspend fun HttpClient.room(): Room {
        for (uid in listOf("asha", "bilal", "chetan", "dev", "esha")) setUpProfile(uid, name = uid.replaceFirstChar { it.uppercase() })
        val teamA = post("/teams") { bearerAuth("test-bilal"); jsonBody(TeamRequest("Bilal XI")) }.body<SavedTeam>()
        val teamB = post("/teams") { bearerAuth("test-chetan"); jsonBody(TeamRequest("Chetan XI")) }.body<SavedTeam>()
        val cup = post("/tournaments") {
            bearerAuth("test-asha")
            jsonBody(CreateTournamentRequest("Auction Cup", format = TournamentFormat.ROUND_ROBIN, oversPerInnings = 5))
        }.body<TournamentDetails>()
        for (team in listOf(teamA, teamB)) {
            post("/tournaments/${cup.tournament.id}/teams") { bearerAuth("test-asha"); jsonBody(EnterTeamRequest(team.id)) }
        }
        return Room(cup.tournament.id, teamA, teamB)
    }

    private suspend fun HttpClient.createAuction(room: Room, settings: AuctionSettings = small): AuctionDetails {
        val response = post("/tournaments/${room.tournamentId}/auction") { bearerAuth("test-asha"); jsonBody(CreateAuctionRequest(settings)) }
        assertEquals(HttpStatusCode.Created, response.status)
        return response.body()
    }

    private suspend fun HttpClient.act(uid: String, auctionId: String, action: String, body: Any? = null): HttpResponse =
        post("/auctions/$auctionId/$action") { bearerAuth("test-$uid"); body?.let { jsonBody(it) } }

    private suspend fun HttpResponse.details(): AuctionDetails {
        assertEquals(HttpStatusCode.OK, status, runCatching { body<ApiError>().message }.getOrNull())
        return body()
    }

    private suspend fun HttpClient.addPlayer(auctionId: String, request: AddAuctionPlayerRequest) =
        post("/auctions/$auctionId/players") { bearerAuth("test-asha"); jsonBody(request) }

    @Test
    fun aHallAuctionRunsFromThePoolToTheSquads() = apiTest { client ->
        val room = client.room()
        val created = client.createAuction(room)
        val id = created.auction.id
        assertEquals("Auction Cup", created.auction.tournamentName)
        assertEquals(listOf(room.teamA.id, room.teamB.id), created.teams.map { it.teamId })
        assertEquals(listOf("bilal"), created.teams[0].ownerIds)
        assertEquals(listOf("asha"), created.organiserIds)
        assertEquals(HttpStatusCode.OK, client.get("/tournaments/${room.tournamentId}/auction") { bearerAuth("test-dev") }.status)

        // Organisers build the pool: a user, a placeholder, and Esha's own request once accepted.
        assertEquals(HttpStatusCode.Forbidden, client.post("/auctions/$id/players") { bearerAuth("test-bilal"); jsonBody(AddAuctionPlayerRequest(profileId = "dev")) }.status)
        client.addPlayer(id, AddAuctionPlayerRequest(profileId = "dev", basePrice = 20, category = "Batter")).details()
        assertEquals(HttpStatusCode.Conflict, client.addPlayer(id, AddAuctionPlayerRequest(profileId = "dev")).status)
        val withGuest = client.addPlayer(id, AddAuctionPlayerRequest(placeholderName = "Farhan", category = "Bowler")).details()
        val farhan = withGuest.players.single { it.player.name == "Farhan" }
        assertTrue(farhan.player.isPlaceholder)
        assertEquals(10, farhan.basePrice)
        val asked = client.act("esha", id, "requests").details()
        assertTrue(asked.player("esha")!!.requested)
        val accepted = client.put("/auctions/$id/players/esha") { bearerAuth("test-asha"); jsonBody(AuctionPlayerRequest(basePrice = 10)) }.details()
        assertFalse(accepted.player("esha")!!.requested)

        val started = client.act("asha", id, "start").details()
        assertEquals(AuctionStatus.LIVE, started.state.status)
        // Batters before bowlers, then players without a category.
        assertEquals("dev", started.state.order.first())
        assertEquals(farhan.profileId, started.state.order[1])
        assertEquals(HttpStatusCode.Conflict, client.act("asha", id, "start").status)

        val up = client.act("asha", id, "next", NextPlayerRequest()).details()
        assertEquals("dev", up.state.lot!!.profileId)
        // In the hall only the auctioneer bids; each tap goes up one step.
        assertEquals(HttpStatusCode.Forbidden, client.act("bilal", id, "bids", BidRequest(room.teamA.id)).status)
        assertEquals(20, client.act("asha", id, "bids", BidRequest(room.teamA.id)).details().state.lot!!.leading!!.amount)
        assertEquals(25, client.act("asha", id, "bids", BidRequest(room.teamB.id)).details().state.lot!!.leading!!.amount)
        val tooLow = client.act("asha", id, "bids", BidRequest(room.teamA.id, amount = 26))
        assertEquals(HttpStatusCode.Conflict, tooLow.status)
        assertEquals("Bid at least 30 pts", tooLow.body<ApiError>().message)
        val sold = client.act("asha", id, "sold").details()
        assertEquals(AuctionSale("dev", room.teamB.id, 25), sold.state.sales.single())
        assertTrue(sold.latest is AuctionEvent.Sold)

        // Undo takes back the sale, then the last bid.
        client.act("asha", id, "undo").details()
        val undone = client.act("asha", id, "undo").details()
        assertEquals(room.teamA.id, undone.state.lot!!.leading!!.teamId)
        client.act("asha", id, "sold").details()

        client.act("asha", id, "next").details()
        assertEquals(HttpStatusCode.Conflict, client.act("asha", id, "sold").status)
        client.act("asha", id, "unsold").details()
        client.act("asha", id, "next", NextPlayerRequest("esha")).details()
        client.act("asha", id, "bids", BidRequest(room.teamB.id)).details()
        val afterEsha = client.act("asha", id, "sold").details()
        assertEquals(farhan.profileId, afterEsha.state.upNext)
        assertEquals(2, afterEsha.state.round)
        // A sale can be reversed: the points go back.
        val reversed = client.act("asha", id, "reverse", ReverseSaleRequest("esha")).details()
        assertEquals(100, reversed.state.team(room.teamB.id)!!.purseLeft)
        assertEquals(AuctionPlayerStatus.UNSOLD, reversed.state.players["esha"])
        client.act("asha", id, "next", NextPlayerRequest("esha")).details()
        client.act("asha", id, "bids", BidRequest(room.teamB.id)).details()
        client.act("asha", id, "sold").details()

        val done = client.act("asha", id, "finish").details()
        assertEquals(AuctionStatus.FINISHED, done.state.status)
        val a = done.state.team(room.teamA.id)!!
        assertEquals(listOf("dev"), a.bought)
        assertEquals(20 to 80, a.spent to a.purseLeft)
        assertEquals(listOf("esha"), done.state.team(room.teamB.id)!!.bought)
        assertEquals(HttpStatusCode.Conflict, client.act("asha", id, "next").status)

        // Sold players are now members of their teams.
        val squad = client.get("/teams/${room.teamA.id}") { bearerAuth("test-bilal") }.body<SavedTeam>()
        assertNotNull(squad.members.firstOrNull { it.player.profileId == "dev" })
        val other = client.get("/teams/${room.teamB.id}") { bearerAuth("test-chetan") }.body<SavedTeam>()
        assertNotNull(other.members.firstOrNull { it.player.profileId == "esha" })
    }

    @Test
    fun setupIsForOrganisersAndChecked() = apiTest { client ->
        val room = client.room()
        val tooSmall = client.post("/tournaments/${room.tournamentId}/auction") {
            bearerAuth("test-asha"); jsonBody(CreateAuctionRequest(small.copy(purse = 5)))
        }
        assertEquals(HttpStatusCode.BadRequest, tooSmall.status)
        assertEquals(HttpStatusCode.Forbidden, client.post("/tournaments/${room.tournamentId}/auction") { bearerAuth("test-bilal") }.status)
        val id = client.createAuction(room).auction.id
        assertEquals(HttpStatusCode.Conflict, client.post("/tournaments/${room.tournamentId}/auction") { bearerAuth("test-asha") }.status)

        // Retained players come off the purse, and must fit in it.
        client.addPlayer(id, AddAuctionPlayerRequest(profileId = "dev")).details()
        val overspent = client.put("/auctions/$id/players/dev") {
            bearerAuth("test-asha"); jsonBody(AuctionPlayerRequest(basePrice = 10, retainedBy = room.teamA.id, retainedPrice = 101))
        }
        assertEquals(HttpStatusCode.BadRequest, overspent.status)
        val kept = client.put("/auctions/$id/players/dev") {
            bearerAuth("test-asha"); jsonBody(AuctionPlayerRequest(basePrice = 10, retainedBy = room.teamA.id, retainedPrice = 30))
        }.details()
        assertEquals(70, kept.state.team(room.teamA.id)!!.purseLeft)
        assertEquals(AuctionPlayerStatus.RETAINED, kept.state.players["dev"])

        // Settings change until the start.
        val online = client.put("/auctions/$id/settings") { bearerAuth("test-asha"); jsonBody(small.copy(mode = AuctionMode.ONLINE, purseLabel = " coins ")) }.details()
        assertEquals("coins", online.auction.settings.purseLabel)
        // Nobody to bid for yet.
        assertEquals(HttpStatusCode.Conflict, client.act("asha", id, "start").status)
        client.act("esha", id, "requests").details()
        assertEquals(HttpStatusCode.Conflict, client.act("asha", id, "start").status)
        client.delete("/auctions/$id/requests") { bearerAuth("test-esha") }.details()
        client.addPlayer(id, AddAuctionPlayerRequest(profileId = "esha")).details()
        client.act("asha", id, "start").details()
        assertEquals(HttpStatusCode.Conflict, client.put("/auctions/$id/settings") { bearerAuth("test-asha"); jsonBody(small) }.status)

        // An auction comes before its tournament starts.
        val cup = client.post("/tournaments") {
            bearerAuth("test-asha"); jsonBody(CreateTournamentRequest("Started Cup", format = TournamentFormat.KNOCKOUT, oversPerInnings = 5))
        }.body<TournamentDetails>().tournament.id
        for (team in listOf(room.teamA, room.teamB)) client.post("/tournaments/$cup/teams") { bearerAuth("test-asha"); jsonBody(EnterTeamRequest(team.id)) }
        assertEquals(HttpStatusCode.OK, client.post("/tournaments/$cup/start") { bearerAuth("test-asha") }.status)
        assertEquals(HttpStatusCode.Conflict, client.post("/tournaments/$cup/auction") { bearerAuth("test-asha") }.status)
    }

    @Test
    fun onlineOwnersBidAndTheClockSells() = apiTest(auctionSecondMillis = 50) { client ->
        val room = client.room()
        // 6 s when a player comes up, 150 ms after a bid.
        val id = client.createAuction(room, small.copy(mode = AuctionMode.ONLINE, lotSeconds = 120, bidSeconds = 3)).auction.id
        client.addPlayer(id, AddAuctionPlayerRequest(profileId = "dev")).details()
        client.addPlayer(id, AddAuctionPlayerRequest(profileId = "esha")).details()
        client.act("asha", id, "start").details()
        val up = client.act("asha", id, "next", NextPlayerRequest("dev")).details()
        assertNotNull(up.lotEndsAt)

        // Owners bid for their own team only; the organiser doesn't bid online.
        assertEquals(HttpStatusCode.Forbidden, client.act("bilal", id, "bids", BidRequest(room.teamB.id, 10)).status)
        assertEquals(HttpStatusCode.Forbidden, client.act("asha", id, "bids", BidRequest(room.teamA.id, 10)).status)
        val bid = client.act("bilal", id, "bids", BidRequest(room.teamA.id, 10)).details()
        assertTrue(bid.lotEndsAt!! < up.lotEndsAt!!, "The clock goes back to the shorter bid time")

        val sold = client.waitFor(id) { it.state.sales.isNotEmpty() }
        assertEquals(AuctionSale("dev", room.teamA.id, 10), sold.state.sales.single())
        assertNull(sold.lotEndsAt)

        // No bids: once the clock runs out the player goes unsold. Pausing stops the clock; resuming restarts it.
        client.act("asha", id, "next", NextPlayerRequest("esha")).details()
        val paused = client.act("asha", id, "pause").details()
        assertNull(paused.lotEndsAt)
        assertEquals(HttpStatusCode.Conflict, client.act("chetan", id, "bids", BidRequest(room.teamB.id, 10)).status)
        client.act("asha", id, "resume").details()
        val unsold = client.waitFor(id) { it.state.lot == null }
        assertEquals(AuctionPlayerStatus.UNSOLD, unsold.state.players["esha"])
        assertTrue(unsold.latest is AuctionEvent.Unsold)
    }

    @Test
    fun watchersGetEveryChangeLive() = apiTest { client ->
        val room = client.room()
        val id = client.createAuction(room).auction.id
        client.addPlayer(id, AddAuctionPlayerRequest(profileId = "dev")).details()
        client.webSocket("/auctions/$id/live", request = { bearerAuth("test-esha") }) {
            assertEquals(AuctionStatus.SETUP, receiveDeserialized<AuctionDetails>().state.status)
            client.act("asha", id, "start").details()
            assertEquals(AuctionStatus.LIVE, receiveDeserialized<AuctionDetails>().state.status)
            client.act("asha", id, "next").details()
            assertEquals("dev", receiveDeserialized<AuctionDetails>().state.lot?.profileId)
        }
    }

    /** Polls the auction until [done], for up to three seconds. */
    private suspend fun HttpClient.waitFor(id: String, done: (AuctionDetails) -> Boolean): AuctionDetails {
        repeat(60) {
            val details = get("/auctions/$id") { bearerAuth("test-asha") }.body<AuctionDetails>()
            if (done(details)) return details
            delay(50)
        }
        error("The auction didn't get there in time")
    }
}
