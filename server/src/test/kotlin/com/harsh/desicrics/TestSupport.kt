package com.harsh.desicrics

import com.harsh.desicrics.api.CreateMatchRequest
import com.harsh.desicrics.api.MatchDetails
import com.harsh.desicrics.api.SquadPlayer
import com.harsh.desicrics.api.SquadRequest
import com.harsh.desicrics.api.UpdateProfileRequest
import com.harsh.desicrics.auth.FirebasePrincipal
import com.harsh.desicrics.auth.TokenVerifier
import com.harsh.desicrics.live.LoggingPushSender
import com.harsh.desicrics.live.PushSender
import com.harsh.desicrics.model.Delivery
import com.harsh.desicrics.model.MatchRules
import com.harsh.desicrics.model.PlayerRole
import com.harsh.desicrics.model.TeamSide
import com.harsh.desicrics.model.TossDecision
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.sql.DriverManager
import java.util.UUID
import kotlinx.serialization.json.Json

/**
 * Tokens look like "test-<uid>" so tests can act as any user without Firebase. "test-<uid>.<phone>" also
 * carries a verified phone number, as a phone sign-in would.
 */
private val fakeTokens = TokenVerifier { token ->
    token.removePrefix("test-").takeIf { it != token }?.let {
        FirebasePrincipal(uid = it.substringBefore('.'), phone = it.substringAfter('.', "").ifEmpty { null })
    }
}

/**
 * Set TEST_POSTGRES_URL (e.g. jdbc:postgresql://localhost:55432/desicrics, user and password "desicrics")
 * to run the suite against real Postgres instead of H2, each test in a schema of its own.
 */
private val testPostgres: String? = System.getenv("TEST_POSTGRES_URL")

/** A fresh, empty database for one test. */
internal fun freshDatabase(): DatabaseConfig {
    val postgres = testPostgres ?: return DatabaseConfig(
        url = "jdbc:h2:mem:${UUID.randomUUID()};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
    )
    val schema = "t_" + UUID.randomUUID().toString().replace("-", "")
    DriverManager.getConnection(postgres, "desicrics", "desicrics").use { it.createStatement().execute("CREATE SCHEMA $schema") }
    return DatabaseConfig(url = "$postgres?currentSchema=$schema", user = "desicrics", password = "desicrics", maxPoolSize = 2)
}

/** A fresh database per test. Push messages go to [push]; the client can open WebSockets. */
fun apiTest(push: PushSender = LoggingPushSender, block: suspend ApplicationTestBuilder.(HttpClient) -> Unit) = testApplication {
    val config = ServerConfig(database = freshDatabase())
    application { module(config, fakeTokens, push) }
    block(
        createClient {
            install(ContentNegotiation) { json() }
            install(WebSockets) { contentConverter = KotlinxWebsocketSerializationConverter(Json { ignoreUnknownKeys = true }) }
        },
    )
}

/** Keeps every push message instead of sending it. */
class RecordingPushSender : PushSender {
    val sent = mutableListOf<String>()
    val data = mutableListOf<Map<String, String>>()

    override suspend fun send(tokens: List<String>, title: String, body: String, data: Map<String, String>): List<String> {
        sent += body
        return emptyList()
    }

    override suspend fun sendData(tokens: List<String>, data: Map<String, String>): List<String> {
        this.data += data
        return emptyList()
    }
}

fun HttpRequestBuilder.jsonBody(body: Any) {
    contentType(ContentType.Application.Json)
    setBody(body)
}

suspend fun HttpClient.setUpProfile(uid: String, name: String = "Asha", phone: String = "+91 98765 43210") =
    put("/me") {
        bearerAuth("test-$uid")
        jsonBody(UpdateProfileRequest(name = name, phone = phone, role = PlayerRole.ALL_ROUNDER))
    }

fun squad(name: String, vararg players: String) = SquadRequest(name, players.map { SquadPlayer(it) })

suspend fun HttpClient.createMatch(
    uid: String,
    rules: MatchRules = MatchRules(),
    overs: Int = 1,
    teamA: SquadRequest = squad("Strikers", "Asha", "Bilal", "Chetan"),
    teamB: SquadRequest = squad("Royals", "Dev", "Esha"),
): MatchDetails =
    post("/matches") {
        bearerAuth("test-$uid")
        jsonBody(CreateMatchRequest(teamA, teamB, oversPerInnings = overs, tossWinner = TeamSide.A, tossDecision = TossDecision.BAT, rules = rules))
    }.body()

suspend fun HttpClient.record(uid: String, matchId: String, delivery: Delivery) =
    post("/matches/$matchId/deliveries") {
        bearerAuth("test-$uid")
        jsonBody(delivery)
    }
