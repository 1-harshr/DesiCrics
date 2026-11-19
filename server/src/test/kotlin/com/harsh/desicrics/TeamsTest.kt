package com.harsh.desicrics

import com.harsh.desicrics.api.AddMemberRequest
import com.harsh.desicrics.api.ClaimRequest
import com.harsh.desicrics.api.SetRoleRequest
import com.harsh.desicrics.api.SquadPlayer
import com.harsh.desicrics.api.SquadRequest
import com.harsh.desicrics.api.SupportCategory
import com.harsh.desicrics.api.SupportRequest
import com.harsh.desicrics.api.TeamRequest
import com.harsh.desicrics.model.HeadToHead
import com.harsh.desicrics.model.Match
import com.harsh.desicrics.model.Notification
import com.harsh.desicrics.model.NotificationKind
import com.harsh.desicrics.model.PlayerSummary
import com.harsh.desicrics.model.SavedTeam
import com.harsh.desicrics.model.TeamRole
import com.harsh.desicrics.model.TeamSummary
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TeamsTest {

    private suspend fun HttpClient.createTeam(uid: String, name: String = "Andheri Strikers"): SavedTeam =
        post("/teams") {
            bearerAuth("test-$uid")
            jsonBody(TeamRequest(name, "Mumbai"))
        }.body()

    private suspend fun HttpClient.team(uid: String, id: String): SavedTeam = get("/teams/$id") { bearerAuth("test-$uid") }.body()

    @Test
    fun creatorIsAdminAndOthersJoinWithTheCode() = apiTest { client ->
        client.setUpProfile("asha")
        client.setUpProfile("dev", name = "Dev")
        val team = client.createTeam("asha")
        assertEquals(TeamRole.ADMIN, team.roleOf("asha"))
        val code = assertNotNull(team.joinCode)

        val joined = client.post("/teams/join/$code") { bearerAuth("test-dev") }.body<SavedTeam>()
        assertEquals(TeamRole.MEMBER, joined.roleOf("dev"))
        // Members don't see the join code.
        assertNull(joined.joinCode)
        assertEquals(listOf(TeamSummary(team.id, "Andheri Strikers", "Mumbai", 2, TeamRole.MEMBER, code = team.code)), client.get("/teams/mine") { bearerAuth("test-dev") }.body())

        // Only admins manage the team.
        val rename = client.put("/teams/${team.id}") { bearerAuth("test-dev"); jsonBody(TeamRequest("Mine now")) }
        assertEquals(HttpStatusCode.Forbidden, rename.status)
        val newCode = client.post("/teams/${team.id}/join-code") { bearerAuth("test-asha") }.body<SavedTeam>().joinCode
        assertTrue(newCode != code)
        assertEquals(HttpStatusCode.NotFound, client.post("/teams/join/$code") { bearerAuth("test-dev") }.status)
    }

    @Test
    fun playersAndTeamsHavePublicCodesToFindThemBy() = apiTest { client ->
        val asha = client.setUpProfile("asha").body<com.harsh.desicrics.model.UserProfile>()
        val dev = client.setUpProfile("dev", name = "Dev Patel").body<com.harsh.desicrics.model.UserProfile>()
        assertTrue(asha.code.length == 8 && asha.code.all(Char::isDigit))
        assertTrue(asha.code != dev.code)
        // Editing the profile keeps the code.
        assertEquals(asha.code, client.setUpProfile("asha", name = "Asha K").body<com.harsh.desicrics.model.UserProfile>().code)

        val team = client.createTeam("asha")
        assertTrue(team.code.length == 8 && team.code.all(Char::isDigit))

        // Spaces in a code are fine, the way it's shown in the app.
        val spaced = dev.code.chunked(4).joinToString(" ")
        val found = client.get("/players/search?q=$spaced") { bearerAuth("test-asha") }.body<List<PlayerSummary>>()
        assertEquals(listOf("dev" to dev.code), found.map { it.profileId to it.code })
        val search = client.get("/search?q=${team.code}") { bearerAuth("test-dev") }.body<com.harsh.desicrics.model.SearchResults>()
        assertEquals(listOf(team.id), search.teams.map { it.id })
        assertTrue(search.players.isEmpty())
        // The public code finds the team but doesn't let anyone in.
        assertEquals(HttpStatusCode.NotFound, client.post("/teams/join/${team.code}") { bearerAuth("test-dev") }.status)
    }

    @Test
    fun playersAskToJoinAndAdminsDecide() = apiTest { client ->
        client.setUpProfile("asha")
        client.setUpProfile("dev", name = "Dev")
        client.setUpProfile("kabir", name = "Kabir")
        val team = client.createTeam("asha")
        val requests = "/teams/${team.id}/join-requests"

        // Asking twice is harmless, and the asker only sees that they're waiting.
        client.post(requests) { bearerAuth("test-dev") }
        val waiting = client.post(requests) { bearerAuth("test-dev") }.body<SavedTeam>()
        assertTrue(waiting.requested)
        assertTrue(waiting.joinRequests.isEmpty())
        assertNull(waiting.roleOf("dev"))
        client.post(requests) { bearerAuth("test-kabir") }

        // The admin sees who's asking, oldest first, and an inbox entry for each, pointing at the team.
        assertEquals(listOf("dev", "kabir"), client.team("asha", team.id).joinRequests.map { it.profileId })
        val inbox = client.get("/notifications") { bearerAuth("test-asha") }.body<List<Notification>>()
        assertEquals(listOf(NotificationKind.TEAM_JOIN_REQUEST), inbox.map { it.kind }.distinct())
        assertEquals(setOf("Dev", "Kabir"), inbox.map { it.playerName }.toSet())
        assertEquals(team.id, inbox.first().teamId)

        // Only admins decide.
        assertEquals(HttpStatusCode.Forbidden, client.post("$requests/kabir") { bearerAuth("test-dev") }.status)

        val accepted = client.post("$requests/dev") { bearerAuth("test-asha") }.body<SavedTeam>()
        assertEquals(TeamRole.MEMBER, accepted.roleOf("dev"))
        assertEquals(listOf("kabir"), accepted.joinRequests.map { it.profileId })
        val devInbox = client.get("/notifications") { bearerAuth("test-dev") }.body<List<Notification>>()
        assertEquals(listOf(NotificationKind.TEAM_JOIN_ACCEPTED), devInbox.map { it.kind })
        assertEquals(HttpStatusCode.NotFound, client.post("$requests/dev") { bearerAuth("test-asha") }.status)

        // Declining is quiet, and they can ask again.
        val declined = client.delete("$requests/kabir") { bearerAuth("test-asha") }.body<SavedTeam>()
        assertTrue(declined.joinRequests.isEmpty())
        assertNull(declined.roleOf("kabir"))
        assertTrue(client.get("/notifications") { bearerAuth("test-kabir") }.body<List<Notification>>().isEmpty())
        assertFalse(client.team("kabir", team.id).requested)

        // Asking, then joining with the code, clears the request.
        client.post(requests) { bearerAuth("test-kabir") }
        client.post("/teams/join/${team.joinCode}") { bearerAuth("test-kabir") }
        assertTrue(client.team("asha", team.id).joinRequests.isEmpty())
    }

    @Test
    fun aPlayerCanTakeBackTheirRequest() = apiTest { client ->
        client.setUpProfile("asha")
        client.setUpProfile("dev", name = "Dev")
        val team = client.createTeam("asha")
        client.post("/teams/${team.id}/join-requests") { bearerAuth("test-dev") }
        val cancelled = client.delete("/teams/${team.id}/join-requests") { bearerAuth("test-dev") }.body<SavedTeam>()
        assertFalse(cancelled.requested)
        assertTrue(client.team("asha", team.id).joinRequests.isEmpty())
    }

    @Test
    fun adminsAddPlayersBySearchOrAsPlaceholders() = apiTest { client ->
        client.setUpProfile("asha")
        client.setUpProfile("dev", name = "Dev Patel", phone = "9123456789")
        val team = client.createTeam("asha")

        val found = client.get("/players/search?q=pat") { bearerAuth("test-asha") }.body<List<PlayerSummary>>()
        assertEquals(listOf("dev"), found.map { it.profileId })
        val byPhone = client.get("/players/search?q=91234 56789") { bearerAuth("test-asha") }.body<List<PlayerSummary>>()
        assertTrue(byPhone.isEmpty())

        client.post("/teams/${team.id}/members") { bearerAuth("test-asha"); jsonBody(AddMemberRequest(profileId = "dev")) }
        val withGuest = client.post("/teams/${team.id}/members") {
            bearerAuth("test-asha")
            jsonBody(AddMemberRequest(placeholderName = "Kabir"))
        }.body<SavedTeam>()
        val kabir = withGuest.members.single { it.player.name == "Kabir" }
        assertTrue(kabir.player.isPlaceholder)
        assertNotNull(kabir.claimCode)
        // Placeholders aren't searchable; only real users are.
        assertTrue(client.get("/players/search?q=kab") { bearerAuth("test-asha") }.body<List<PlayerSummary>>().isEmpty())
        val again = client.post("/teams/${team.id}/members") { bearerAuth("test-asha"); jsonBody(AddMemberRequest(profileId = "dev")) }
        assertEquals(HttpStatusCode.Conflict, again.status)
    }

    @Test
    fun claimingAPlaceholderMovesItsMatchesAndTeams() = apiTest { client ->
        client.setUpProfile("asha")
        val team = client.createTeam("asha")
        val kabir = client.post("/teams/${team.id}/members") {
            bearerAuth("test-asha")
            jsonBody(AddMemberRequest(placeholderName = "Kabir", placeholderPhone = "+91 90000 11111"))
        }.body<SavedTeam>().members.single { it.player.isPlaceholder }

        // Kabir plays a match before he has the app.
        val squad = SquadRequest("Andheri Strikers", listOf(SquadPlayer("Asha", "asha"), SquadPlayer("Kabir", kabir.player.profileId)), teamId = team.id, captain = 0, keeper = 1)
        val match = client.createMatch("asha", teamA = squad).match
        assertEquals(match.teamA.players[0].id, match.teamA.captainId)
        assertEquals(kabir.player.profileId, match.teamA.players[1].profileId)

        // He signs in with that phone number and finds his placeholder waiting.
        client.setUpProfile("kabir", name = "Kabir Shah", phone = "9000011111")
        val claimable = client.get("/players/claimable") { bearerAuth("test-kabir.919000011111") }.body<List<PlayerSummary>>()
        assertEquals(listOf(kabir.player.profileId), claimable.map { it.profileId })
        // A phone that doesn't match can't claim it.
        val stranger = client.post("/players/claim") { bearerAuth("test-kabir.919999999999"); jsonBody(ClaimRequest(profileId = kabir.player.profileId)) }
        assertEquals(HttpStatusCode.NotFound, stranger.status)
        client.post("/players/claim") { bearerAuth("test-kabir.919000011111"); jsonBody(ClaimRequest(profileId = kabir.player.profileId)) }

        assertEquals(TeamRole.MEMBER, client.team("kabir", team.id).roleOf("kabir"))
        val played = client.get("/me/matches") { bearerAuth("test-kabir") }.body<List<Match>>()
        assertEquals(listOf(match.id), played.map { it.id })
        val reloaded = client.get("/matches/${match.id}") { bearerAuth("test-kabir") }.body<com.harsh.desicrics.api.MatchDetails>()
        assertEquals("kabir", reloaded.match.teamA.players[1].profileId)
    }

    @Test
    fun claimByCode() = apiTest { client ->
        client.setUpProfile("asha")
        client.setUpProfile("zoya", name = "Zoya")
        val team = client.createTeam("asha")
        val code = client.post("/teams/${team.id}/members") {
            bearerAuth("test-asha")
            jsonBody(AddMemberRequest(placeholderName = "Zoya"))
        }.body<SavedTeam>().members.single { it.player.isPlaceholder }.claimCode!!
        val claimed = client.post("/players/claim") { bearerAuth("test-zoya"); jsonBody(ClaimRequest(code = code.lowercase())) }
        assertEquals(HttpStatusCode.OK, claimed.status)
        assertEquals(HttpStatusCode.NotFound, client.post("/players/claim") { bearerAuth("test-zoya"); jsonBody(ClaimRequest(code = code)) }.status)
    }

    @Test
    fun theLastAdminCantLeaveOrStepDown() = apiTest { client ->
        client.setUpProfile("asha")
        client.setUpProfile("dev", name = "Dev")
        val team = client.createTeam("asha")
        client.post("/teams/join/${team.joinCode}") { bearerAuth("test-dev") }

        assertEquals(HttpStatusCode.Conflict, client.delete("/teams/${team.id}/members/asha") { bearerAuth("test-asha") }.status)
        assertEquals(HttpStatusCode.Conflict, client.put("/teams/${team.id}/members/asha") { bearerAuth("test-asha"); jsonBody(SetRoleRequest(TeamRole.MEMBER)) }.status)
        client.put("/teams/${team.id}/members/dev") { bearerAuth("test-asha"); jsonBody(SetRoleRequest(TeamRole.ADMIN)) }
        assertEquals(HttpStatusCode.OK, client.delete("/teams/${team.id}/members/asha") { bearerAuth("test-asha") }.status)
        assertNull(client.team("dev", team.id).roleOf("asha"))
        // A member can't remove others.
        client.setUpProfile("esha", name = "Esha")
        client.post("/teams/join/${client.team("dev", team.id).joinCode}") { bearerAuth("test-esha") }
        assertEquals(HttpStatusCode.Forbidden, client.delete("/teams/${team.id}/members/dev") { bearerAuth("test-esha") }.status)
    }

    @Test
    fun deletingAnAccountClearsItButKeepsScorecards() = apiTest { client ->
        client.setUpProfile("asha")
        client.setUpProfile("dev", name = "Dev")
        val team = client.createTeam("asha")
        client.post("/teams/join/${team.joinCode}") { bearerAuth("test-dev") }
        val match = client.createMatch("asha", teamA = SquadRequest("Strikers", listOf(SquadPlayer("Asha", "asha"), SquadPlayer("Dev", "dev")))).match

        assertEquals(HttpStatusCode.NoContent, client.delete("/me") { bearerAuth("test-asha") }.status)
        assertEquals(HttpStatusCode.NotFound, client.get("/me") { bearerAuth("test-asha") }.status)
        // Dev runs the team now, and the match is still there.
        assertEquals(TeamRole.ADMIN, client.team("dev", team.id).roleOf("dev"))
        assertEquals(HttpStatusCode.OK, client.get("/matches/${match.id}") { bearerAuth("test-dev") }.status)
        assertTrue(client.get("/players/search?q=asha") { bearerAuth("test-dev") }.body<List<PlayerSummary>>().isEmpty())
    }

    @Test
    fun headToHeadAndLastSquad() = apiTest { client ->
        client.setUpProfile("asha")
        val strikers = client.createTeam("asha")
        val royals = client.createTeam("asha", "Royals")
        val match = client.createMatch(
            "asha",
            teamA = SquadRequest("Strikers", listOf(SquadPlayer("Asha", "asha"), SquadPlayer("Guest")), teamId = strikers.id),
            teamB = SquadRequest("Royals", listOf(SquadPlayer("X"), SquadPlayer("Y")), teamId = royals.id),
        ).match
        assertEquals(listOf("asha"), client.team("asha", strikers.id).lastSquad)
        val h2h = client.get("/teams/${strikers.id}/head-to-head/${royals.id}") { bearerAuth("test-asha") }.body<HeadToHead>()
        // Not finished yet, so it doesn't count.
        assertEquals(0, h2h.played)
        assertFalse(match.teamA.teamId == null)
    }

    @Test
    fun joinLinksHaveAPublicPage() = apiTest { client ->
        val page = client.get("/join/ab3<b>")
        assertEquals(HttpStatusCode.OK, page.status)
        assertTrue(page.bodyAsText().contains("AB3B"))
    }

    @Test
    fun assetLinksListTheApp() = apiTest { client ->
        val links = client.get("/.well-known/assetlinks.json")
        assertEquals(HttpStatusCode.OK, links.status)
        assertTrue("\"package_name\":\"com.harsh.desicrics\"" in links.bodyAsText())
    }

    @Test
    fun supportMessagesAreSaved() = apiTest { client ->
        client.setUpProfile("asha")
        val ok = client.post("/support") { bearerAuth("test-asha"); jsonBody(SupportRequest(SupportCategory.BUG, "Undo froze")) }
        assertEquals(HttpStatusCode.NoContent, ok.status)
        val empty = client.post("/support") { bearerAuth("test-asha"); jsonBody(SupportRequest(SupportCategory.BUG, " ")) }
        assertEquals(HttpStatusCode.BadRequest, empty.status)
    }
}

