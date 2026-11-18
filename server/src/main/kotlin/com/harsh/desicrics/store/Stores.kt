package com.harsh.desicrics.store

import com.harsh.desicrics.api.MatchDetails
import com.harsh.desicrics.api.SupportRequest
import com.harsh.desicrics.model.Delivery
import com.harsh.desicrics.model.EntryStatus
import com.harsh.desicrics.model.FollowType
import com.harsh.desicrics.model.Match
import com.harsh.desicrics.model.MatchEvent
import com.harsh.desicrics.model.Notification
import com.harsh.desicrics.model.OrganiserRole
import com.harsh.desicrics.model.Player
import com.harsh.desicrics.model.PlayerMatchLine
import com.harsh.desicrics.model.PlayerSummary
import com.harsh.desicrics.model.SavedTeam
import com.harsh.desicrics.model.TeamRole
import com.harsh.desicrics.model.TeamSide
import com.harsh.desicrics.model.TeamSummary
import com.harsh.desicrics.model.Tournament
import com.harsh.desicrics.model.TournamentSummary
import com.harsh.desicrics.model.TournamentTeam
import com.harsh.desicrics.model.UserProfile

/**
 * Persistence boundary. Routes and services only see these interfaces, so any SQL database works by
 * changing DATABASE_URL, and a non-SQL store only needs new implementations of these interfaces.
 */
interface UserStore {
    suspend fun get(uid: String): UserProfile?
    suspend fun upsert(profile: UserProfile)
}

interface MatchStore {
    /** Newest first. */
    suspend fun recent(limit: Int): List<Match>

    suspend fun details(matchId: String): MatchDetails?

    /** Newest first: matches [profileId] played in, scored or created. */
    suspend fun forPlayer(profileId: String, limit: Int): List<Match>

    /** A tournament's fixtures (without rosters), by round. */
    suspend fun forTournament(tournamentId: String): List<Match>

    /** Turns a scheduled fixture into a match in play with [match]'s squads and toss. False if it had already started. */
    suspend fun startFixture(match: Match): Boolean

    suspend fun setSchedule(matchId: String, scheduledAt: Long?)

    /** Profile IDs who played in each of [matchIds]. */
    suspend fun profileIds(matchIds: Collection<String>): Map<String, Set<String>>

    /** Newest first: matches either of whose teams is named like [query]. */
    suspend fun search(query: String, limit: Int): List<Match>

    /** Match rows (without rosters) where saved teams [teamId] and [otherId] played each other. */
    suspend fun between(teamId: String, otherId: String): List<Match>

    /** Profile IDs who played for [teamId] in its latest match. */
    suspend fun lastSquad(teamId: String): List<String>

    /** Saves the match and its roster together. */
    suspend fun create(match: Match)

    /**
     * Saves the ball and the refreshed match row in one transaction.
     * Returns false if a ball with the same sequence number already exists.
     */
    suspend fun addDelivery(match: Match, delivery: Delivery): Boolean

    /** Saves the event and the refreshed match row in one transaction. Returns false if [MatchEvent.seq] is taken. */
    suspend fun addEvent(match: Match, event: MatchEvent): Boolean

    /**
     * Removes the ball or event numbered [seq] and saves the refreshed match row in one transaction.
     * Returns false if neither existed.
     */
    suspend fun removeEntry(match: Match, seq: Int): Boolean

    /** Adds a player to one side of an existing match, after the current squad. */
    suspend fun addPlayer(matchId: String, side: TeamSide, player: Player)

    /** Saves a handover code for the match, replacing any earlier one. False if [code] is already in use. */
    suspend fun saveHandover(matchId: String, code: String, expiresAt: Long): Boolean

    /**
     * Uses up [code]: if it's still valid at [now], [newScorer] becomes the match's scorer. Returns the
     * match ID, or null if the code is unknown or expired.
     */
    suspend fun takeHandover(code: String, now: Long, newScorer: String): String?

    /** Matches with balls bowled but no per-innings scores, i.e. scored before scores were stored. */
    suspend fun idsMissingInningsScores(): List<String>

    /** Saves the match row's score fields (summary, status, result, innings). */
    suspend fun updateScore(match: Match)
}

/** Players across matches: users and the placeholders team admins add. See [com.harsh.desicrics.model.PlayerSummary]. */
interface PlayerStore {
    /** Creates or refreshes the player row of a user from their profile. */
    suspend fun syncUser(profile: UserProfile)

    /** False if [claimCode] is taken; the caller retries with another. */
    suspend fun createPlaceholder(id: String, name: String, phone: String, claimCode: String, createdBy: String, at: Long): Boolean

    suspend fun summaries(ids: Collection<String>): Map<String, PlayerSummary>

    /** Users (not placeholders) whose name contains [query], or whose phone ends with its digits. */
    suspend fun search(query: String, limit: Int): List<PlayerSummary>

