package com.harsh.desicrics

import com.harsh.desicrics.model.PlayerPage
import com.harsh.desicrics.model.PlayerSummary
import com.harsh.desicrics.model.PublicTeamPage
import com.harsh.desicrics.model.SearchResults
import com.harsh.desicrics.model.TeamSummary
import com.harsh.desicrics.model.TodayFeed
import com.harsh.desicrics.service.SocialService
import com.harsh.desicrics.service.StatsService
import com.harsh.desicrics.service.TeamService
import com.harsh.desicrics.service.TournamentService
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

/**
 * Read-only browsing for the web app (docs/web.md), with no sign-in: matches, tournaments, search, teams and
 * players. Account IDs and the public team and player codes are left out, as are join codes and requests.
 */
fun Route.publicBrowseRoutes(social: SocialService, teams: TeamService, tournaments: TournamentService, stats: StatsService) {
    route("/public") {
        get("/feed") { call.respond(social.publicFeed(call.request.queryParameters["city"].orEmpty()).forPublic()) }
        get("/matches") {
            val q = call.request.queryParameters
            call.respond(social.publicPage(q["status"], q["before"], q["id"], q["city"].orEmpty()).map { it.forPublic() })
        }
        get("/tournaments") { call.respond(tournaments.browse(call.request.queryParameters["city"].orEmpty())) }
        get("/search") { call.respond(social.search(call.request.queryParameters["q"].orEmpty()).forPublic()) }
        get("/teams/{id}") { call.respond(teams.publicPage(call.parameters["id"].orEmpty()).forPublic()) }
        get("/players/{id}") { call.respond(social.publicPlayerPage(call.parameters["id"].orEmpty()).forPublic()) }
        get("/players/{id}/stats") {
            val query = call.request.queryParameters
            call.respond(stats.career(call.parameters["id"].orEmpty(), query.season(), query.format()))
        }
    }
}

private fun TodayFeed.forPublic() = TodayFeed(
    live = live.map { it.forPublic() },
    upcoming = upcoming.map { it.forPublic() },
    recent = recent.map { it.forPublic() },
)

private fun PlayerSummary.forPublic() = copy(code = null)

private fun TeamSummary.forPublic() = copy(code = "")

private fun SearchResults.forPublic() = SearchResults(
    teams = teams.map { it.forPublic() },
    players = players.map { it.forPublic() },
    matches = matches.map { it.forPublic() },
)

private fun PlayerPage.forPublic() = copy(player = player.forPublic(), teams = teams.map { it.forPublic() }, matches = matches.map { it.forPublic() })

private fun PublicTeamPage.forPublic() = copy(
    team = team.copy(members = team.members.map { it.copy(player = it.player.forPublic()) }),
    matches = matches.map { it.forPublic() },
)
