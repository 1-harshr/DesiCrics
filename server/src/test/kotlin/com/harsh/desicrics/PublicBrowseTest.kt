package com.harsh.desicrics

import com.harsh.desicrics.api.CreateTournamentRequest
import com.harsh.desicrics.api.TeamRequest
import com.harsh.desicrics.api.UpdateProfileRequest
import com.harsh.desicrics.model.CareerStats
import com.harsh.desicrics.model.Match
import com.harsh.desicrics.model.PlayerPage
import com.harsh.desicrics.model.PlayerRole
import com.harsh.desicrics.model.PublicTeamPage
import com.harsh.desicrics.model.SavedTeam
import com.harsh.desicrics.model.SearchResults
import com.harsh.desicrics.model.TodayFeed
import com.harsh.desicrics.model.TournamentFormat
import com.harsh.desicrics.model.TournamentSummary
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
