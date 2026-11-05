package com.harsh.desicrics.data

import com.harsh.desicrics.data.sqlconnect.IdTokenProvider
import com.harsh.desicrics.data.sqlconnect.SqlConnectClient
import com.harsh.desicrics.data.sqlconnect.SqlConnectConfig
import com.harsh.desicrics.data.sqlconnect.SqlConnectException
import com.harsh.desicrics.model.Delivery
import com.harsh.desicrics.model.MatchStatus
import com.harsh.desicrics.model.PlayerRole
import com.harsh.desicrics.model.TeamSide
import com.harsh.desicrics.model.TossDecision
import com.harsh.desicrics.model.UserProfile
import com.harsh.desicrics.scoring.ScoringEngine
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Runs the real repositories against the SQL Connect emulator. Skipped unless FDC_EMULATOR_HOST is set:
 *
 *   firebase emulators:exec --only dataconnect --project demo-desicrics \
 *     'FDC_EMULATOR_HOST=http://127.0.0.1:9399 ./gradlew :app:shared:testAndroidHostTest'
 */
class SqlConnectEmulatorTest {

    private val host = System.getenv("FDC_EMULATOR_HOST")
    private val http = HttpClient()

    /** The emulator accepts unsigned tokens, so any user can be impersonated in tests. */
    private fun client(uid: String) = SqlConnectClient(
        http = http,
        tokens = IdTokenProvider { emulatorToken(uid) },
        config = SqlConnectConfig(projectId = "demo-desicrics", apiKey = "fake-api-key", host = host),
    )

    @Test
    fun profileMatchAndScoringRoundTrip() = runTest {
        if (host == null) return@runTest
        val uid = "scorer-${kotlin.random.Random.nextInt(1_000_000)}"
        val users = UserRepository(client(uid))
        val matches = MatchRepository(client(uid))

        assertNull(users.getProfile())
        users.saveProfile(UserProfile(uid = uid, name = "Asha", phone = "+919876543210", city = "Pune", role = PlayerRole.ALL_ROUNDER))
        assertEquals("Asha", users.getProfile()?.name)

        val matchId = matches.createMatch(
            teamAName = "Strikers", teamAPlayers = listOf("Asha", "Bilal", "Chetan"),
            teamBName = "Royals", teamBPlayers = listOf("Dev", "Esha"),
            oversPerInnings = 1, tossWinner = TeamSide.A, tossDecision = TossDecision.BAT,
        )
        val created = assertNotNull(matches.observeMatch(matchId).first().getOrThrow())
        assertEquals(MatchStatus.IN_PROGRESS, created.match.status)
        assertEquals(listOf("Asha", "Bilal", "Chetan"), created.match.teamA.players.map { it.name })
        assertEquals(uid, created.match.createdBy)

        val (striker, nonStriker) = created.match.teamA.players
        val bowler = created.match.teamB.players.first()
        val ball = Delivery(seq = 0, innings = 1, strikerId = striker.id, nonStrikerId = nonStriker.id, bowlerId = bowler.id, runsOffBat = 4)
        val scored = ScoringEngine.compute(created.match, listOf(ball))
        matches.recordDelivery(created.match.copy(scoreSummary = scored.scoreSummary), ball)

        val afterBall = assertNotNull(matches.observeMatch(matchId).first().getOrThrow())
        assertEquals(listOf(ball), afterBall.deliveries)
        assertEquals("Strikers 4/0 (0.1)", afterBall.match.scoreSummary)
        assertTrue(matches.observeRecentMatches().first().getOrThrow().any { it.id == matchId })

        // Someone else can watch, but not score.
        val intruder = MatchRepository(client("intruder"))
        assertEquals(1, assertNotNull(intruder.observeMatch(matchId).first().getOrThrow()).deliveries.size)
        assertFailsWith<SqlConnectException> { intruder.recordDelivery(afterBall.match, ball.copy(seq = 1)) }

        // A double-tap can't record the same ball twice.
        assertFailsWith<SqlConnectException> { matches.recordDelivery(afterBall.match, ball) }

        matches.undoDelivery(afterBall.match.copy(scoreSummary = ""), ball)
        assertTrue(assertNotNull(matches.observeMatch(matchId).first().getOrThrow()).deliveries.isEmpty())
    }

    private fun emulatorToken(uid: String): String {
        val now = System.currentTimeMillis() / 1000
        val header = """{"alg":"none","typ":"JWT"}"""
        val payload = """{"iss":"https://securetoken.google.com/demo-desicrics","aud":"demo-desicrics",""" +
            """"auth_time":$now,"iat":$now,"exp":${now + 3600},"sub":"$uid","user_id":"$uid",""" +
            """"firebase":{"sign_in_provider":"google.com","identities":{}}}"""
        val encode = { s: String -> Base64.UrlSafe.encode(s.encodeToByteArray()).trimEnd('=') }
        return "${encode(header)}.${encode(payload)}."
    }
}
