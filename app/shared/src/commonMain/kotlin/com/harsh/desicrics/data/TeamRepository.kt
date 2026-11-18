package com.harsh.desicrics.data

import com.harsh.desicrics.api.AddMemberRequest
import com.harsh.desicrics.api.ClaimRequest
import com.harsh.desicrics.api.SupportCategory
import com.harsh.desicrics.api.SupportRequest
import com.harsh.desicrics.api.TeamRequest
import com.harsh.desicrics.data.api.ApiConfig
import com.harsh.desicrics.data.api.DesiCricsApi
import com.harsh.desicrics.model.HeadToHead
import com.harsh.desicrics.model.Match
import com.harsh.desicrics.model.PlayerSummary
import com.harsh.desicrics.model.SavedTeam
import com.harsh.desicrics.model.TeamRole
import com.harsh.desicrics.model.TeamSummary

/** Saved teams, finding players, and the account. Every call goes to the server; nothing is cached. */
class TeamRepository(private val api: DesiCricsApi, private val config: ApiConfig, private val analytics: Analytics) {

    suspend fun myTeams(): List<TeamSummary> = api.myTeams()

    suspend fun create(name: String, city: String): SavedTeam =
        api.createTeam(TeamRequest(name.trim(), city.trim())).also { analytics.log(AnalyticsEvents.TEAM_CREATED, emptyMap()) }

    suspend fun team(teamId: String): SavedTeam = api.team(teamId)

    suspend fun rename(teamId: String, name: String, city: String): SavedTeam = api.updateTeam(teamId, TeamRequest(name.trim(), city.trim()))

    suspend fun addPlayer(teamId: String, profileId: String): SavedTeam = api.addMember(teamId, AddMemberRequest(profileId = profileId))

    suspend fun addPlaceholder(teamId: String, name: String, phone: String): SavedTeam =
        api.addMember(teamId, AddMemberRequest(placeholderName = name.trim(), placeholderPhone = phone.trim().ifEmpty { null }))

    suspend fun setRole(teamId: String, profileId: String, role: TeamRole): SavedTeam = api.setRole(teamId, profileId, role)

    suspend fun remove(teamId: String, profileId: String): SavedTeam = api.removeMember(teamId, profileId)

    suspend fun newJoinCode(teamId: String): SavedTeam = api.newJoinCode(teamId)

    suspend fun uploadLogo(teamId: String, jpeg: ByteArray): SavedTeam = api.uploadTeamLogo(teamId, jpeg)

    suspend fun join(code: String): SavedTeam =
        api.joinTeam(code.trim().uppercase()).also { analytics.log(AnalyticsEvents.TEAM_JOINED, emptyMap()) }

    suspend fun headToHead(teamId: String, otherId: String): HeadToHead = api.headToHead(teamId, otherId)

    suspend fun search(query: String): List<PlayerSummary> = api.searchPlayers(query)

    suspend fun claimable(): List<PlayerSummary> = api.claimablePlayers()

    suspend fun claimByCode(code: String): PlayerSummary =
        api.claimPlayer(ClaimRequest(code = code.trim().uppercase())).also { analytics.log(AnalyticsEvents.PLAYER_CLAIMED, mapOf("by" to "code")) }

    suspend fun claim(profileId: String): PlayerSummary =
        api.claimPlayer(ClaimRequest(profileId = profileId)).also { analytics.log(AnalyticsEvents.PLAYER_CLAIMED, mapOf("by" to "phone")) }

    suspend fun myMatches(): List<Match> = api.myMatches()

    suspend fun deleteAccount() = api.deleteAccount()

    suspend fun contactSupport(category: SupportCategory, message: String, appVersion: String) =
        api.contactSupport(SupportRequest(category, message.trim(), appVersion))

    /** The link a join QR code and invite message carry; the app opens it, and the server answers it in a browser. */
    fun joinLink(code: String): String = "${config.publicUrl.trimEnd('/')}/join/$code"
}
