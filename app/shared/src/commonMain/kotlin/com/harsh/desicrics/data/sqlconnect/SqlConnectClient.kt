package com.harsh.desicrics.data.sqlconnect

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer

/** Where the SQL Connect service lives; must match `dataconnect/dataconnect.yaml` and `connector.yaml`. */
data class SqlConnectConfig(
    val projectId: String,
    val apiKey: String,
    val location: String = "asia-south1",
    val serviceId: String = "desicrics",
    val connectorId: String = "app",
    /** Set to e.g. `http://10.0.2.2:9399` to use the local emulator. */
    val host: String = "https://firebasedataconnect.googleapis.com",
) {
    val connectorName: String
        get() = "projects/$projectId/locations/$location/services/$serviceId/connectors/$connectorId"
}

class SqlConnectException(message: String, val code: String? = null) : Exception(message)

/** Supplies the signed-in user's Firebase ID token; implemented by [com.harsh.desicrics.data.AuthRepository]. */
fun interface IdTokenProvider {
    suspend fun idToken(forceRefresh: Boolean): String?
}

/**
 * Calls the queries and mutations in `dataconnect/connector` over SQL Connect's REST API. There's no
 * Kotlin Multiplatform SDK, so this does what the generated SDKs do: POST the operation name and
 * variables, with the signed-in user's Firebase ID token in `X-Firebase-Auth-Token`.
 */
class SqlConnectClient(
    private val http: HttpClient,
    private val tokens: IdTokenProvider,
    private val config: SqlConnectConfig,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend inline fun <reified V, reified D> query(operationName: String, variables: V): D =
        execute("executeQuery", operationName, variables, serializer(), serializer())

    suspend inline fun <reified V, reified D> mutation(operationName: String, variables: V): D =
        execute("executeMutation", operationName, variables, serializer(), serializer())

    @PublishedApi
    internal suspend fun <V, D> execute(
        method: String,
        operationName: String,
        variables: V,
        variablesSerializer: KSerializer<V>,
        dataSerializer: KSerializer<D>,
    ): D {
        val body = OperationRequest(
            name = config.connectorName,
            operationName = operationName,
            variables = json.encodeToJsonElement(variablesSerializer, variables),
        )
        var response = post(method, body, forceRefreshToken = false)
        if (response.status == HttpStatusCode.Unauthorized) {
            // The cached ID token may have just expired.
            response = post(method, body, forceRefreshToken = true)
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            val error = runCatching { json.decodeFromString<HttpError>(text) }.getOrNull()
            throw SqlConnectException(error?.message ?: "Request failed (${response.status.value})", response.status.value.toString())
        }
        val result = json.decodeFromString<OperationResponse>(text)
        result.errors.firstOrNull()?.let { error ->
            throw SqlConnectException(error.cleanMessage, error.extensions?.code)
        }
        val data = result.data ?: throw SqlConnectException("Empty response for $operationName")
        return json.decodeFromJsonElement(dataSerializer, data)
    }

    private suspend fun post(method: String, body: OperationRequest, forceRefreshToken: Boolean): HttpResponse {
        val idToken = tokens.idToken(forceRefreshToken)
        return http.post("${config.host}/v1/${config.connectorName}:$method") {
            parameter("key", config.apiKey)
            contentType(ContentType.Application.Json)
            idToken?.let { header("X-Firebase-Auth-Token", it) }
            setBody(json.encodeToString(OperationRequest.serializer(), body))
        }
    }
}

@Serializable
private class OperationRequest(
    val name: String,
    val operationName: String,
    val variables: JsonElement = JsonObject(emptyMap()),
)

@Serializable
private class OperationResponse(
    val data: JsonElement? = null,
    val errors: List<GraphqlError> = emptyList(),
)

@Serializable
private class GraphqlError(
    val message: String,
    val extensions: GraphqlErrorExtensions? = null,
) {
    /** "Only the scorer can change this match (aborted)\n(rolled back)" → "Only the scorer can change this match" */
    val cleanMessage: String
        get() = message.lineSequence().first().removeSuffix(" (aborted)").ifBlank { "Request failed" }
}

@Serializable
private class GraphqlErrorExtensions(val code: String? = null)

@Serializable
private class HttpError(val message: String? = null)
