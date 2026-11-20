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
    /** [Notification.playerName] asked to join the team [Notification.teamId]; sent to its admins. */
    TEAM_JOIN_REQUEST,
    /** An admin let the user into the team [Notification.teamId]. */
    TEAM_JOIN_ACCEPTED,
    /**
     * [Notification.playerName], a team or a player ([Notification.detail] "team" or "player"), asked to
     * enter the tournament [Notification.tournamentId]; sent to its organisers.
     */
    ENTRY_REQUEST,
    /** ... or entered it outright, where entry is open. */
    ENTRY_JOINED,
    /** An organiser confirmed or added the entry; sent to the team's admins or the player. */
    ENTRY_CONFIRMED,
    /** An organiser turned the entry down. */
    ENTRY_REJECTED,
    /** An organiser invited the team (to its admins) or the player; they accept from the tournament. */
    ENTRY_INVITED,
    /** A team or player left the tournament before it started; sent to its organisers. */
    ENTRY_WITHDRAWN,
    /** A place opened and the waiting team or player moved up, to wait for an organiser. */
    ENTRY_PROMOTED,
    /** The team [Notification.playerName] asked the user to join its squad; they accept from the tournament. */
    SQUAD_OFFER,
    /** [Notification.playerName] took the team's offer and joined its squad; sent to its admins. */
    SQUAD_JOINED,
    /** Entry closes within a day and [Notification.value] entries still wait for the organisers. */
    ENTRY_CLOSING,
    /** The user was placed in [Notification.playerName]'s squad for the tournament. */
    SQUAD_PLACED,
}

/**
 * An inbox entry. Stored as facts rather than sentences, so the app words it in the user's language;
 * push messages, which the server words, are in English for now.
 */
@Serializable
data class Notification(
    val id: String,
    val kind: NotificationKind,
    /** Empty for team and tournament news, which have [teamId] or [tournamentId] instead. */
    val matchId: String,
    /** "Strikers vs Royals", or the team's or tournament's name for their news. */
    val matchTitle: String,
    val playerName: String? = null,
    val value: Int? = null,
    val detail: String? = null,
    val createdAt: Long,
    val read: Boolean = false,
    val teamId: String? = null,
    val tournamentId: String? = null,
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
    /** Filled in by the web app's public search only. */
    val tournaments: List<TournamentSummary> = emptyList(),
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
