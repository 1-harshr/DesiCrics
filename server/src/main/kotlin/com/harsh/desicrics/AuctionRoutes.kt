package com.harsh.desicrics

import com.harsh.desicrics.api.AddAuctionPlayerRequest
import com.harsh.desicrics.api.AuctionPlayerRequest
import com.harsh.desicrics.api.BidRequest
import com.harsh.desicrics.api.CreateAuctionRequest
import com.harsh.desicrics.api.NextPlayerRequest
import com.harsh.desicrics.api.ReverseSaleRequest
import com.harsh.desicrics.live.LiveAuctions
import com.harsh.desicrics.model.AuctionSettings
import com.harsh.desicrics.service.ApiException
import com.harsh.desicrics.service.AuctionService
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

/** Player auctions; see `core/.../api/AuctionApi.kt` for the routes. */
fun Route.auctionRoutes(auctions: AuctionService, live: LiveAuctions) {
    route("/tournaments/{id}/auction") {
        get { call.respond(auctions.forTournament(call.parameters["id"].orEmpty())) }
        post {
            val request = call.receiveOr(CreateAuctionRequest())
            call.respond(HttpStatusCode.Created, auctions.create(call.user, call.parameters["id"].orEmpty(), request))
        }
    }
    route("/auctions/{id}") {
        get { call.respond(auctions.details(call.auctionId)) }
        put("/settings") { call.respond(auctions.updateSettings(call.user, call.auctionId, call.receive<AuctionSettings>())) }
        route("/players") {
            post { call.respond(auctions.addPlayer(call.user, call.auctionId, call.receive<AddAuctionPlayerRequest>())) }
            put("/{profileId}") {
                call.respond(auctions.updatePlayer(call.user, call.auctionId, call.profileId, call.receive<AuctionPlayerRequest>()))
            }
            delete("/{profileId}") { call.respond(auctions.removePlayer(call.user, call.auctionId, call.profileId)) }
        }
        route("/requests") {
            post { call.respond(auctions.request(call.user, call.auctionId)) }
            delete { call.respond(auctions.withdrawRequest(call.user, call.auctionId)) }
        }
        post("/start") { call.respond(auctions.start(call.user, call.auctionId)) }
        post("/next") {
            val request = call.receiveOr(NextPlayerRequest())
            call.respond(auctions.next(call.user, call.auctionId, request.profileId))
        }
        post("/bids") { call.respond(auctions.bid(call.user, call.auctionId, call.receive<BidRequest>())) }
        post("/sold") { call.respond(auctions.sold(call.user, call.auctionId)) }
        post("/unsold") { call.respond(auctions.unsold(call.user, call.auctionId)) }
        post("/undo") { call.respond(auctions.undo(call.user, call.auctionId)) }
        post("/pause") { call.respond(auctions.pause(call.user, call.auctionId)) }
        post("/resume") { call.respond(auctions.resume(call.user, call.auctionId)) }
        post("/reverse") { call.respond(auctions.reverseSale(call.user, call.auctionId, call.receive<ReverseSaleRequest>().profileId)) }
        post("/finish") { call.respond(auctions.finish(call.user, call.auctionId)) }
    }
    webSocket("/auctions/{id}/live") {
        val auctionId = call.parameters["id"].orEmpty()
        try {
            // The current state is read once the subscription is live, so no change slips between the two.
            live.updates(auctionId)
                .onSubscription { emit(auctions.details(auctionId)) }
                .collect { sendSerialized(it) }
        } catch (e: ApiException) {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, e.message))
        }
    }
}

private val RoutingCall.auctionId: String
    get() = parameters["id"] ?: throw ApiException(HttpStatusCode.BadRequest, "Missing auction ID")

private val RoutingCall.profileId: String
    get() = parameters["profileId"] ?: throw ApiException(HttpStatusCode.BadRequest, "Missing player")

/** The body, or [default] when the request has none (no content type), so callers can leave it out. */
private suspend inline fun <reified T : Any> RoutingCall.receiveOr(default: T): T =
    if (request.headers["Content-Type"] == null) default else receive<T>()
