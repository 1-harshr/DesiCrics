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
    /** A signed-up player's public code; null for placeholders. */
    val code: String? = null,
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
    /** The team's public code, 8 digits, for finding it exactly. Unlike [joinCode], it lets no one in. */
    val code: String = "",
    /** Profile IDs who played the team's last match, to start the next one with the same squad. */
    val lastSquad: List<String> = emptyList(),
    val logoUrl: String? = null,
    /** Players asking to join, oldest first. Only sent to admins. */
    val joinRequests: List<PlayerSummary> = emptyList(),
    /** The caller has asked to join and is waiting for an admin. */
    val requested: Boolean = false,
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
    val code: String = "",
)

/** A team as anyone may see it on the web: its players and matches, without codes or join requests. */
@Serializable
data class PublicTeamPage(
    val team: SavedTeam,
    /** The latest, newest first. */
    val matches: List<Match> = emptyList(),
    /** Every completed match the team has played. */
    val record: WinLoss = WinLoss(),
    /** Against each saved team it has played, most played first. */
    val against: List<RecordAgainst> = emptyList(),
)

/** Completed matches and how they ended. Abandoned matches count as played but neither won, lost nor tied. */
@Serializable
data class WinLoss(val played: Int = 0, val won: Int = 0, val lost: Int = 0, val tied: Int = 0)

@Serializable
data class RecordAgainst(val opponentId: String, val opponentName: String, val record: WinLoss)

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
