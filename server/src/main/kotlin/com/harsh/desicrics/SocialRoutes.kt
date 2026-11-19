package com.harsh.desicrics

import com.harsh.desicrics.api.PushTokenRequest
import com.harsh.desicrics.live.LiveMatches
import com.harsh.desicrics.model.FollowType
import com.harsh.desicrics.service.ApiException
import com.harsh.desicrics.service.MatchService
import com.harsh.desicrics.service.SocialService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.websocket.sendSerialized
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.close
import kotlinx.coroutines.flow.onSubscription

fun Route.socialRoutes(social: SocialService) {
    get("/feed") { call.respond(social.feed(call.user)) }
    get("/feed/matches") {
        val q = call.request.queryParameters
        call.respond(social.page(q["status"], q["before"], q["id"]))
    }
    get("/search") { call.respond(social.search(call.request.queryParameters["q"].orEmpty())) }
    get("/players/{id}") { call.respond(social.playerPage(call.user, call.parameters["id"].orEmpty())) }
    route("/follows") {
        get { call.respond(social.follows(call.user)) }
        put("/{type}/{id}") {
            social.follow(call.user, call.followType(), call.parameters["id"].orEmpty())
            call.respond(HttpStatusCode.NoContent)
        }
        delete("/{type}/{id}") {
            social.unfollow(call.user, call.followType(), call.parameters["id"].orEmpty())
            call.respond(HttpStatusCode.NoContent)
        }
    }
    get("/notifications") { call.respond(social.notifications(call.user)) }
    post("/notifications/read") {
        social.markRead(call.user)
        call.respond(HttpStatusCode.NoContent)
    }
    put("/me/push-token") {
        social.savePushToken(call.user, call.receive<PushTokenRequest>())
        call.respond(HttpStatusCode.NoContent)
    }
}

/**
 * The live feed: the current scorecard straight away, then a new one after every change, until the
 * watcher goes away. Whole scorecards rather than changes, so a watcher can never drift out of step.
 */
fun Route.liveRoutes(matches: MatchService, live: LiveMatches) {
    webSocket("/matches/{id}/live") {
        val matchId = call.parameters["id"].orEmpty()
        try {
            // The current scorecard is read once the subscription is live, so no change slips between the two.
            live.updates(matchId)
                .onSubscription { emit(matches.details(matchId)) }
                .collect { sendSerialized(it) }
        } catch (e: ApiException) {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, e.message))
        }
    }
}

private fun RoutingCall.followType(): FollowType =
    runCatching { FollowType.valueOf(parameters["type"].orEmpty().uppercase()) }.getOrNull()
        ?: throw ApiException(HttpStatusCode.BadRequest, "Follow a team or a player")
