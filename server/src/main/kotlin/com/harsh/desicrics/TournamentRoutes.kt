package com.harsh.desicrics
import com.harsh.desicrics.api.EndBeforeStartRequest

import com.harsh.desicrics.api.AddOrganiserRequest
import com.harsh.desicrics.api.AddTournamentPlayerRequest
import com.harsh.desicrics.api.CreateTournamentRequest
import com.harsh.desicrics.api.CustomFixtureRequest
import com.harsh.desicrics.api.EnterTeamRequest
import com.harsh.desicrics.api.EntrySettingsRequest
import com.harsh.desicrics.api.PlacePlayerRequest
import com.harsh.desicrics.api.ReviewEntryRequest
import com.harsh.desicrics.api.ScheduleRequest
import com.harsh.desicrics.api.StartFixtureRequest
import com.harsh.desicrics.service.TournamentService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

fun Route.tournamentRoutes(tournaments: TournamentService) {
    route("/tournaments") {
        get("/mine") { call.respond(tournaments.mine(call.user)) }
        post { call.respond(HttpStatusCode.Created, tournaments.create(call.user, call.receive<CreateTournamentRequest>())) }
        route("/{id}") {
            get { call.respond(tournaments.details(call.parameters["id"].orEmpty())) }
            post("/teams") { call.respond(tournaments.enterTeam(call.user, call.parameters["id"].orEmpty(), call.receive<EnterTeamRequest>().teamId)) }
            put("/teams/{teamId}") {
                val review = call.receive<ReviewEntryRequest>()
                call.respond(tournaments.review(call.user, call.parameters["id"].orEmpty(), call.parameters["teamId"].orEmpty(), review.confirm))
            }
            delete("/teams/{teamId}") {
                call.respond(tournaments.removeTeam(call.user, call.parameters["id"].orEmpty(), call.parameters["teamId"].orEmpty()))
            }
            put("/entry") { call.respond(tournaments.setEntry(call.user, call.parameters["id"].orEmpty(), call.receive<EntrySettingsRequest>())) }
            route("/players") {
                post { call.respond(tournaments.addPlayer(call.user, call.parameters["id"].orEmpty(), call.receive<AddTournamentPlayerRequest>())) }
                put("/{profileId}") {
                    val request = call.receive<PlacePlayerRequest>()
                    call.respond(tournaments.placePlayer(call.user, call.parameters["id"].orEmpty(), call.parameters["profileId"].orEmpty(), request))
                }
                delete("/{profileId}") {
                    call.respond(tournaments.removePlayer(call.user, call.parameters["id"].orEmpty(), call.parameters["profileId"].orEmpty()))
                }
            }
            post("/organisers") { call.respond(tournaments.addOrganiser(call.user, call.parameters["id"].orEmpty(), call.receive<AddOrganiserRequest>())) }
            post("/start") { call.respond(tournaments.start(call.user, call.parameters["id"].orEmpty())) }
            post("/fixtures") { call.respond(tournaments.addFixture(call.user, call.parameters["id"].orEmpty(), call.receive<CustomFixtureRequest>())) }
        }
    }
    put("/matches/{id}/schedule") {
        call.respond(tournaments.schedule(call.user, call.parameters["id"].orEmpty(), call.receive<ScheduleRequest>().scheduledAt))
    }
    post("/matches/{id}/start") {
        call.respond(tournaments.startFixture(call.user, call.parameters["id"].orEmpty(), call.receive<StartFixtureRequest>()))
    }
    // A walkover or call-off for a match that never starts. During play the scorer records an end_match event.
    post("/matches/{id}/end") {
        call.respond(tournaments.endBeforeStart(call.user, call.parameters["id"].orEmpty(), call.receive<EndBeforeStartRequest>()))
    }
}
