package com.harsh.desicrics

import com.harsh.desicrics.api.AddMemberRequest
import com.harsh.desicrics.api.ClaimRequest
import com.harsh.desicrics.api.SetRoleRequest
import com.harsh.desicrics.api.SupportRequest
import com.harsh.desicrics.api.TeamRequest
import com.harsh.desicrics.service.AccountService
import com.harsh.desicrics.service.ApiException
import com.harsh.desicrics.service.MatchService
import com.harsh.desicrics.service.PlayerService
import com.harsh.desicrics.service.TeamService
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

fun Route.teamRoutes(teams: TeamService) {
    route("/teams") {
        get("/mine") { call.respond(teams.mine(call.user)) }
        post { call.respond(HttpStatusCode.Created, teams.create(call.user, call.receive<TeamRequest>())) }
        post("/join/{code}") { call.respond(teams.join(call.user, call.param("code"))) }
        route("/{id}") {
            get { call.respond(teams.get(call.user, call.param("id"))) }
            put { call.respond(teams.update(call.user, call.param("id"), call.receive<TeamRequest>())) }
            post("/members") { call.respond(teams.addMember(call.user, call.param("id"), call.receive<AddMemberRequest>())) }
            put("/members/{profileId}") {
                call.respond(teams.setRole(call.user, call.param("id"), call.param("profileId"), call.receive<SetRoleRequest>().role))
            }
            delete("/members/{profileId}") { call.respond(teams.removeMember(call.user, call.param("id"), call.param("profileId"))) }
            post("/join-code") { call.respond(teams.newJoinCode(call.user, call.param("id"))) }
            route("/join-requests") {
                post { call.respond(teams.requestToJoin(call.user, call.param("id"))) }
                delete { call.respond(teams.cancelJoinRequest(call.user, call.param("id"))) }
                post("/{profileId}") { call.respond(teams.acceptJoinRequest(call.user, call.param("id"), call.param("profileId"))) }
                delete("/{profileId}") { call.respond(teams.declineJoinRequest(call.user, call.param("id"), call.param("profileId"))) }
            }
            get("/insights") { call.respond(teams.insights(call.user, call.param("id"))) }
            get("/head-to-head/{otherId}") { call.respond(teams.headToHead(call.param("id"), call.param("otherId"))) }
        }
    }
}

fun Route.playerRoutes(players: PlayerService) {
    route("/players") {
        get("/search") { call.respond(players.search(call.request.queryParameters["q"].orEmpty())) }
        post("/placeholder") { call.respond(players.placeholder(call.user, call.receive<AddMemberRequest>())) }
        get("/claimable") { call.respond(players.claimable(call.user)) }
        post("/claim") { call.respond(players.claim(call.user, call.receive<ClaimRequest>())) }
    }
}

fun Route.accountRoutes(account: AccountService, matches: MatchService) {
    get("/me/matches") { call.respond(matches.mine(call.user)) }
    delete("/me") {
        account.delete(call.user)
        call.respond(HttpStatusCode.NoContent)
    }
    post("/support") {
        account.contactSupport(call.user, call.receive<SupportRequest>())
        call.respond(HttpStatusCode.NoContent)
    }
}

private fun RoutingCall.param(name: String): String =
    parameters[name] ?: throw ApiException(HttpStatusCode.BadRequest, "Missing $name")
