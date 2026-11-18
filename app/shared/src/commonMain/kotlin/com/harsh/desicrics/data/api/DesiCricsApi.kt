package com.harsh.desicrics.data.api

import com.harsh.desicrics.api.AddMemberRequest
import com.harsh.desicrics.api.AddPlayerRequest
import com.harsh.desicrics.api.ClaimRequest
import com.harsh.desicrics.api.SetRoleRequest
import com.harsh.desicrics.api.SupportRequest
import com.harsh.desicrics.api.TeamRequest
import com.harsh.desicrics.model.HeadToHead
import com.harsh.desicrics.model.PlayerSummary
import com.harsh.desicrics.model.SavedTeam
import com.harsh.desicrics.model.TeamRole
import com.harsh.desicrics.model.TeamSummary
import com.harsh.desicrics.api.ApiError
import com.harsh.desicrics.api.CreateMatchRequest
import com.harsh.desicrics.api.HandoverCode
import com.harsh.desicrics.api.MatchDetails
import com.harsh.desicrics.api.UpdateProfileRequest
import com.harsh.desicrics.model.Delivery
import com.harsh.desicrics.model.Match
import com.harsh.desicrics.model.MatchEvent
import com.harsh.desicrics.model.UserProfile
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess

/**
 * Where the DesiCrics server is. Android emulators reach the host machine at 10.0.2.2. [publicUrl] is the
 * address people outside the app open (join links, public scorecards); it's the same server.
 */
data class ApiConfig(val baseUrl: String, val publicUrl: String = baseUrl)

/** Supplies the signed-in user's Firebase ID token; implemented by [com.harsh.desicrics.data.AuthRepository]. */
fun interface IdTokenProvider {
    suspend fun idToken(forceRefresh: Boolean): String?
}

class ApiException(val status: HttpStatusCode, message: String) : Exception(message)

/** Client for the Ktor server's REST API (see `core/.../api/Api.kt` for the routes). */
class DesiCricsApi(
    private val http: HttpClient,
    private val tokens: IdTokenProvider,
    config: ApiConfig,
) {
    private val baseUrl = config.baseUrl.trimEnd('/')

    /** Null until the user finishes onboarding. */
    suspend fun myProfile(): UserProfile? = try {
        call { get("$baseUrl/me", it) }.body()
    } catch (e: ApiException) {
        if (e.status == HttpStatusCode.NotFound) null else throw e
    }

    suspend fun updateProfile(request: UpdateProfileRequest): UserProfile =
        call { put("$baseUrl/me") { it(); json(request) } }.body()

    suspend fun recentMatches(limit: Int): List<Match> =
        call { get("$baseUrl/matches") { it(); parameter("limit", limit) } }.body()

    suspend fun match(matchId: String): MatchDetails? = try {
        call { get("$baseUrl/matches/$matchId", it) }.body()
    } catch (e: ApiException) {
        if (e.status == HttpStatusCode.NotFound) null else throw e
    }

    suspend fun createMatch(request: CreateMatchRequest): MatchDetails =
        call { post("$baseUrl/matches") { it(); json(request) } }.body()

    suspend fun recordDelivery(matchId: String, delivery: Delivery): MatchDetails =
        call { post("$baseUrl/matches/$matchId/deliveries") { it(); json(delivery) } }.body()

    suspend fun recordEvent(matchId: String, event: MatchEvent): MatchDetails =
        call { post("$baseUrl/matches/$matchId/events") { it(); json(event) } }.body()

    suspend fun addPlayer(matchId: String, request: AddPlayerRequest): MatchDetails =
        call { post("$baseUrl/matches/$matchId/players") { it(); json(request) } }.body()

    suspend fun startHandover(matchId: String): HandoverCode =
        call { post("$baseUrl/matches/$matchId/handover", it) }.body()

    /** Null if the code is wrong or has expired. */
    suspend fun takeHandover(code: String): MatchDetails? = try {
        call { post("$baseUrl/handover/$code", it) }.body()
    } catch (e: ApiException) {
        if (e.status == HttpStatusCode.NotFound) null else throw e
    }

    // Teams, players and the account.

    suspend fun myTeams(): List<TeamSummary> = call { get("$baseUrl/teams/mine", it) }.body()

    suspend fun createTeam(request: TeamRequest): SavedTeam = call { post("$baseUrl/teams") { it(); json(request) } }.body()

    suspend fun team(teamId: String): SavedTeam = call { get("$baseUrl/teams/$teamId", it) }.body()

    suspend fun updateTeam(teamId: String, request: TeamRequest): SavedTeam =
        call { put("$baseUrl/teams/$teamId") { it(); json(request) } }.body()

    suspend fun addMember(teamId: String, request: AddMemberRequest): SavedTeam =
        call { post("$baseUrl/teams/$teamId/members") { it(); json(request) } }.body()

    suspend fun setRole(teamId: String, profileId: String, role: TeamRole): SavedTeam =
        call { put("$baseUrl/teams/$teamId/members/$profileId") { it(); json(SetRoleRequest(role)) } }.body()

    suspend fun removeMember(teamId: String, profileId: String): SavedTeam =
        call { delete("$baseUrl/teams/$teamId/members/$profileId", it) }.body()

    suspend fun newJoinCode(teamId: String): SavedTeam = call { post("$baseUrl/teams/$teamId/join-code", it) }.body()

    suspend fun joinTeam(code: String): SavedTeam = call { post("$baseUrl/teams/join/$code", it) }.body()

    suspend fun headToHead(teamId: String, otherId: String): HeadToHead =
        call { get("$baseUrl/teams/$teamId/head-to-head/$otherId", it) }.body()

    suspend fun searchPlayers(query: String): List<PlayerSummary> =
        call { get("$baseUrl/players/search") { it(); parameter("q", query) } }.body()

    suspend fun claimablePlayers(): List<PlayerSummary> = call { get("$baseUrl/players/claimable", it) }.body()

    suspend fun claimPlayer(request: ClaimRequest): PlayerSummary = call { post("$baseUrl/players/claim") { it(); json(request) } }.body()

    suspend fun myMatches(): List<Match> = call { get("$baseUrl/me/matches", it) }.body()

    suspend fun deleteAccount() {
        call { delete("$baseUrl/me", it) }
    }

    suspend fun contactSupport(request: SupportRequest) {
        call { post("$baseUrl/support") { it(); json(request) } }
    }

    /** Removes the latest ball or event. */
    suspend fun undoLast(matchId: String): MatchDetails =
        call { delete("$baseUrl/matches/$matchId/last", it) }.body()

    /**
     * Sends a request with the user's ID token, retrying once with a fresh token if the cached one has
     * expired. [send] receives a block that adds the auth header; call it inside the request builder.
     */
    private suspend fun call(send: suspend HttpClient.(authorize: HttpRequestBuilder.() -> Unit) -> HttpResponse): HttpResponse {
        suspend fun attempt(forceRefresh: Boolean): HttpResponse {
            val token = tokens.idToken(forceRefresh)
            return http.send { token?.let { bearerAuth(it) } }
        }
        var response = attempt(forceRefresh = false)
        if (response.status == HttpStatusCode.Unauthorized) response = attempt(forceRefresh = true)
        if (!response.status.isSuccess()) {
            val message = runCatching { response.body<ApiError>().message }.getOrNull()
            throw ApiException(response.status, message ?: "Request failed (${response.status.value})")
        }
        return response
    }

    private inline fun <reified T> HttpRequestBuilder.json(body: T) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }
}
