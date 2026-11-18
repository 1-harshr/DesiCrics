package com.harsh.desicrics

import com.harsh.desicrics.api.DirectoryRequest
import com.harsh.desicrics.api.LookingForRequest
import com.harsh.desicrics.api.RespondRequest
import com.harsh.desicrics.model.DirectoryKind
import com.harsh.desicrics.model.LookingForKind
import com.harsh.desicrics.service.CommunityService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

fun Route.communityRoutes(community: CommunityService) {
    route("/looking-for") {
        get {
            val query = call.request.queryParameters
            val kind = query["kind"]?.let { k -> LookingForKind.entries.firstOrNull { it.name.equals(k, ignoreCase = true) } }
            call.respond(community.open(query["city"].orEmpty(), kind))
        }
        get("/mine") { call.respond(community.mine(call.user)) }
        post { call.respond(HttpStatusCode.Created, community.post(call.user, call.receive<LookingForRequest>())) }
        post("/{id}/responses") { call.respond(community.respond(call.user, call.parameters["id"].orEmpty(), call.receive<RespondRequest>())) }
        post("/{id}/close") { call.respond(community.close(call.user, call.parameters["id"].orEmpty())) }
    }
    route("/directory") {
        get {
            val query = call.request.queryParameters
            val kind = query["kind"]?.let { k -> DirectoryKind.entries.firstOrNull { it.name.equals(k, ignoreCase = true) } }
            call.respond(community.directory(query["city"].orEmpty(), kind))
        }
        post { call.respond(HttpStatusCode.Created, community.addEntry(call.user, call.receive<DirectoryRequest>())) }
        delete("/{id}") {
            community.removeEntry(call.user, call.parameters["id"].orEmpty())
            call.respond(HttpStatusCode.NoContent)
        }
    }
    get("/rankings/teams") { call.respond(community.teamRankings(call.request.queryParameters["city"].orEmpty())) }
    get("/rankings/players") { call.respond(community.playerRankings(call.request.queryParameters["city"].orEmpty())) }
}