    /** Unclaimed placeholders created with [phone] (normalised to its last 10 digits). */
    suspend fun placeholdersWithPhone(phone: String): List<PlayerSummary>

    /** The placeholder with this claim code, if any. */
    suspend fun placeholderByCode(code: String): PlayerSummary?

    suspend fun claimCodes(ids: Collection<String>): Map<String, String>

    /**
     * Moves everything [placeholderId] did to [userId] (team places, match appearances) and deletes the
     * placeholder, in one transaction. Returns false if it was no longer a placeholder.
     */
    suspend fun merge(placeholderId: String, userId: String): Boolean

    /** Clears a deleted account's personal data, keeping the rows old scorecards point at. */
    suspend fun anonymise(uid: String, at: Long)
}

interface TeamStore {
    /** Saves the team with [adminId] as its first admin. False if [team]'s join code is taken. */
    suspend fun create(team: SavedTeam, adminId: String, at: Long): Boolean

    /** With every member's role and summary, the join code, and placeholders' claim codes left for the caller to strip. */
    suspend fun get(teamId: String): SavedTeam?

    suspend fun forPlayer(profileId: String): List<TeamSummary>

    suspend fun rename(teamId: String, name: String, city: String)

    /** False if they were already a member. */
    suspend fun addMember(teamId: String, profileId: String, role: TeamRole, at: Long): Boolean

    suspend fun setRole(teamId: String, profileId: String, role: TeamRole)

    suspend fun removeMember(teamId: String, profileId: String)

    suspend fun byJoinCode(code: String): String?

    /** False if [code] is taken. */
    suspend fun setJoinCode(teamId: String, code: String): Boolean

    suspend fun setLogo(teamId: String, url: String?)

    /** Teams whose name contains [query]. */
    suspend fun search(query: String, limit: Int): List<TeamSummary>

    /** Teams [profileId] belongs to, for removing them when the account is deleted. */
    suspend fun teamIdsOf(profileId: String): List<String>
}

interface SupportStore {
    suspend fun save(id: String, uid: String, request: SupportRequest, at: Long)
}

/** Follows, the notification inbox and push tokens. */
interface SocialStore {
    suspend fun follow(uid: String, type: FollowType, targetId: String, at: Long)
    suspend fun unfollow(uid: String, type: FollowType, targetId: String)
    suspend fun follows(uid: String): List<Pair<FollowType, String>>
    /** Users following any of [targetIds] of [type]. */
    suspend fun followers(type: FollowType, targetIds: Collection<String>): Set<String>
    suspend fun followerCount(type: FollowType, targetId: String): Int

    suspend fun addNotifications(notifications: Map<String, Notification>)
    suspend fun notifications(uid: String, limit: Int): List<Notification>
    suspend fun unreadCount(uid: String): Int
    suspend fun markAllRead(uid: String, at: Long)

    suspend fun savePushToken(uid: String, token: String, platform: String, at: Long)
    suspend fun pushTokens(uids: Collection<String>): Map<String, List<String>>
    suspend fun deletePushTokens(tokens: Collection<String>)
}

interface TournamentStore {
    suspend fun create(tournament: Tournament)
    /** With its organisers (named) and entered teams. */
    suspend fun get(tournamentId: String): Tournament?
    /** Tournaments [uid] organises, or that a team they belong to is in. */
    suspend fun forUser(uid: String): List<TournamentSummary>
    /** False if the team was already entered. */
    suspend fun addTeam(tournamentId: String, team: TournamentTeam): Boolean
    suspend fun setTeamStatus(tournamentId: String, teamId: String, status: EntryStatus)
    suspend fun removeTeam(tournamentId: String, teamId: String)
    suspend fun addOrganiser(tournamentId: String, uid: String, role: OrganiserRole)
    /** Fixes the teams' groups and marks the tournament started. False if it already was. */
    suspend fun markStarted(tournamentId: String, teams: List<TournamentTeam>): Boolean
}

/** The stats projection: one line per linked player per completed match. */
interface StatsStore {
    /** Replaces every line of [matchId] with [lines] (none, if it's no longer complete). */
    suspend fun replace(matchId: String, lines: List<PlayerMatchLine>)
    suspend fun forProfiles(profileIds: Collection<String>): List<PlayerMatchLine>
    suspend fun forTournament(tournamentId: String): List<PlayerMatchLine>
    /** Completed matches with no lines yet, e.g. finished before stats existed. */
    suspend fun completedMatchesWithoutLines(): List<String>
    /** Profile IDs of users who live in [city] (any case). */
    suspend fun profilesInCity(city: String): List<String>
}

/** Small uploaded pictures: profile photos and team logos. */
interface ImageStore {
    suspend fun save(id: String, ownerUid: String, contentType: String, bytes: ByteArray, at: Long)
    /** The content type and bytes, or null. */
    suspend fun get(id: String): Pair<String, ByteArray>?
}
