package com.harsh.desicrics

import com.harsh.desicrics.api.AddPlayerRequest
import com.harsh.desicrics.api.CreateMatchRequest
import com.harsh.desicrics.api.UpdateProfileRequest
import com.harsh.desicrics.auth.FirebasePrincipal
import com.harsh.desicrics.model.Delivery
import com.harsh.desicrics.model.MatchEvent
import com.harsh.desicrics.service.ApiException
import com.harsh.desicrics.service.MatchService
import com.harsh.desicrics.service.ProfileService
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

/** Routes are thin: auth, (de)serialisation and status codes. Rules live in the services. */
fun Route.profileRoutes(profiles: ProfileService) {
    route("/me") {
        get {
            val profile = profiles.get(call.user) ?: throw ApiException(HttpStatusCode.NotFound, "No profile yet")
            call.respond(profile)
        }
        put {
            call.respond(profiles.update(call.user, call.receive<UpdateProfileRequest>()))
        }
    }
}

fun Route.matchRoutes(matches: MatchService) {
    route("/matches") {
        get {
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 30
            call.respond(matches.recent(limit))
        }
        post {
            call.respond(HttpStatusCode.Created, matches.create(call.user, call.receive<CreateMatchRequest>()))
        }
        route("/{id}") {
            get {
                call.respond(matches.details(call.matchId))
            }
            post("/deliveries") {
                call.respond(matches.recordDelivery(call.user, call.matchId, call.receive<Delivery>()))
            }
            post("/events") {
                call.respond(matches.recordEvent(call.user, call.matchId, call.receive<MatchEvent>()))
            }
            post("/players") {
                call.respond(matches.addPlayer(call.user, call.matchId, call.receive<AddPlayerRequest>()))
            }
            delete("/last") {
                call.respond(matches.undoLast(call.user, call.matchId))
            }
            post("/handover") {
                call.respond(matches.startHandover(call.user, call.matchId))
            }
        }
    }
    post("/handover/{code}") {
        val code = call.parameters["code"] ?: throw ApiException(HttpStatusCode.BadRequest, "Missing code")
        call.respond(matches.takeHandover(call.user, code))
    }
}

private val RoutingCall.user: FirebasePrincipal
    get() = principal<FirebasePrincipal>() ?: throw ApiException(HttpStatusCode.Unauthorized, "Sign in required")

private val RoutingCall.matchId: String
    get() = parameters["id"] ?: throw ApiException(HttpStatusCode.BadRequest, "Missing match ID")
