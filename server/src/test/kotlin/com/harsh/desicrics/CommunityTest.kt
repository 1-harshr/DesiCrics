package com.harsh.desicrics

import com.harsh.desicrics.api.DirectoryRequest
import com.harsh.desicrics.api.LookingForRequest
import com.harsh.desicrics.api.RespondRequest
import com.harsh.desicrics.api.SquadPlayer
import com.harsh.desicrics.api.SquadRequest
import com.harsh.desicrics.api.TeamRequest
import com.harsh.desicrics.model.Delivery
import com.harsh.desicrics.model.DirectoryEntry
import com.harsh.desicrics.model.DirectoryKind
import com.harsh.desicrics.model.DismissalKind
import com.harsh.desicrics.model.LookingForKind
import com.harsh.desicrics.model.LookingForPost
import com.harsh.desicrics.model.OverlayScore
import com.harsh.desicrics.model.SavedTeam
import com.harsh.desicrics.model.TeamRanking
import com.harsh.desicrics.model.Wicket
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommunityTest {

    private val tomorrow = System.currentTimeMillis() + 24 * 60 * 60 * 1000L

    @Test
    fun lookingForAnOpponent() = apiTest { client ->
        client.setUpProfile("asha")
        client.setUpProfile("dev", name = "Dev")
        val team = client.post("/teams") { bearerAuth("test-asha"); jsonBody(TeamRequest("Strikers", "Pune")) }.body<SavedTeam>()
        // Only a team's admins post for it.
        val notAdmin = client.post("/looking-for") { bearerAuth("test-dev"); jsonBody(LookingForRequest(LookingForKind.OPPONENT, "Pune", tomorrow, teamId = team.id)) }
        assertEquals(HttpStatusCode.Forbidden, notAdmin.status)
        val post = client.post("/looking-for") {
            bearerAuth("test-asha")
            jsonBody(LookingForRequest(LookingForKind.OPPONENT, "Pune", tomorrow, "Tennis ball, 10 overs, Sunday morning", team.id))
        }.body<LookingForPost>()
        assertEquals("Strikers", post.team?.name)

        val open = client.get("/looking-for?city=pune") { bearerAuth("test-dev") }.body<List<LookingForPost>>()
        assertEquals(listOf(post.id), open.map { it.id })
        assertTrue(client.get("/looking-for?city=pune&kind=umpire") { bearerAuth("test-dev") }.body<List<LookingForPost>>().isEmpty())

        val answered = client.post("/looking-for/${post.id}/responses") { bearerAuth("test-dev"); jsonBody(RespondRequest("We're in!", "9123456789")) }
        assertEquals(1, answered.body<LookingForPost>().responseCount)
        // Responses stay private to the author, once per person, and not on your own post.
        assertTrue(answered.body<LookingForPost>().responses.isEmpty())
        assertEquals(HttpStatusCode.Conflict, client.post("/looking-for/${post.id}/responses") { bearerAuth("test-dev"); jsonBody(RespondRequest("Again", "9123456789")) }.status)
        assertEquals(HttpStatusCode.BadRequest, client.post("/looking-for/${post.id}/responses") { bearerAuth("test-asha"); jsonBody(RespondRequest("Me", "9123456789")) }.status)
        val mine = client.get("/looking-for/mine") { bearerAuth("test-asha") }.body<List<LookingForPost>>().single()
        assertEquals("We're in!", mine.responses.single().message)
        assertEquals("9123456789", mine.responses.single().phone)

        assertEquals(HttpStatusCode.Forbidden, client.post("/looking-for/${post.id}/close") { bearerAuth("test-dev") }.status)
        client.post("/looking-for/${post.id}/close") { bearerAuth("test-asha") }
        assertTrue(client.get("/looking-for?city=Pune") { bearerAuth("test-dev") }.body<List<LookingForPost>>().isEmpty())
    }

    @Test
    fun pastPostsDropOffTheList() = apiTest { client ->
        client.setUpProfile("asha")
        client.post("/looking-for") { bearerAuth("test-asha"); jsonBody(LookingForRequest(LookingForKind.PLAYER, "Pune", 1_000L)) }
        assertTrue(client.get("/looking-for?city=Pune") { bearerAuth("test-asha") }.body<List<LookingForPost>>().isEmpty())
    }

    @Test
    fun directoryOfGroundsUmpiresAndScorers() = apiTest { client ->
        client.setUpProfile("asha")
        client.setUpProfile("dev", name = "Dev")
        val ground = client.post("/directory") {
            bearerAuth("test-asha")
            jsonBody(DirectoryRequest(DirectoryKind.GROUND, "Oval Maidan", "Mumbai", notes = "Book at the gate"))
        }.body<DirectoryEntry>()
        client.post("/directory") { bearerAuth("test-dev"); jsonBody(DirectoryRequest(DirectoryKind.UMPIRE, "Ravi", "Mumbai", "9876543210")) }
        assertEquals(2, client.get("/directory?city=mumbai") { bearerAuth("test-dev") }.body<List<DirectoryEntry>>().size)
        assertEquals(listOf("Oval Maidan"), client.get("/directory?city=mumbai&kind=ground") { bearerAuth("test-dev") }.body<List<DirectoryEntry>>().map { it.name })
        assertEquals(HttpStatusCode.Forbidden, client.delete("/directory/${ground.id}") { bearerAuth("test-dev") }.status)
        assertEquals(HttpStatusCode.NoContent, client.delete("/directory/${ground.id}") { bearerAuth("test-asha") }.status)
    }

    /** Saved team [winner] beats [loser] in a one-over match: four runs, then both sides bowled out. */
    private suspend fun HttpClient.win(winner: SavedTeam, loser: SavedTeam) {
        val match = createMatch(
            "asha",
            teamA = SquadRequest(winner.name, listOf(SquadPlayer("${winner.name} 1"), SquadPlayer("${winner.name} 2")), teamId = winner.id),
            teamB = SquadRequest(loser.name, listOf(SquadPlayer("${loser.name} 1"), SquadPlayer("${loser.name} 2")), teamId = loser.id),
        ).match
        val (a1, a2) = match.teamA.players
        val (b1, b2) = match.teamB.players
        record("asha", match.id, Delivery(0, 1, a1.id, a2.id, b1.id, runsOffBat = 4))
        record("asha", match.id, Delivery(1, 1, a1.id, a2.id, b1.id, wicket = Wicket(DismissalKind.BOWLED, a1.id)))
        record("asha", match.id, Delivery(2, 2, b1.id, b2.id, a1.id, wicket = Wicket(DismissalKind.BOWLED, b1.id)))
    }

    @Test
    fun cityTeamRankingsByElo() = apiTest { client ->
        client.setUpProfile("asha")
        val teams = listOf("Andheri", "Bandra", "Colaba").map { client.post("/teams") { bearerAuth("test-asha"); jsonBody(TeamRequest(it, "Mumbai")) }.body<SavedTeam>() }
        val (andheri, bandra, colaba) = teams
        client.win(andheri, bandra)
        client.win(andheri, colaba)
        client.win(bandra, colaba)
        val ranked = client.get("/rankings/teams?city=Mumbai") { bearerAuth("test-asha") }.body<List<TeamRanking>>()
        assertEquals(listOf("Andheri", "Bandra", "Colaba"), ranked.map { it.team.name })
        assertTrue(ranked.first().rating > 1000 && ranked.last().rating < 1000)
        assertEquals(2, ranked.first().played)
    }

    @Test
    fun streamOverlayIsPublic() = apiTest { client ->
        client.setUpProfile("asha")
        val match = client.createMatch("asha").match
        val (a1, a2) = match.teamA.players
        val b1 = match.teamB.players.first()
        client.record("asha", match.id, Delivery(0, 1, a1.id, a2.id, b1.id, runsOffBat = 6))
        val score = client.get("/m/${match.id}/overlay.json").body<OverlayScore>()
        assertEquals("6/0", score.score)
        assertEquals(listOf("6"), score.thisOver)
        assertEquals("Asha 6 (1)", score.striker)
        assertTrue(client.get("/overlay/${match.id}").bodyAsText().contains("/m/${match.id}/overlay.json"))
        assertEquals(HttpStatusCode.NotFound, client.get("/overlay/nope").status)
    }
}
