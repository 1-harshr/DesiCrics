package com.harsh.desicrics.model

import kotlinx.serialization.Serializable

@Serializable
enum class LookingForKind { OPPONENT, PLAYER, GROUND, UMPIRE, SCORER }

/** "Looking for an opponent in Pune on Sunday": a need posted for others in the city to answer. */
@Serializable
data class LookingForPost(
    val id: String,
    val kind: LookingForKind,
    val city: String,
    /** The day it's for, as epoch milliseconds at the start of that day. */
    val date: Long,
    val note: String,
    val author: PlayerSummary,
    val team: TeamSummary? = null,
    val responseCount: Int = 0,
    val open: Boolean = true,
    /** Only for the author. */
    val responses: List<LookingForResponse> = emptyList(),
)

@Serializable
data class LookingForResponse(val from: PlayerSummary, val message: String, val phone: String, val createdAt: Long)

@Serializable
enum class DirectoryKind { GROUND, UMPIRE, SCORER }

/** A ground, umpire or scorer people can contact. */
@Serializable
data class DirectoryEntry(
    val id: String,
    val kind: DirectoryKind,
    val name: String,
    val city: String,
    val phone: String,
    val notes: String,
    val addedBy: String,
)

/** A team's place in its city, by an Elo rating from results against other saved teams. */
@Serializable
data class TeamRanking(val team: TeamSummary, val rating: Int, val played: Int)

/** A player's place in their city: MVP points, each match weighted by the strength of the side they faced. */
@Serializable
data class PlayerRanking(val player: PlayerSummary, val score: Int, val matches: Int)

/** What a livestream overlay shows, refreshed every couple of seconds. Public: no names beyond the scorecard's. */
@Serializable
data class OverlayScore(
    val title: String,
    val battingTeam: String,
    val score: String,
    val overs: String,
    val detail: String,
    val striker: String? = null,
    val nonStriker: String? = null,
    val bowler: String? = null,
    val thisOver: List<String> = emptyList(),
)
