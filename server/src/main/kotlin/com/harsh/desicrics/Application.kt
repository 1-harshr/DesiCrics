package com.harsh.desicrics

import com.harsh.desicrics.api.ApiError
import com.harsh.desicrics.auth.FirebaseTokenVerifier
import com.harsh.desicrics.auth.TokenVerifier
import com.harsh.desicrics.db.DatabaseFactory
import com.harsh.desicrics.service.ApiException
import com.harsh.desicrics.live.FcmPushSender
import com.harsh.desicrics.live.LiveAuctions
import com.harsh.desicrics.live.LiveMatches
import com.harsh.desicrics.live.LoggingPushSender
import com.harsh.desicrics.live.Notifier
import com.harsh.desicrics.live.PushSender
import com.harsh.desicrics.service.AccountService
import com.harsh.desicrics.service.AuctionService
import com.harsh.desicrics.service.CommunityService
import com.harsh.desicrics.service.ImageService
import com.harsh.desicrics.service.MatchService
import com.harsh.desicrics.service.PlayerService
import com.harsh.desicrics.service.SocialService
import com.harsh.desicrics.service.StatsProjector
import com.harsh.desicrics.service.StatsService
import com.harsh.desicrics.service.TournamentService
import com.harsh.desicrics.service.TeamService
import com.harsh.desicrics.service.ProfileService
import com.harsh.desicrics.store.ExposedAuctionStore
import com.harsh.desicrics.store.ExposedCommunityStore
import com.harsh.desicrics.store.ExposedImageStore
import com.harsh.desicrics.store.ExposedMatchStore
import com.harsh.desicrics.store.ExposedPlayerStore
import com.harsh.desicrics.store.ExposedSocialStore
import com.harsh.desicrics.store.ExposedStatsStore
import com.harsh.desicrics.store.ExposedSupportStore
import com.harsh.desicrics.store.ExposedTeamStore
import com.harsh.desicrics.store.ExposedTournamentStore
import com.harsh.desicrics.store.ExposedUserStore
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import com.harsh.desicrics.cities.standardiseCities
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
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
    pushSender: PushSender = config.firebaseServiceAccount?.let { FcmPushSender(it, config.firebaseProjectId) } ?: LoggingPushSender,
) {
    val db = DatabaseFactory.connect(config.database)
    monitor.subscribe(ApplicationStopped) { db.close() }
    val database = db.database
    val users = ExposedUserStore(database)
    val players = ExposedPlayerStore(database)
    val teamStore = ExposedTeamStore(database)
    val matchStore = ExposedMatchStore(database)
    val profiles = ProfileService(users, players)
    val socialStore = ExposedSocialStore(database)
    val live = LiveMatches()
    val matches = MatchService(matchStore, users, players, teamStore)
    val social = SocialService(socialStore, matchStore, teamStore, players)
    val notifier = Notifier(socialStore, pushSender)
    val statsStore = ExposedStatsStore(database)
    val liveAuctions = LiveAuctions()
    val auctions = AuctionService(
        ExposedAuctionStore(database), ExposedTournamentStore(database), teamStore, players, users, liveAuctions,
        scope = this, secondMillis = config.auctionSecondMillis,
    )
    val tournaments = TournamentService(
        ExposedTournamentStore(database), matchStore, matches, teamStore, players, users, statsStore,
        onRescheduled = notifier::rescheduled, notify = notifier::tournamentNotice, onPoolJoined = auctions::addFromTournament,
    )
    // The live feed first, so watchers see a ball before anything slower runs.
    matches.listen(live)
    matches.listen(notifier)
    val projector = StatsProjector(statsStore, matchStore)
    matches.listen(projector)
    // After the projector, so a finished fixture's stats are in before the next round is made.
    matches.listen(tournaments)
    launch { projector.backfill() }
    val teams = TeamService(teamStore, players, matchStore, users, notify = notifier::teamNotice)
    val community = CommunityService(ExposedCommunityStore(database), players, teamStore, users, matchStore, statsStore)
    val images = ImageService(ExposedImageStore(database), users, teamStore, teams)
    val playerService = PlayerService(players, users)
    val account = AccountService(players, teamStore, ExposedSupportStore(database))
    launch { matches.backfillInningsScores() }
    launch {
        runCatching { standardiseCities(database) }
            .onSuccess { if (it > 0) environment.log.info("Standardised $it city names") }
            .onFailure { environment.log.warn("Standardising cities failed", it) }
    }
    launch { auctions.resumeClocks() }
    launch {
        while (isActive) {
            runCatching { tournaments.remindClosing() }.onFailure { environment.log.warn("Entry reminders failed", it) }
            delay(config.entryReminderEveryMillis)
        }
    }

    install(CallLogging)
    install(WebSockets) {
        contentConverter = KotlinxWebsocketSerializationConverter(Json { ignoreUnknownKeys = true })
        pingPeriod = 20.seconds
    }
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
        publicRoutes(matches, tournaments, community, config.androidCertFingerprints)
        imageRoutes(images)
        authenticate("firebase") {
            profileRoutes(profiles)
            matchRoutes(matches)
            teamRoutes(teams)
            playerRoutes(playerService)
            accountRoutes(account, matches)
            socialRoutes(social)
            liveRoutes(matches, live)
            tournamentRoutes(tournaments)
            imageUploadRoutes(images)
            communityRoutes(community)
            cityRoutes()
            statsRoutes(StatsService(statsStore, players, users, teamStore, socialStore))
            auctionRoutes(auctions, liveAuctions)
        }
    }
}
