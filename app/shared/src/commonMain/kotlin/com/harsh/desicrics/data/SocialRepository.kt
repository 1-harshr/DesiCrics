package com.harsh.desicrics.data

import com.harsh.desicrics.data.api.ApiConfig
import com.harsh.desicrics.data.api.DesiCricsApi
import com.harsh.desicrics.model.Follow
import com.harsh.desicrics.model.FollowType
import com.harsh.desicrics.model.Notification
import com.harsh.desicrics.model.PlayerPage
import com.harsh.desicrics.model.SearchResults

/** Following, search, player pages and the notification inbox. */
class SocialRepository(private val api: DesiCricsApi, private val config: ApiConfig) {

    suspend fun search(query: String): SearchResults = api.search(query.trim())

    suspend fun playerPage(profileId: String): PlayerPage = api.playerPage(profileId)

    suspend fun follows(): List<Follow> = api.follows()

    suspend fun setFollowing(type: FollowType, id: String, following: Boolean) = api.setFollowing(type, id, following)

    suspend fun notifications(): List<Notification> = api.notifications()

    suspend fun markNotificationsRead() = api.markNotificationsRead()

    suspend fun savePushToken(token: String) = api.savePushToken(token)

    /** A browser source for OBS or Larix: the match's score as a bar along the bottom of a livestream. */
    fun overlayLink(matchId: String): String = "${config.publicUrl.trimEnd('/')}/overlay/$matchId"

    /** The public scorecard anyone can open, in or out of the app. */
    fun matchLink(matchId: String): String = "${config.publicUrl.trimEnd('/')}/m/$matchId"
}
