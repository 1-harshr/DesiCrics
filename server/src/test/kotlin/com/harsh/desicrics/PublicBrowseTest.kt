package com.harsh.desicrics

import com.harsh.desicrics.api.CreateTournamentRequest
import com.harsh.desicrics.api.SquadPlayer
import com.harsh.desicrics.api.SquadRequest
import com.harsh.desicrics.api.TeamRequest
import com.harsh.desicrics.api.UpdateProfileRequest
import com.harsh.desicrics.model.CareerStats
import com.harsh.desicrics.model.Delivery
import com.harsh.desicrics.model.DismissalKind
import com.harsh.desicrics.model.Match
import com.harsh.desicrics.model.PlayerPage
import com.harsh.desicrics.model.PlayerRole
import com.harsh.desicrics.model.PublicTeamPage
import com.harsh.desicrics.model.RecordAgainst
import com.harsh.desicrics.model.SavedTeam
import com.harsh.desicrics.model.SearchResults
import com.harsh.desicrics.model.TodayFeed
import com.harsh.desicrics.model.TournamentFormat
import com.harsh.desicrics.model.TournamentSummary
import com.harsh.desicrics.model.Wicket
import com.harsh.desicrics.model.WinLoss
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The web app's read-only browsing: no token, and no account IDs or codes in what comes back. */
class PublicBrowseTest {

    private suspend fun HttpClient.profileIn(uid: String, name: String, city: String) =
        put("/me") { bearerAuth("test-$uid"); jsonBody(UpdateProfileRequest(name = name, phone = "+91 98765 43210", city = city, role = PlayerRole.ALL_ROUNDER)) }

    private suspend fun HttpClient.team(uid: String, name: String, city: String): SavedTeam =
        post("/teams") { bearerAuth("test-$uid"); jsonBody(TeamRequest(name, city)) }.body()

    @Test
    fun feedListsLiveMatchesByCity() = apiTest { client ->
        client.profileIn("asha", "Asha", "Pune")
        val match = client.createMatch("asha").match

        val feed = client.get("/public/feed").body<TodayFeed>()
        assertEquals(listOf(match.id), feed.live.map { it.id })
        assertEquals("", feed.live.single().createdBy)
        assertEquals(listOf(match.id), client.get("/public/feed?city=pune").body<TodayFeed>().live.map { it.id })
        assertTrue(client.get("/public/feed?city=Mumbai").body<TodayFeed>().live.isEmpty())

        val page = client.get("/public/matches?status=IN_PROGRESS&city=Pune").body<List<Match>>()
        assertEquals(listOf(match.id), page.map { it.id })
        assertTrue(page.all { it.scorerUid.isEmpty() })
        assertEquals(HttpStatusCode.BadRequest, client.get("/public/matches?status=NOPE").status)
    }

    @Test
    fun teamAndPlayerPagesLeaveOutCodes() = apiTest { client ->
        client.profileIn("asha", "Asha", "Pune")
        val team = client.team("asha", "Kothrud Kings", "Pune")
        assertTrue(team.joinCode != null && team.code.isNotEmpty())

        val page = client.get("/public/teams/${team.id}").body<PublicTeamPage>()
        assertEquals("Kothrud Kings", page.team.name)
        assertNull(page.team.joinCode)
        assertEquals("", page.team.code)
        assertEquals("", page.team.createdBy)
        assertTrue(page.team.members.all { it.claimCode == null && it.player.code == null })
        assertEquals(HttpStatusCode.NotFound, client.get("/public/teams/nope").status)

        val search = client.get("/public/search?q=kothrud").body<SearchResults>()
        assertEquals(listOf(team.id), search.teams.map { it.id })
        assertEquals("", search.teams.single().code)

        val player = client.get("/public/players/asha").body<PlayerPage>()
        assertEquals("Asha", player.player.name)
        assertNull(player.player.code)
        assertEquals(listOf("Kothrud Kings"), player.teams.map { it.name })
        assertEquals("asha", client.get("/public/players/asha/stats").body<CareerStats>().profileId)
    }

    /** A one-over match between [a] and [b] (saved teams, or a guest side when null) that [aWins] or loses. */
    private suspend fun HttpClient.play(a: SavedTeam, b: SavedTeam?, aWins: Boolean) {
        val match = createMatch(
            "asha",
            teamA = SquadRequest(a.name, listOf(SquadPlayer("${a.name} 1"), SquadPlayer("${a.name} 2")), teamId = a.id),
            teamB = SquadRequest(b?.name ?: "Guests", listOf(SquadPlayer("B 1"), SquadPlayer("B 2")), teamId = b?.id),
        ).match
        val (a1, a2) = match.teamA.players
        val (b1, b2) = match.teamB.players
        record("asha", match.id, Delivery(0, 1, a1.id, a2.id, b1.id, runsOffBat = 4))
        record("asha", match.id, Delivery(1, 1, a1.id, a2.id, b1.id, wicket = Wicket(DismissalKind.BOWLED, a1.id)))
        val last = if (aWins) Delivery(2, 2, b1.id, b2.id, a1.id, wicket = Wicket(DismissalKind.BOWLED, b1.id))
        else Delivery(2, 2, b1.id, b2.id, a1.id, runsOffBat = 6)
        assertEquals(HttpStatusCode.OK, record("asha", match.id, last).status)
    }

    @Test
    fun teamPageHasItsRecordAgainstEachTeam() = apiTest { client ->
        client.profileIn("asha", "Asha", "Pune")
        val kings = client.team("asha", "Kothrud Kings", "Pune")
        val royals = client.team("asha", "Baner Royals", "Pune")
        client.play(kings, royals, aWins = true)
        client.play(kings, royals, aWins = false)
        client.play(kings, null, aWins = true)

        val page = client.get("/public/teams/${kings.id}").body<PublicTeamPage>()
        assertEquals(WinLoss(played = 3, won = 2, lost = 1), page.record)
        // The guest side has no team, so only the Royals get a record.
        assertEquals(listOf(RecordAgainst(royals.id, "Baner Royals", WinLoss(played = 2, won = 1, lost = 1))), page.against)
        assertEquals(3, page.matches.size)
    }

    @Test
    fun searchFindsTournaments() = apiTest { client ->
        client.profileIn("asha", "Asha", "Pune")
        client.post("/tournaments") {
            bearerAuth("test-asha")
            jsonBody(CreateTournamentRequest("Monsoon Cup", city = "Pune", format = TournamentFormat.KNOCKOUT, oversPerInnings = 6))
        }
        assertEquals(listOf("Monsoon Cup"), client.get("/public/search?q=monsoon").body<SearchResults>().tournaments.map { it.name })
        assertTrue(client.get("/public/search?q=m").body<SearchResults>().tournaments.isEmpty())
    }

    @Test
    fun tournamentsAreListedByCity() = apiTest { client ->
        client.profileIn("asha", "Asha", "Pune")
        client.post("/tournaments") {
            bearerAuth("test-asha")
            jsonBody(CreateTournamentRequest("Weekend Cup", city = "Pune", format = TournamentFormat.KNOCKOUT, oversPerInnings = 6))
        }

        val listed = client.get("/public/tournaments?city=pune").body<List<TournamentSummary>>()
        assertEquals(listOf("Weekend Cup"), listed.map { it.name })
        assertTrue(!listed.single().started && !listed.single().finished)
        assertTrue(client.get("/public/tournaments?city=Mumbai").body<List<TournamentSummary>>().isEmpty())
    }
}
