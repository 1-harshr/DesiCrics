package com.harsh.desicrics

import com.harsh.desicrics.api.ApiError
import com.harsh.desicrics.auth.FirebaseTokenVerifier
import com.harsh.desicrics.auth.TokenVerifier
import com.harsh.desicrics.db.DatabaseFactory
import com.harsh.desicrics.service.ApiException
import com.harsh.desicrics.service.AccountService
import com.harsh.desicrics.service.MatchService
import com.harsh.desicrics.service.PlayerService
import com.harsh.desicrics.service.TeamService
import com.harsh.desicrics.service.ProfileService
import com.harsh.desicrics.store.ExposedMatchStore
import com.harsh.desicrics.store.ExposedPlayerStore
import com.harsh.desicrics.store.ExposedSupportStore
import com.harsh.desicrics.store.ExposedTeamStore
import com.harsh.desicrics.store.ExposedUserStore
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.bearer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

fun main() {
    val config = ServerConfig.fromEnv()
    embeddedServer(Netty, port = config.port, host = "0.0.0.0") { module(config) }
        .start(wait = true)
}

fun Application.module(
    config: ServerConfig = ServerConfig.fromEnv(),
    tokenVerifier: TokenVerifier = FirebaseTokenVerifier(config.firebaseProjectId),
) {
    val db = DatabaseFactory.connect(config.database)
    monitor.subscribe(ApplicationStopped) { db.close() }
    val database = db.database
    val users = ExposedUserStore(database)
    val players = ExposedPlayerStore(database)
    val teamStore = ExposedTeamStore(database)
    val matchStore = ExposedMatchStore(database)
    val profiles = ProfileService(users, players)
    val matches = MatchService(matchStore, users, players, teamStore)
    val teams = TeamService(teamStore, players, matchStore, users)
    val playerService = PlayerService(players, users)
    val account = AccountService(players, teamStore, ExposedSupportStore(database))
    launch { matches.backfillInningsScores() }

    install(CallLogging)
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
    install(StatusPages) {
        exception<ApiException> { call, e -> call.respond(e.status, ApiError(e.message)) }
        exception<BadRequestException> { call, _ -> call.respond(HttpStatusCode.BadRequest, ApiError("Malformed request")) }
        exception<Throwable> { call, e ->
            call.application.environment.log.error("Unhandled error", e)
            call.respond(HttpStatusCode.InternalServerError, ApiError("Something went wrong on our side"))
        }
    }
    install(Authentication) {
        // The app sends its Firebase ID token as `Authorization: Bearer <token>`.
        bearer("firebase") {
            authenticate { credential -> tokenVerifier.verify(credential.token) }
        }
    }

    routing {
        get("/") { call.respondText("DesiCrics API") }
        publicRoutes()
        authenticate("firebase") {
            profileRoutes(profiles)
            matchRoutes(matches)
            teamRoutes(teams)
            playerRoutes(playerService)
            accountRoutes(account, matches)
        }
    }
}
