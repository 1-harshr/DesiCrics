package com.harsh.desicrics

import io.ktor.client.request.header
import io.ktor.client.request.options
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WebOriginsTest {

    private val webApp = "http://localhost:5173"

    @Test
    fun `the web app's origin may call the API with a token`() = apiTest(webOrigins = listOf(webApp)) { client ->
        val response = client.options("/me") {
            header(HttpHeaders.Origin, webApp)
            header(HttpHeaders.AccessControlRequestMethod, HttpMethod.Put.value)
            header(HttpHeaders.AccessControlRequestHeaders, "authorization,content-type")
        }
        assertEquals(webApp, response.headers[HttpHeaders.AccessControlAllowOrigin])
    }

    @Test
    fun `other origins are not allowed`() = apiTest(webOrigins = listOf(webApp)) { client ->
        val response = client.options("/me") {
            header(HttpHeaders.Origin, "https://example.com")
            header(HttpHeaders.AccessControlRequestMethod, HttpMethod.Get.value)
        }
        assertNull(response.headers[HttpHeaders.AccessControlAllowOrigin])
    }
}
