package com.harsh.desicrics.data

import com.harsh.desicrics.model.TeamInsights
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

    suspend fun searchTeams(query: String) = api.search(query).teams
    suspend fun placeholder(name: String, phone: String) = api.placeholder(AddMemberRequest(placeholderName = name, placeholderPhone = phone))

    suspend fun myTeams(): List<TeamSummary> = api.myTeams()

    suspend fun browse(query: String, offset: Int): List<TeamSummary> = api.browseTeams(query.trim(), offset)

    suspend fun create(name: String, city: String, lineup: List<com.harsh.desicrics.api.SquadPlayer> = emptyList()): SavedTeam =
        api.createTeam(TeamRequest(name.trim(), city.trim(), lineup)).also { analytics.log(AnalyticsEvents.TEAM_CREATED, emptyMap()) }

    suspend fun team(teamId: String): SavedTeam = api.team(teamId)

    suspend fun rename(teamId: String, name: String, city: String): SavedTeam = api.updateTeam(teamId, TeamRequest(name.trim(), city.trim()))

    suspend fun addPlayer(teamId: String, profileId: String): SavedTeam = api.addMember(teamId, AddMemberRequest(profileId = profileId))

    suspend fun addPlaceholder(teamId: String, name: String, phone: String): SavedTeam =
        api.addMember(teamId, AddMemberRequest(placeholderName = name.trim(), placeholderPhone = phone.trim().ifEmpty { null }))

    suspend fun setRole(teamId: String, profileId: String, role: TeamRole): SavedTeam = api.setRole(teamId, profileId, role)

    suspend fun remove(teamId: String, profileId: String): SavedTeam = api.removeMember(teamId, profileId)

    suspend fun newJoinCode(teamId: String): SavedTeam = api.newJoinCode(teamId)

    suspend fun uploadLogo(teamId: String, jpeg: ByteArray): SavedTeam = api.uploadTeamLogo(teamId, jpeg)

    /** For a team found by name: its admins are asked, and the caller joins once one accepts. */
    suspend fun requestToJoin(teamId: String): SavedTeam = api.requestToJoin(teamId)

    suspend fun cancelJoinRequest(teamId: String): SavedTeam = api.cancelJoinRequest(teamId)

    suspend fun acceptJoinRequest(teamId: String, profileId: String): SavedTeam = api.acceptJoinRequest(teamId, profileId)

    suspend fun declineJoinRequest(teamId: String, profileId: String): SavedTeam = api.declineJoinRequest(teamId, profileId)

    suspend fun join(code: String): SavedTeam =
        api.joinTeam(code.trim().uppercase()).also { analytics.log(AnalyticsEvents.TEAM_JOINED, emptyMap()) }

    suspend fun headToHead(teamId: String, otherId: String): HeadToHead = api.headToHead(teamId, otherId)

    suspend fun insights(teamId: String): TeamInsights = api.teamInsights(teamId)

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
