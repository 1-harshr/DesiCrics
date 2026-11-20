package com.harsh.desicrics

import com.harsh.desicrics.cities.Cities
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.cityRoutes(cities: Cities = Cities.India) {
    get("/cities") { call.respond(cities.suggest(call.request.queryParameters["q"].orEmpty().take(100))) }
}