class ImagesTest {
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3)

    @Test
    fun profilePhotosAndTeamLogos() = apiTest { client ->
        client.setUpProfile("asha")
        client.setUpProfile("dev", name = "Dev")
        val profile = client.put("/me/photo") {
            bearerAuth("test-asha")
            contentType(ContentType.Image.PNG)
            setBody(png)
        }.body<com.harsh.desicrics.model.UserProfile>()
        val path = profile.photoUrl!!
        assertTrue(path.startsWith("/images/"))
        // Served to anyone, cached for good.
        val served = client.get(path)
        assertEquals(HttpStatusCode.OK, served.status)
        assertTrue(served.readRawBytes().contentEquals(png))
        assertEquals("public, max-age=31536000, immutable", served.headers[io.ktor.http.HttpHeaders.CacheControl])
        // And it shows wherever the player does.
        assertEquals(path, client.get("/players/asha") { bearerAuth("test-dev") }.body<com.harsh.desicrics.model.PlayerPage>().player.photoUrl)

        val team = client.post("/teams") { bearerAuth("test-asha"); jsonBody(com.harsh.desicrics.api.TeamRequest("Strikers")) }.body<SavedTeam>()
        val notAdmin = client.put("/teams/${team.id}/logo") { bearerAuth("test-dev"); contentType(ContentType.Image.PNG); setBody(png) }
        assertEquals(HttpStatusCode.Forbidden, notAdmin.status)
        val withLogo = client.put("/teams/${team.id}/logo") { bearerAuth("test-asha"); contentType(ContentType.Image.PNG); setBody(png) }.body<SavedTeam>()
        assertTrue(withLogo.logoUrl!!.startsWith("/images/"))

        val text = client.put("/me/photo") { bearerAuth("test-asha"); contentType(ContentType.Text.Plain); setBody("hi") }
        assertEquals(HttpStatusCode.UnsupportedMediaType, text.status)
        val huge = client.put("/me/photo") { bearerAuth("test-asha"); contentType(ContentType.Image.JPEG); setBody(ByteArray(1_000_001)) }
        assertEquals(HttpStatusCode.PayloadTooLarge, huge.status)
    }
}
