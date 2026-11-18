package com.harsh.desicrics

import com.harsh.desicrics.api.ApiError
import com.harsh.desicrics.api.AddPlayerRequest
import com.harsh.desicrics.api.CreateMatchRequest
import com.harsh.desicrics.api.MatchDetails
import com.harsh.desicrics.api.UpdateProfileRequest
import com.harsh.desicrics.auth.FirebasePrincipal
import com.harsh.desicrics.auth.TokenVerifier
import com.harsh.desicrics.model.Delivery
import com.harsh.desicrics.model.DismissalKind
import com.harsh.desicrics.model.ExtraType
import com.harsh.desicrics.model.InningsScore
import com.harsh.desicrics.model.MarginUnit
import com.harsh.desicrics.model.Match
import com.harsh.desicrics.model.MatchEvent
import com.harsh.desicrics.model.MatchResult
import com.harsh.desicrics.model.MatchRules
import com.harsh.desicrics.model.MatchStatus
import com.harsh.desicrics.model.PlayerRole
import com.harsh.desicrics.model.RuleMode
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
import kotlin.test.assertNull
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

    private suspend fun HttpClient.createMatch(uid: String, rules: MatchRules = MatchRules(), overs: Int = 1): MatchDetails =
        post("/matches") {
            bearerAuth("test-$uid")
            contentType(ContentType.Application.Json)
            setBody(
                CreateMatchRequest(
                    teamAName = "Strikers", teamAPlayers = listOf("Asha", "Bilal", "Chetan"),
                    teamBName = "Royals", teamBPlayers = listOf("Dev", "Esha"),
                    oversPerInnings = overs, tossWinner = TeamSide.A, tossDecision = TossDecision.BAT,
                    rules = rules,
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

        val undone = client.delete("/matches/${match.id}/last") { bearerAuth("test-asha") }.body<MatchDetails>()
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
        assertEquals(listOf(InningsScore(TeamSide.A, 4, 0, 6), InningsScore(TeamSide.B, 0, 1, 1)), listed.innings)
        val details = client.get("/matches/${match.id}") { bearerAuth("test-dev") }.body<MatchDetails>()
        assertEquals(a2.id, details.deliveries.last().wicket?.fielderId)
    }

    @Test
    fun retirementsAndLatecomers() = apiTest { client ->
        client.setUpProfile("asha")
        val match = client.createMatch("asha").match
        val (a1, a2) = match.teamA.players
        val b1 = match.teamB.players.first()
        val single = Delivery(0, innings = 1, strikerId = a1.id, nonStrikerId = a2.id, bowlerId = b1.id, runsOffBat = 1)
        assertEquals(HttpStatusCode.OK, client.record("asha", match.id, single).status)

        // a2 is now on strike and retires hurt; the event shares the sequence with balls.
        val retire: MatchEvent = MatchEvent.Retire(seq = 1, innings = 1, playerId = a2.id, out = false)
        val afterRetire = client.post("/matches/${match.id}/events") {
            bearerAuth("test-asha")
            contentType(ContentType.Application.Json)
            setBody(retire)
        }.body<MatchDetails>()
        assertEquals(listOf(1), afterRetire.events.map { it.seq })
        assertEquals(2, afterRetire.nextSeq)

        // A latecomer joins the batting side.
        val joined = client.post("/matches/${match.id}/players") {
            bearerAuth("test-asha")
            contentType(ContentType.Application.Json)
            setBody(AddPlayerRequest(TeamSide.A, "Zoya"))
        }.body<MatchDetails>()
        assertEquals("Zoya", joined.match.teamA.players.last().name)

        // A penalty is stored and read back like any event.
        val penalty: MatchEvent = MatchEvent.Penalty(seq = 2, innings = 1, toBattingSide = true, runs = 5)
        val afterPenalty = client.post("/matches/${match.id}/events") {
            bearerAuth("test-asha")
            contentType(ContentType.Application.Json)
            setBody(penalty)
        }.body<MatchDetails>()
        assertEquals(penalty, afterPenalty.events.last().let { (it as MatchEvent.Penalty).copy(timestamp = 0) })
        assertEquals("Strikers 6/0 (0.1)", afterPenalty.match.scoreSummary)
        client.delete("/matches/${match.id}/last") { bearerAuth("test-asha") }

        // Undo removes the retirement, the latest entry.
        val undone = client.delete("/matches/${match.id}/last") { bearerAuth("test-asha") }.body<MatchDetails>()
        assertTrue(undone.events.isEmpty())
        assertEquals(1, undone.deliveries.size)
    }

    @Test
    fun matchRulesAreKeptAndApplied() = apiTest { client ->
        client.setUpProfile("asha")
        val rules = MatchRules.Gully.copy(widePenalty = 2, wideRebowled = false, consecutiveOversMode = RuleMode.ENFORCE)
        val match = client.createMatch("asha", rules, overs = 2).match
        val (a1, a2, a3) = match.teamA.players
        val (b1, b2) = match.teamB.players
        assertEquals(rules, client.get("/matches/${match.id}") { bearerAuth("test-dev") }.body<MatchDetails>().match.rules)

        // A wide scored with the laws' penalty is refused; the match's own is stored on the ball.
        val lawsWide = Delivery(0, innings = 1, strikerId = a1.id, nonStrikerId = a2.id, bowlerId = b1.id, extra = ExtraType.WIDE, extraRuns = 1)
        assertEquals(HttpStatusCode.Conflict, client.record("asha", match.id, lawsWide).status)
        val wide = lawsWide.copy(extraRuns = 2, extraPenalty = 2, countsAsBall = true)
        val afterWide = client.record("asha", match.id, wide).body<MatchDetails>()
        assertEquals("Strikers 2/0 (0.1)", afterWide.match.scoreSummary)
        assertEquals(wide, afterWide.deliveries.single().copy(timestamp = 0))

        // Two wickets leave a3 alone, which Gully rules allow.
        var seq = 1
        for (batter in listOf(a1, a3)) {
            val ball = Delivery(seq++, innings = 1, strikerId = batter.id, nonStrikerId = a2.id, bowlerId = b1.id, wicket = Wicket(DismissalKind.BOWLED, batter.id))
            assertEquals(HttpStatusCode.OK, client.record("asha", match.id, ball).status)
        }
        val alone = Delivery(seq++, innings = 1, strikerId = a2.id, nonStrikerId = null, bowlerId = b1.id, runsOffBat = 1)
        val afterAlone = client.record("asha", match.id, alone).body<MatchDetails>()
        assertNull(afterAlone.deliveries.last().nonStrikerId)

        // Finish the over, then b1 can't bowl the next one.
        repeat(2) { client.record("asha", match.id, alone.copy(seq = seq++, runsOffBat = 0)) }
        assertEquals(HttpStatusCode.Conflict, client.record("asha", match.id, alone.copy(seq = seq)).status)
        assertEquals(HttpStatusCode.OK, client.record("asha", match.id, alone.copy(seq = seq, bowlerId = b2.id)).status)
    }

    @Test
    fun unknownMatchIsNotFound() = apiTest { client ->
        assertEquals(HttpStatusCode.NotFound, client.get("/matches/nope") { bearerAuth("test-asha") }.status)
    }
}
