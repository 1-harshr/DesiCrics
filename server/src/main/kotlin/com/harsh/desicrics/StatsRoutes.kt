package com.harsh.desicrics

import com.harsh.desicrics.model.FormatFilter
import com.harsh.desicrics.model.LeaderboardMetric
import com.harsh.desicrics.model.LeaderboardScope
import com.harsh.desicrics.service.ApiException
import com.harsh.desicrics.service.StatsService
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.statsRoutes(stats: StatsService) {
    get("/players/{id}/stats") {
        val query = call.request.queryParameters
        call.respond(stats.career(call.parameters["id"].orEmpty(), query.season(), query.format()))
    }
    get("/stats/compare") {
        val query = call.request.queryParameters
        call.respond(stats.compare(query["a"].orEmpty(), query["b"].orEmpty(), query.season(), query.format()))
    }
    get("/leaderboards") {
        val query = call.request.queryParameters
        call.respond(
            stats.leaderboard(
                call.user,
                query.enum<LeaderboardScope>("scope") ?: LeaderboardScope.FRIENDS,
                query["id"],
                query.enum<LeaderboardMetric>("metric") ?: LeaderboardMetric.RUNS,
                query.season(),
            ),
        )
    }
}

internal fun Parameters.season(): Int? = get("season")?.toIntOrNull()

internal fun Parameters.format(): FormatFilter = enum<FormatFilter>("format") ?: FormatFilter.ALL

internal inline fun <reified T : Enum<T>> Parameters.enum(name: String): T? = get(name)?.let { value ->
    enumValues<T>().firstOrNull { it.name.equals(value, ignoreCase = true) }
        ?: throw ApiException(HttpStatusCode.BadRequest, "Unknown $name: $value")
}
