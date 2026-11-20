package com.harsh.desicrics

import com.harsh.desicrics.api.MatchDetails
import com.harsh.desicrics.api.PushTokenRequest
import com.harsh.desicrics.api.SquadPlayer
import com.harsh.desicrics.api.SquadRequest
import com.harsh.desicrics.api.TeamRequest
import com.harsh.desicrics.model.Delivery
import com.harsh.desicrics.model.Follow
import com.harsh.desicrics.model.FollowType
import com.harsh.desicrics.model.Notification
import com.harsh.desicrics.model.NotificationKind
import com.harsh.desicrics.model.PlayerPage
import com.harsh.desicrics.model.SavedTeam
import com.harsh.desicrics.model.SearchResults
import com.harsh.desicrics.model.TodayFeed
import com.harsh.desicrics.model.Wicket
import com.harsh.desicrics.model.DismissalKind
import io.ktor.client.call.body
import io.ktor.client.plugins.websocket.receiveDeserialized
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SocialTest {

    @Test
    fun followersHearAboutStartsResultsAndMilestones() {
        val push = RecordingPushSender()
        apiTest(push) { client ->
            client.setUpProfile("asha")
            client.setUpProfile("dev", name = "Dev")
            val team = client.post("/teams") { bearerAuth("test-asha"); jsonBody(TeamRequest("Strikers")) }.body<SavedTeam>()
            client.put("/follows/team/${team.id}") { bearerAuth("test-dev") }
            client.put("/follows/player/asha") { bearerAuth("test-dev") }
            client.put("/me/push-token") { bearerAuth("test-dev"); jsonBody(PushTokenRequest("device-1")) }

            val strikers = SquadRequest("Strikers", listOf(SquadPlayer("Asha", "asha"), SquadPlayer("Bilal")), teamId = team.id)
            val match = client.createMatch("asha", overs = 20, teamA = strikers).match
            val (a1, a2) = match.teamA.players
            val (b1, b2) = match.teamB.players

            suspend fun ball(seq: Int, striker: String, nonStriker: String, bowler: String, runs: Int = 0, wicket: Wicket? = null, innings: Int = 1) =
                assertEquals(HttpStatusCode.OK, client.record("asha", match.id, Delivery(seq, innings, striker, nonStriker, bowler, runs, wicket = wicket)).status)
            // Asha hits six sixes, Bilal gives her the strike back, and three more take her past 50: one milestone.
            repeat(6) { ball(it, a1.id, a2.id, b1.id, runs = 6) }
            ball(6, a2.id, a1.id, b2.id, runs = 1)
            repeat(3) { ball(7 + it, a1.id, a2.id, b2.id, runs = 6) }
            ball(10, a1.id, a2.id, b2.id, wicket = Wicket(DismissalKind.BOWLED, a1.id))
            // Royals need 56; they're bowled out first ball.
            ball(11, b1.id, b2.id, a1.id, wicket = Wicket(DismissalKind.BOWLED, b1.id), innings = 2)

            val inbox = client.get("/notifications") { bearerAuth("test-dev") }.body<List<Notification>>()
            assertEquals(
                listOf(NotificationKind.MATCH_RESULT, NotificationKind.MILESTONE_RUNS, NotificationKind.MATCH_STARTED),
                inbox.map { it.kind },
            )
            assertEquals(50, inbox[1].value)
            assertEquals(3, push.sent.size)
            // Live score updates: end of over 1, then Asha's wicket ends the innings and Royals' ends the match.
            assertEquals(listOf("live", "live", "live_ended"), push.data.map { it.getValue("type") })
            assertEquals("Strikers 36/0 (1.0)", push.data.first().getValue("score"))
            // The scorer isn't told about their own match.
            assertTrue(client.get("/notifications") { bearerAuth("test-asha") }.body<List<Notification>>().isEmpty())

            assertEquals(3, client.get("/feed") { bearerAuth("test-dev") }.body<TodayFeed>().unreadNotifications)
            client.post("/notifications/read") { bearerAuth("test-dev") }
            assertEquals(0, client.get("/feed") { bearerAuth("test-dev") }.body<TodayFeed>().unreadNotifications)
        }
    }

    @Test
    fun followsAreListedAndCanBeUndone() = apiTest { client ->
        client.setUpProfile("asha", name = "Asha Rao")
        client.setUpProfile("dev", name = "Dev")
        assertEquals(HttpStatusCode.BadRequest, client.put("/follows/player/dev") { bearerAuth("test-dev") }.status)
        assertEquals(HttpStatusCode.NotFound, client.put("/follows/team/nope") { bearerAuth("test-dev") }.status)
        client.put("/follows/player/asha") { bearerAuth("test-dev") }
        assertEquals(listOf(Follow(FollowType.PLAYER, "asha", "Asha Rao")), client.get("/follows") { bearerAuth("test-dev") }.body())
        val page = client.get("/players/asha") { bearerAuth("test-dev") }.body<PlayerPage>()
        assertTrue(page.following)
        assertEquals(1, page.followers)
        client.delete("/follows/player/asha") { bearerAuth("test-dev") }
        assertFalse(client.get("/players/asha") { bearerAuth("test-dev") }.body<PlayerPage>().following)
    }

    @Test
    fun feedPutsYourOwnMatchFirstAndSearchFindsThings() = apiTest { client ->
        client.setUpProfile("asha", name = "Asha Rao")
        client.setUpProfile("dev", name = "Dev")
        client.post("/teams") { bearerAuth("test-asha"); jsonBody(TeamRequest("Andheri Strikers")) }
        val theirs = client.createMatch("dev", teamA = squad("Powai XI", "x", "y")).match
        val mine = client.createMatch("asha", teamA = squad("Andheri Strikers", "a", "b")).match

        val feed = client.get("/feed") { bearerAuth("test-asha") }.body<TodayFeed>()
        assertEquals(listOf(mine.id), feed.mine.map { it.id })
        assertEquals(listOf(theirs.id), feed.live.map { it.id })

        val results = client.get("/search?q=andheri") { bearerAuth("test-dev") }.body<SearchResults>()
        assertEquals(listOf("Andheri Strikers"), results.teams.map { it.name })
        assertEquals(listOf(mine.id), results.matches.map { it.id })
        assertEquals(listOf("asha"), client.get("/search?q=asha") { bearerAuth("test-dev") }.body<SearchResults>().players.map { it.profileId })
    }

    @Test
    fun watchersGetEveryBallLive() = apiTest { client ->
        client.setUpProfile("asha")
        val match = client.createMatch("asha").match
        val (a1, a2) = match.teamA.players
        val b1 = match.teamB.players.first()
        client.webSocket("/matches/${match.id}/live", request = { bearerAuth("test-dev") }) {
            assertTrue(receiveDeserialized<MatchDetails>().deliveries.isEmpty())
            client.record("asha", match.id, Delivery(0, 1, a1.id, a2.id, b1.id, runsOffBat = 4))
            assertEquals("Strikers 4/0 (0.1)", receiveDeserialized<MatchDetails>().match.scoreSummary)
        }
    }

    @Test
    fun anyoneWithALinkGetsEveryBallLiveWithoutSigningIn() = apiTest { client ->
        client.setUpProfile("asha")
        val match = client.createMatch("asha").match
        val (a1, a2) = match.teamA.players
        val b1 = match.teamB.players.first()

        val details = client.get("/public/matches/${match.id}").body<MatchDetails>()
        assertEquals(match.teamA.name, details.match.teamA.name)
        assertEquals("", details.match.createdBy)
        assertEquals("", details.match.scorerUid)
        assertEquals(HttpStatusCode.NotFound, client.get("/public/matches/nope").status)

        client.webSocket("/public/matches/${match.id}/live") {
            assertTrue(receiveDeserialized<MatchDetails>().deliveries.isEmpty())
            client.record("asha", match.id, Delivery(0, 1, a1.id, a2.id, b1.id, runsOffBat = 4))
            val update = receiveDeserialized<MatchDetails>()
            assertEquals("Strikers 4/0 (0.1)", update.match.scoreSummary)
            assertEquals("", update.match.scorerUid)
        }
    }

    @Test
    fun anyoneCanOpenAMatchLink() = apiTest { client ->
        client.setUpProfile("asha")
        val match = client.createMatch("asha", teamA = squad("Strikers <script>", "Asha", "Bilal")).match
        val page = client.get("/m/${match.id}")
        assertEquals(HttpStatusCode.OK, page.status)
        val html = page.bodyAsText()
        assertTrue("Strikers &lt;script&gt;" in html)
        assertTrue("http-equiv=\"refresh\"" in html)
        assertEquals(HttpStatusCode.NotFound, client.get("/m/nope").status)
    }
}
