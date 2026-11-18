package com.harsh.desicrics

import com.harsh.desicrics.service.ImageService
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.server.request.contentType
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put

/** Uploads take the picture as the raw request body, typed by its Content-Type. */
fun Route.imageUploadRoutes(images: ImageService) {
    put("/me/photo") {
        call.respond(images.setProfilePhoto(call.user, call.request.contentType().withoutParameters().toString(), call.receive<ByteArray>()))
    }
    put("/teams/{id}/logo") {
        val type = call.request.contentType().withoutParameters().toString()
        call.respond(images.setTeamLogo(call.user, call.parameters["id"].orEmpty(), type, call.receive<ByteArray>()))
    }
}

/** Pictures are public and never change (a new upload gets a new ID), so they can be cached for good. */
fun Route.imageRoutes(images: ImageService) {
    get("/images/{id}") {
        val (type, bytes) = images.get(call.parameters["id"].orEmpty())
        call.response.header(HttpHeaders.CacheControl, "public, max-age=31536000, immutable")
        call.respondBytes(bytes, ContentType.parse(type))
    }
}
