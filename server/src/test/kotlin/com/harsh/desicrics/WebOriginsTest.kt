package com.harsh.desicrics

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WebOriginsTest {

    private val webApp = "http://localhost:5173"

    @Test
    fun `the web app's origin may read the public API`() = apiTest(webOrigins = listOf(webApp)) { client ->
        val response = client.get("/public/matches/nope") { header(HttpHeaders.Origin, webApp) }
        assertEquals(webApp, response.headers[HttpHeaders.AccessControlAllowOrigin])
    }

    @Test
    fun `other origins are not allowed`() = apiTest(webOrigins = listOf(webApp)) { client ->
        val response = client.get("/public/matches/nope") { header(HttpHeaders.Origin, "https://example.com") }
        assertNull(response.headers[HttpHeaders.AccessControlAllowOrigin])
    }
}
