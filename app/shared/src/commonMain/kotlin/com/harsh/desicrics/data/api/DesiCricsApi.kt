package com.harsh.desicrics.data.api

import com.harsh.desicrics.model.TeamInsights
import com.harsh.desicrics.api.AddMemberRequest
import com.harsh.desicrics.api.AddOrganiserRequest
import com.harsh.desicrics.api.CreateTournamentRequest
import com.harsh.desicrics.api.DirectoryRequest
import com.harsh.desicrics.api.LookingForRequest
import com.harsh.desicrics.api.RespondRequest
import com.harsh.desicrics.api.CustomFixtureRequest
import com.harsh.desicrics.api.EnterTeamRequest
import com.harsh.desicrics.api.ReviewEntryRequest
import com.harsh.desicrics.api.ScheduleRequest
import com.harsh.desicrics.api.StartFixtureRequest
import com.harsh.desicrics.api.AddPlayerRequest
import com.harsh.desicrics.api.ClaimRequest
import com.harsh.desicrics.api.PushTokenRequest
import com.harsh.desicrics.api.SetRoleRequest
import com.harsh.desicrics.api.SupportRequest
import com.harsh.desicrics.api.TeamRequest
import com.harsh.desicrics.model.CareerStats
import com.harsh.desicrics.model.DirectoryEntry
import com.harsh.desicrics.model.DirectoryKind
import com.harsh.desicrics.model.Follow
import com.harsh.desicrics.model.LookingForKind
import com.harsh.desicrics.model.LookingForPost
import com.harsh.desicrics.model.PlayerRanking
import com.harsh.desicrics.model.TeamRanking
import com.harsh.desicrics.model.FormatFilter
import com.harsh.desicrics.model.LeaderboardMetric
import com.harsh.desicrics.model.LeaderboardRow
import com.harsh.desicrics.model.LeaderboardScope
import com.harsh.desicrics.model.FollowType
import com.harsh.desicrics.model.HeadToHead
import com.harsh.desicrics.model.Notification
import com.harsh.desicrics.model.PlayerPage
import com.harsh.desicrics.model.SearchResults
import com.harsh.desicrics.model.TodayFeed
import com.harsh.desicrics.model.TournamentDetails
import com.harsh.desicrics.model.TournamentSummary
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
import io.ktor.client.plugins.websocket.receiveDeserialized
import io.ktor.client.plugins.websocket.webSocket
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

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

    suspend fun teamInsights(teamId: String): TeamInsights =
        call { get("$baseUrl/teams/$teamId/insights", it) }.body()

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

    // Following, the feed, search and notifications.

    suspend fun feed(): TodayFeed = call { get("$baseUrl/feed", it) }.body()

    suspend fun search(query: String): SearchResults = call { get("$baseUrl/search") { it(); parameter("q", query) } }.body()

    suspend fun playerPage(profileId: String): PlayerPage = call { get("$baseUrl/players/$profileId", it) }.body()

    suspend fun follows(): List<Follow> = call { get("$baseUrl/follows", it) }.body()

    suspend fun setFollowing(type: FollowType, id: String, following: Boolean) {
        val url = "$baseUrl/follows/${type.name.lowercase()}/$id"
        call { if (following) put(url, it) else delete(url, it) }
    }

    suspend fun notifications(): List<Notification> = call { get("$baseUrl/notifications", it) }.body()

    suspend fun markNotificationsRead() {
        call { post("$baseUrl/notifications/read", it) }
    }

    suspend fun savePushToken(token: String) {
        call { put("$baseUrl/me/push-token") { it(); json(PushTokenRequest(token)) } }
    }

    /**
     * Every scorecard of [matchId] as it changes, over a WebSocket, starting with the current one. Ends (or
     * throws) when the connection drops; the caller decides whether to reconnect.
     */
    fun liveMatch(matchId: String): Flow<MatchDetails> = flow {
        val token = tokens.idToken(forceRefresh = false)
        val url = baseUrl.replaceFirst("http", "ws") + "/matches/$matchId/live"
        http.webSocket(url, request = { token?.let { bearerAuth(it) } }) {
            while (true) emit(receiveDeserialized<MatchDetails>())
        }
    }

    // Tournaments.

    suspend fun myTournaments(): List<TournamentSummary> = call { get("$baseUrl/tournaments/mine", it) }.body()

    suspend fun createTournament(request: CreateTournamentRequest): TournamentDetails =
        call { post("$baseUrl/tournaments") { it(); json(request) } }.body()

    suspend fun tournament(id: String): TournamentDetails = call { get("$baseUrl/tournaments/$id", it) }.body()

    suspend fun enterTeam(id: String, teamId: String): TournamentDetails =
        call { post("$baseUrl/tournaments/$id/teams") { it(); json(EnterTeamRequest(teamId)) } }.body()

    suspend fun reviewEntry(id: String, teamId: String, confirm: Boolean): TournamentDetails =
        call { put("$baseUrl/tournaments/$id/teams/$teamId") { it(); json(ReviewEntryRequest(confirm)) } }.body()

    suspend fun addOrganiser(id: String, profileId: String): TournamentDetails =
        call { post("$baseUrl/tournaments/$id/organisers") { it(); json(AddOrganiserRequest(profileId)) } }.body()

    suspend fun startTournament(id: String): TournamentDetails = call { post("$baseUrl/tournaments/$id/start", it) }.body()

    suspend fun addFixture(id: String, request: CustomFixtureRequest): TournamentDetails =
        call { post("$baseUrl/tournaments/$id/fixtures") { it(); json(request) } }.body()

    suspend fun schedule(matchId: String, scheduledAt: Long?): MatchDetails =
        call { put("$baseUrl/matches/$matchId/schedule") { it(); json(ScheduleRequest(scheduledAt)) } }.body()

    suspend fun startFixture(matchId: String, request: StartFixtureRequest): MatchDetails =
        call { post("$baseUrl/matches/$matchId/start") { it(); json(request) } }.body()

    // Stats.

    suspend fun playerStats(profileId: String, season: Int?, format: FormatFilter): CareerStats =
        call { get("$baseUrl/players/$profileId/stats") { it(); season?.let { s -> parameter("season", s) }; parameter("format", format.name) } }.body()

    suspend fun compare(a: String, b: String, season: Int?, format: FormatFilter): List<CareerStats> =
        call {
            get("$baseUrl/stats/compare") {
                it(); parameter("a", a); parameter("b", b); season?.let { s -> parameter("season", s) }; parameter("format", format.name)
            }
        }.body()

    suspend fun leaderboard(scope: LeaderboardScope, id: String?, metric: LeaderboardMetric, season: Int?): List<LeaderboardRow> =
        call {
            get("$baseUrl/leaderboards") {
                it(); parameter("scope", scope.name); id?.let { v -> parameter("id", v) }; parameter("metric", metric.name)
                season?.let { s -> parameter("season", s) }
            }
        }.body()

    suspend fun uploadProfilePhoto(jpeg: ByteArray): UserProfile =
        call { put("$baseUrl/me/photo") { it(); contentType(ContentType.Image.JPEG); setBody(jpeg) } }.body()

    suspend fun uploadTeamLogo(teamId: String, jpeg: ByteArray): SavedTeam =
        call { put("$baseUrl/teams/$teamId/logo") { it(); contentType(ContentType.Image.JPEG); setBody(jpeg) } }.body()

    // Community.

    suspend fun lookingFor(city: String, kind: LookingForKind?): List<LookingForPost> =
        call { get("$baseUrl/looking-for") { it(); parameter("city", city); kind?.let { k -> parameter("kind", k.name) } } }.body()

    suspend fun myLookingFor(): List<LookingForPost> = call { get("$baseUrl/looking-for/mine", it) }.body()

    suspend fun postLookingFor(request: LookingForRequest): LookingForPost = call { post("$baseUrl/looking-for") { it(); json(request) } }.body()

    suspend fun respond(postId: String, request: RespondRequest): LookingForPost =
        call { post("$baseUrl/looking-for/$postId/responses") { it(); json(request) } }.body()

    suspend fun closeLookingFor(postId: String): LookingForPost = call { post("$baseUrl/looking-for/$postId/close", it) }.body()

    suspend fun directory(city: String, kind: DirectoryKind?): List<DirectoryEntry> =
        call { get("$baseUrl/directory") { it(); parameter("city", city); kind?.let { k -> parameter("kind", k.name) } } }.body()

    suspend fun addDirectoryEntry(request: DirectoryRequest): DirectoryEntry = call { post("$baseUrl/directory") { it(); json(request) } }.body()

    suspend fun removeDirectoryEntry(id: String) {
        call { delete("$baseUrl/directory/$id", it) }
    }

    suspend fun teamRankings(city: String): List<TeamRanking> = call { get("$baseUrl/rankings/teams") { it(); parameter("city", city) } }.body()

    suspend fun playerRankings(city: String): List<PlayerRanking> = call { get("$baseUrl/rankings/players") { it(); parameter("city", city) } }.body()

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
