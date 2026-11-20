package com.harsh.desicrics

import com.harsh.desicrics.api.ApiError
import com.harsh.desicrics.api.AddPlayerRequest
import com.harsh.desicrics.api.CreateMatchRequest
import com.harsh.desicrics.api.HandoverCode
import com.harsh.desicrics.api.MatchDetails
import com.harsh.desicrics.api.UpdateProfileRequest
import com.harsh.desicrics.auth.FirebasePrincipal
import com.harsh.desicrics.auth.TokenVerifier
import com.harsh.desicrics.model.Delivery
import com.harsh.desicrics.model.DismissalKind
import com.harsh.desicrics.model.ExtraType
import com.harsh.desicrics.model.FieldZone
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
import com.harsh.desicrics.scoring.ScoringEngine
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

    @Test
    fun rootIsPublic() = apiTest { client ->
        assertEquals("DesiCrics API", client.get("/").bodyAsText())
        assertEquals("ok", client.get("/health").bodyAsText())
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
            setBody(CreateMatchRequest(squad("A", "a", "b"), squad("B", "c", "d"), 2, TeamSide.A, TossDecision.BAT))
        }
        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    @Test
    fun scheduledMatchKeepsItsVenueAndStartsOnce() = apiTest { client ->
        client.setUpProfile("asha")
        client.setUpProfile("dev")
        val created = client.post("/matches") {
            bearerAuth("test-asha")
            jsonBody(CreateMatchRequest(squad("A"), squad("B"), 5, pending = true, scheduledAt = 1800000000000L, venue = "Shivaji Park"))
        }.body<MatchDetails>().match
        val path = "/matches/${created.id}"
        val saved = client.get(path) { bearerAuth("test-asha") }.body<MatchDetails>().match
        assertEquals(MatchStatus.SCHEDULED, saved.status)
        assertEquals("Shivaji Park", saved.venue)
        val request = com.harsh.desicrics.api.StartFixtureRequest(squad("A", "One", "Two"), squad("B", "Three", "Four"), TeamSide.B, TossDecision.BAT)
        suspend fun start(uid: String) = client.post("$path/start") { bearerAuth("test-$uid"); jsonBody(request) }
        assertEquals(HttpStatusCode.Forbidden, start("dev").status)
        assertEquals(MatchStatus.IN_PROGRESS, start("asha").body<MatchDetails>().match.status)
        assertEquals(HttpStatusCode.Conflict, start("asha").status)
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
        assertEquals(
            listOf(InningsScore(TeamSide.A, 4, 0, 6, maxBalls = 6), InningsScore(TeamSide.B, 0, 1, 1, target = 5, maxBalls = 6, allOut = true)),
            listed.innings,
        )
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
    fun undoingTheBallAfterABowlerChangeKeepsTheNewBowler() = apiTest { client ->
        client.setUpProfile("asha")
        val match = client.createMatch("asha").match
        val (a1, a2) = match.teamA.players
        val (b1, b2) = match.teamB.players
        val dot = Delivery(0, innings = 1, strikerId = a1.id, nonStrikerId = a2.id, bowlerId = b1.id)
        repeat(5) { assertEquals(HttpStatusCode.OK, client.record("asha", match.id, dot.copy(seq = it)).status) }

        // b2 finishes the over; the last ball is taken back, and b2 is still the one bowling, even on a fresh load.
        val change: MatchEvent = MatchEvent.ReplaceBowler(seq = 5, innings = 1, bowlerId = b2.id)
        client.post("/matches/${match.id}/events") {
            bearerAuth("test-asha")
            contentType(ContentType.Application.Json)
            setBody(change)
        }
        client.record("asha", match.id, dot.copy(seq = 6, bowlerId = b2.id))
        client.delete("/matches/${match.id}/last") { bearerAuth("test-asha") }
        val reloaded = client.get("/matches/${match.id}") { bearerAuth("test-asha") }.body<MatchDetails>()
        assertEquals(b2.id, (reloaded.events.single() as MatchEvent.ReplaceBowler).bowlerId)
        assertEquals(b2.id, ScoringEngine.compute(reloaded.match, reloaded.deliveries, reloaded.events).current.bowlerId)
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
    fun declareAndRevisedTarget() = apiTest { client ->
        client.setUpProfile("asha")
        val match = client.createMatch("asha", overs = 5).match
        val (a1, a2) = match.teamA.players
        val (b1, b2) = match.teamB.players
        suspend fun event(event: MatchEvent) = client.post("/matches/${match.id}/events") {
            bearerAuth("test-asha")
            contentType(ContentType.Application.Json)
            setBody(event)
        }

        val four = Delivery(0, innings = 1, strikerId = a1.id, nonStrikerId = a2.id, bowlerId = b1.id, runsOffBat = 4)
        client.record("asha", match.id, four)
        // Strikers declare on 4; Royals then lose overs to rain and get a new target.
        assertEquals(HttpStatusCode.OK, event(MatchEvent.Declare(1, 1)).status)
        assertEquals(HttpStatusCode.Conflict, event(MatchEvent.ReviseTarget(2, 2, overs = 2)).status)
        val revised = event(MatchEvent.ReviseTarget(2, 2, overs = 2, target = 3)).body<MatchDetails>()
        assertEquals(MatchEvent.ReviseTarget(2, 2, overs = 2, target = 3), (revised.events.last() as MatchEvent.ReviseTarget).copy(timestamp = 0))
        assertEquals(InningsScore(TeamSide.B, 0, 0, 0, target = 3, maxBalls = 12), revised.match.innings.last())

        val three = Delivery(3, innings = 2, strikerId = b1.id, nonStrikerId = b2.id, bowlerId = a1.id, runsOffBat = 3)
        val won = client.record("asha", match.id, three).body<MatchDetails>()
        assertEquals(MatchResult(TeamSide.B, 1, MarginUnit.WICKETS), won.match.result)
    }

    @Test
    fun scoringHandsOverWithACode() = apiTest { client ->
        client.setUpProfile("asha")
        client.setUpProfile("dev")
        val match = client.createMatch("asha", MatchRules(wagonWheel = true)).match
        val (a1, a2) = match.teamA.players
        val b1 = match.teamB.players.first()

        // Only the scorer can make a code.
        assertEquals(HttpStatusCode.Forbidden, client.post("/matches/${match.id}/handover") { bearerAuth("test-dev") }.status)
        val code = client.post("/matches/${match.id}/handover") { bearerAuth("test-asha") }.body<HandoverCode>()
        assertEquals(6, code.code.length)

        val taken = client.post("/handover/${code.code}") { bearerAuth("test-dev") }.body<MatchDetails>()
        assertEquals("dev", taken.match.scorerUid)
        // Codes work once, and the old scorer can't score any more.
        assertEquals(HttpStatusCode.NotFound, client.post("/handover/${code.code}") { bearerAuth("test-dev") }.status)
        val four = Delivery(0, innings = 1, strikerId = a1.id, nonStrikerId = a2.id, bowlerId = b1.id, runsOffBat = 4, zone = FieldZone.COVER)
        assertEquals(HttpStatusCode.Forbidden, client.record("asha", match.id, four).status)
        val scored = client.record("dev", match.id, four).body<MatchDetails>()
        assertEquals(FieldZone.COVER, scored.deliveries.single().zone)
    }

    @Test
    fun unknownMatchIsNotFound() = apiTest { client ->
        assertEquals(HttpStatusCode.NotFound, client.get("/matches/nope") { bearerAuth("test-asha") }.status)
    }
}
