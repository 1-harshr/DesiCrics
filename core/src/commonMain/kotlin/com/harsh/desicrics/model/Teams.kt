package com.harsh.desicrics.model

import kotlinx.serialization.Serializable

/**
 * A player across matches and teams: a signed-up user (whose [profileId] is their UID) or a placeholder a
 * team admin added for someone without the app, who can claim it later. Stats add up by [profileId].
 */
@Serializable
data class PlayerSummary(
    val profileId: String,
    val name: String,
    val city: String = "",
    val role: PlayerRole? = null,
    val isPlaceholder: Boolean = false,
    /** A path on the server ("/images/…") or a full URL, e.g. a Google profile photo. */
    val photoUrl: String? = null,
)

@Serializable
enum class TeamRole { ADMIN, MEMBER }

@Serializable
data class TeamMember(
    val player: PlayerSummary,
    val role: TeamRole,
    /** For a placeholder, the code its real player enters to claim it. Only sent to the team's admins. */
    val claimCode: String? = null,
)

/** A team that plays more than one match: its members and, for admins, the code others join with. */
@Serializable
data class SavedTeam(
    val id: String,
    val name: String,
    val city: String,
    val createdBy: String,
    val members: List<TeamMember>,
    /** Only sent to admins. */
    val joinCode: String? = null,
    /** Profile IDs who played the team's last match, to start the next one with the same squad. */
    val lastSquad: List<String> = emptyList(),
    val logoUrl: String? = null,
) {
    fun roleOf(profileId: String?): TeamRole? = members.firstOrNull { it.player.profileId == profileId }?.role
}

@Serializable
data class TeamSummary(
    val id: String,
    val name: String,
    val city: String,
    val memberCount: Int,
    /** The caller's role, or null if they aren't a member. */
    val myRole: TeamRole? = null,
    val logoUrl: String? = null,
)

/** How two saved teams have done against each other in completed matches. */
@Serializable
data class HeadToHead(
    val teamId: String,
    val opponentId: String,
    val played: Int,
    val won: Int,
    val lost: Int,
    val tied: Int,
)
