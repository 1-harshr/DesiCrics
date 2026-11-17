package com.harsh.desicrics

import com.harsh.desicrics.api.ApiError
import com.harsh.desicrics.api.CreateMatchRequest
import com.harsh.desicrics.api.MatchDetails
import com.harsh.desicrics.api.UpdateProfileRequest
import com.harsh.desicrics.auth.FirebasePrincipal
import com.harsh.desicrics.auth.TokenVerifier
import com.harsh.desicrics.model.Delivery
import com.harsh.desicrics.model.DismissalKind
import com.harsh.desicrics.model.MarginUnit
import com.harsh.desicrics.model.Match
import com.harsh.desicrics.model.MatchResult
import com.harsh.desicrics.model.MatchStatus
import com.harsh.desicrics.model.PlayerRole
import com.harsh.desicrics.model.TeamSide
import com.harsh.desicrics.model.TossDecision
import com.harsh.desicrics.model.UserProfile
import com.harsh.desicrics.model.Wicket
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApplicationTest {

    /** Tokens look like "test-<uid>" so tests can act as any user without Firebase. */
    private val fakeTokens = TokenVerifier { token -> token.removePrefix("test-").takeIf { it != token }?.let { FirebasePrincipal(it) } }

    private fun apiTest(block: suspend ApplicationTestBuilder.(HttpClient) -> Unit) = testApplication {
        val config = ServerConfig(database = DatabaseConfig(url = "jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1"))
        application { module(config, fakeTokens) }
        block(createClient { install(ContentNegotiation) { json() } })
    }

    private suspend fun HttpClient.setUpProfile(uid: String) =
        put("/me") {
            bearerAuth("test-$uid")
            contentType(ContentType.Application.Json)
            setBody(UpdateProfileRequest(name = "Asha", phone = "+91 98765 43210", role = PlayerRole.ALL_ROUNDER))
        }

    private suspend fun HttpClient.createMatch(uid: String): MatchDetails =
        post("/matches") {
            bearerAuth("test-$uid")
            contentType(ContentType.Application.Json)
            setBody(
                CreateMatchRequest(
                    teamAName = "Strikers", teamAPlayers = listOf("Asha", "Bilal", "Chetan"),
                    teamBName = "Royals", teamBPlayers = listOf("Dev", "Esha"),
                    oversPerInnings = 1, tossWinner = TeamSide.A, tossDecision = TossDecision.BAT,
                ),
            )
        }.body()

    private suspend fun HttpClient.record(uid: String, matchId: String, delivery: Delivery) =
        post("/matches/$matchId/deliveries") {
            bearerAuth("test-$uid")
            contentType(ContentType.Application.Json)
            setBody(delivery)
        }

    @Test
    fun rootIsPublic() = apiTest { client ->
        assertEquals("DesiCrics API", client.get("/").bodyAsText())
    }

    @Test
    fun apiRequiresAValidToken() = apiTest { client ->
        assertEquals(HttpStatusCode.Unauthorized, client.get("/me").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/me") { bearerAuth("forged") }.status)
    }

    @Test
    fun profileIsCreatedThenReturned() = apiTest { client ->
        assertEquals(HttpStatusCode.NotFound, client.get("/me") { bearerAuth("test-asha") }.status)
        assertEquals(HttpStatusCode.OK, client.setUpProfile("asha").status)
        val profile = client.get("/me") { bearerAuth("test-asha") }.body<UserProfile>()
        assertEquals("asha", profile.uid)
        assertEquals(PlayerRole.ALL_ROUNDER, profile.role)
    }

    @Test
    fun invalidProfileIsRejected() = apiTest { client ->
        val response = client.put("/me") {
            bearerAuth("test-asha")
            contentType(ContentType.Application.Json)
            setBody(UpdateProfileRequest(name = " ", phone = "123"))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("Please enter your name", response.body<ApiError>().message)
    }

    @Test
    fun startingAMatchNeedsAProfile() = apiTest { client ->
        val response = client.post("/matches") {
            bearerAuth("test-nobody")
            contentType(ContentType.Application.Json)
            setBody(CreateMatchRequest("A", listOf("a", "b"), "B", listOf("c", "d"), 2, TeamSide.A, TossDecision.BAT))
        }
        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    @Test
    fun scoringRoundTrip() = apiTest { client ->
        client.setUpProfile("asha")
        val created = client.createMatch("asha")
        val match = created.match
        assertEquals(listOf("Asha", "Bilal", "Chetan"), match.teamA.players.map { it.name })

        val (a1, a2) = match.teamA.players
        val b1 = match.teamB.players.first()
        val four = Delivery(seq = 0, innings = 1, strikerId = a1.id, nonStrikerId = a2.id, bowlerId = b1.id, runsOffBat = 4)

        val afterFour = client.record("asha", match.id, four).body<MatchDetails>()
        assertEquals("Strikers 4/0 (0.1)", afterFour.match.scoreSummary)

        // Watchers see the same scorecard, and the match shows in the list.
        val watched = client.get("/matches/${match.id}") { bearerAuth("test-dev") }.body<MatchDetails>()
        assertEquals(1, watched.deliveries.size)
        assertTrue(client.get("/matches") { bearerAuth("test-dev") }.body<List<Match>>().any { it.id == match.id })

        // Only the scorer can score, a double-tap is rejected, and impossible balls are refused.
        assertEquals(HttpStatusCode.Forbidden, client.record("dev", match.id, four.copy(seq = 1)).status)
        assertEquals(HttpStatusCode.Conflict, client.record("asha", match.id, four).status)
        val wrongStriker = client.record("asha", match.id, four.copy(seq = 1, strikerId = a2.id, nonStrikerId = a1.id).copy(strikerId = b1.id))
        assertEquals(HttpStatusCode.Conflict, wrongStriker.status)

        val wicket = four.copy(seq = 1, runsOffBat = 0, wicket = Wicket(DismissalKind.BOWLED, a1.id))
        assertEquals("Strikers 4/1 (0.2)", client.record("asha", match.id, wicket).body<MatchDetails>().match.scoreSummary)

        val undone = client.delete("/matches/${match.id}/deliveries/last") { bearerAuth("test-asha") }.body<MatchDetails>()
        assertEquals(listOf(four.seq), undone.deliveries.map { it.seq })
        assertEquals("Strikers 4/0 (0.1)", undone.match.scoreSummary)
    }

    @Test
    fun completedMatchKeepsItsResultAndFielders() = apiTest { client ->
        client.setUpProfile("asha")
        val match = client.createMatch("asha").match
        val (a1, a2) = match.teamA.players
        val (b1, b2) = match.teamB.players

        // Strikers make 4 off their one over.
        repeat(6) { seq ->
            val ball = Delivery(seq, innings = 1, strikerId = a1.id, nonStrikerId = a2.id, bowlerId = b1.id, runsOffBat = if (seq == 0) 4 else 0)
            assertEquals(HttpStatusCode.OK, client.record("asha", match.id, ball).status)
        }
        // Royals have two players, so one wicket is all out.
        val caught = Delivery(6, innings = 2, strikerId = b1.id, nonStrikerId = b2.id, bowlerId = a1.id, wicket = Wicket(DismissalKind.CAUGHT, b1.id, fielderId = a2.id))
        assertEquals(HttpStatusCode.OK, client.record("asha", match.id, caught).status)

        val listed = client.get("/matches") { bearerAuth("test-dev") }.body<List<Match>>().single { it.id == match.id }
        assertEquals(MatchStatus.COMPLETED, listed.status)
        assertEquals(MatchResult(TeamSide.A, 4, MarginUnit.RUNS), listed.result)
        val details = client.get("/matches/${match.id}") { bearerAuth("test-dev") }.body<MatchDetails>()
        assertEquals(a2.id, details.deliveries.last().wicket?.fielderId)
    }

    @Test
    fun unknownMatchIsNotFound() = apiTest { client ->
        assertEquals(HttpStatusCode.NotFound, client.get("/matches/nope") { bearerAuth("test-asha") }.status)
    }
}
