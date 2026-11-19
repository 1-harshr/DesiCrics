package com.harsh.desicrics.service

import com.harsh.desicrics.api.PushTokenRequest
import com.harsh.desicrics.auth.FirebasePrincipal
import com.harsh.desicrics.model.Follow
import com.harsh.desicrics.model.FollowType
import com.harsh.desicrics.model.Match
import com.harsh.desicrics.model.MatchStatus
import com.harsh.desicrics.model.Notification
import com.harsh.desicrics.model.PlayerPage
import com.harsh.desicrics.model.SearchResults
import com.harsh.desicrics.model.TodayFeed
import com.harsh.desicrics.store.MatchStore
import com.harsh.desicrics.store.PlayerStore
import com.harsh.desicrics.store.SocialStore
import com.harsh.desicrics.store.TeamStore
import io.ktor.http.HttpStatusCode

/** Following teams and players, and what that changes: the home feed, search, player pages and the inbox. */
class SocialService(
    private val social: SocialStore,
    private val matches: MatchStore,
    private val teams: TeamStore,
    private val players: PlayerStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /**
     * The caller's own unfinished matches first, then everyone's live, upcoming and recent ones. Within
     * each, matches involving a team or player they follow (or play for) come first.
     */
    suspend fun feed(user: FirebasePrincipal): TodayFeed {
        val all = matches.recent(FEED_SIZE)
        val follows = social.follows(user.uid)
        val teamIds = follows.filter { it.first == FollowType.TEAM }.map { it.second }.toSet() + teams.forPlayer(user.uid).map { it.id }
        val playerIds = follows.filter { it.first == FollowType.PLAYER }.map { it.second }.toSet() + user.uid
        val squads = matches.profileIds(all.map { it.id })
        fun Match.followed() = teamA.teamId in teamIds || teamB.teamId in teamIds || squads[id].orEmpty().any { it in playerIds }
        fun List<Match>.followedFirst() = sortedByDescending { it.followed() }

        val mine = all.filter { it.status != MatchStatus.COMPLETED && (it.scorerUid == user.uid || user.uid in squads[it.id].orEmpty()) }
        return TodayFeed(
            mine = mine,
            live = all.filter { it.status == MatchStatus.IN_PROGRESS && it !in mine }.followedFirst(),
            upcoming = all.filter { it.status == MatchStatus.SCHEDULED }.sortedBy { it.scheduledAt ?: Long.MAX_VALUE }.followedFirst(),
            recent = all.filter { it.status == MatchStatus.COMPLETED }.followedFirst().take(RECENT_SIZE),
            unreadNotifications = social.unreadCount(user.uid),
        )
    }

    suspend fun page(status: String?, before: String?, id: String?): List<Match> {
        val filter = status?.let { MatchStatus.entries.find { s -> s.name == it } ?: throw ApiException(HttpStatusCode.BadRequest, "Invalid match status") }
        val time = before?.toLongOrNull()
        if ((before != null && time == null) || (time == null) != (id == null)) throw ApiException(HttpStatusCode.BadRequest, "Invalid cursor")
        return matches.feedPage(filter, time, id.orEmpty())
    }

    suspend fun search(query: String): SearchResults {
        if (query.trim().length < 2) return SearchResults()
        return SearchResults(
            teams = teams.search(query, SEARCH_SIZE),
            players = players.search(query, SEARCH_SIZE),
            matches = matches.search(query, SEARCH_SIZE),
        )
    }

    suspend fun playerPage(user: FirebasePrincipal, profileId: String): PlayerPage {
        val player = players.summaries(listOf(profileId))[profileId] ?: throw ApiException(HttpStatusCode.NotFound, "Player not found")
        return PlayerPage(
            player = player,
            teams = teams.forPlayer(profileId).map { it.copy(myRole = null) },
            matches = matches.forPlayer(profileId, limit = 20),
            following = social.follows(user.uid).contains(FollowType.PLAYER to profileId),
            followers = social.followerCount(FollowType.PLAYER, profileId),
        )
    }

    suspend fun follows(user: FirebasePrincipal): List<Follow> {
        val follows = social.follows(user.uid)
        val playerNames = players.summaries(follows.filter { it.first == FollowType.PLAYER }.map { it.second })
        return follows.mapNotNull { (type, id) ->
            when (type) {
                FollowType.PLAYER -> playerNames[id]?.let { Follow(type, id, it.name) }
                FollowType.TEAM -> teams.get(id)?.let { Follow(type, id, it.name) }
            }
        }
    }

    suspend fun follow(user: FirebasePrincipal, type: FollowType, targetId: String) {
        val exists = when (type) {
            FollowType.PLAYER -> players.summaries(listOf(targetId)).isNotEmpty()
            FollowType.TEAM -> teams.get(targetId) != null
        }
        if (!exists) throw ApiException(HttpStatusCode.NotFound, "Nothing to follow there")
        if (type == FollowType.PLAYER && targetId == user.uid) throw ApiException(HttpStatusCode.BadRequest, "You can't follow yourself")
        social.follow(user.uid, type, targetId, clock())
    }

    suspend fun unfollow(user: FirebasePrincipal, type: FollowType, targetId: String) = social.unfollow(user.uid, type, targetId)

    suspend fun notifications(user: FirebasePrincipal): List<Notification> = social.notifications(user.uid, limit = 100)

    suspend fun markRead(user: FirebasePrincipal) = social.markAllRead(user.uid, clock())

    suspend fun savePushToken(user: FirebasePrincipal, request: PushTokenRequest) {
        if (request.token.isBlank() || request.token.length > 512) throw ApiException(HttpStatusCode.BadRequest, "Invalid push token")
        social.savePushToken(user.uid, request.token, request.platform.take(10), clock())
    }

    private companion object {
        const val FEED_SIZE = 100
        const val RECENT_SIZE = 30
        const val SEARCH_SIZE = 20
    }
}
