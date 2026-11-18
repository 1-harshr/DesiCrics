package com.harsh.desicrics.model

import kotlinx.serialization.Serializable

@Serializable
enum class FollowType { TEAM, PLAYER }

/** Something the user follows, with its name for listing. */
@Serializable
data class Follow(val type: FollowType, val targetId: String, val name: String)

@Serializable
enum class NotificationKind {
    /** A match involving something the user follows has started. */
    MATCH_STARTED,
    /** ... and finished; [Notification.detail] is the score summary. */
    MATCH_RESULT,
    /** A followed player reached [Notification.value] runs. */
    MILESTONE_RUNS,
    /** A followed player took [Notification.value] wickets. */
    MILESTONE_WICKETS,
    /** A fixture's start time changed; [Notification.detail] is the new time in epoch milliseconds, or empty if unset. */
    FIXTURE_RESCHEDULED,
}

/**
 * An inbox entry. Stored as facts rather than sentences, so the app words it in the user's language;
 * push messages, which the server words, are in English for now.
 */
@Serializable
data class Notification(
    val id: String,
    val kind: NotificationKind,
    val matchId: String,
    /** "Strikers vs Royals". */
    val matchTitle: String,
    val playerName: String? = null,
    val value: Int? = null,
    val detail: String? = null,
    val createdAt: Long,
    val read: Boolean = false,
)

/** The home screen: the caller's own live match first, then live, upcoming and recent, followed ones first. */
@Serializable
data class TodayFeed(
    val mine: List<Match> = emptyList(),
    val live: List<Match> = emptyList(),
    val upcoming: List<Match> = emptyList(),
    val recent: List<Match> = emptyList(),
    val unreadNotifications: Int = 0,
)

@Serializable
data class SearchResults(
    val teams: List<TeamSummary> = emptyList(),
    val players: List<PlayerSummary> = emptyList(),
    val matches: List<Match> = emptyList(),
)

/** Another player's page: who they are, their teams and matches, and whether the caller follows them. */
@Serializable
data class PlayerPage(
    val player: PlayerSummary,
    val teams: List<TeamSummary> = emptyList(),
    val matches: List<Match> = emptyList(),
    val following: Boolean = false,
    val followers: Int = 0,
)
