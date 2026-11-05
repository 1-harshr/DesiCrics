package com.harsh.desicrics.model

import kotlinx.serialization.Serializable

/** A match row in SQL Connect, with its players. Deliveries are stored separately, one row per ball. */
@Serializable
data class Match(
    val id: String,
    val createdBy: String,
    val createdAt: Long,
    val teamA: Team,
    val teamB: Team,
    val oversPerInnings: Int,
    val tossWinner: TeamSide,
    val tossDecision: TossDecision,
    val status: MatchStatus = MatchStatus.SETUP,
    /** Denormalised for list screens, e.g. "Strikers 145/6 (20.0) · Royals 98/3 (12.4)". */
    val scoreSummary: String = "",
    val result: String? = null,
) {
    val battingFirst: TeamSide
        get() = if (tossDecision == TossDecision.BAT) tossWinner else tossWinner.other

    fun team(side: TeamSide): Team = if (side == TeamSide.A) teamA else teamB
}

@Serializable
data class Team(
    val name: String,
    val players: List<Player>,
)

/** A player in a match. Not linked to a [UserProfile] yet; that comes with "add player by phone number". */
@Serializable
data class Player(
    val id: String,
    val name: String,
)

@Serializable
enum class TeamSide {
    A, B;

    val other: TeamSide get() = if (this == A) B else A
}

@Serializable
enum class TossDecision(val displayName: String) {
    BAT("Bat"),
    BOWL("Bowl"),
}

@Serializable
enum class MatchStatus {
    /** Created, but the roster isn't fully saved yet. */
    SETUP,
    IN_PROGRESS,
    COMPLETED,
}
