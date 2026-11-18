package com.harsh.desicrics.store

import com.harsh.desicrics.api.MatchDetails
import com.harsh.desicrics.model.Delivery
import com.harsh.desicrics.model.Match
import com.harsh.desicrics.model.MatchEvent
import com.harsh.desicrics.model.Player
import com.harsh.desicrics.model.TeamSide
import com.harsh.desicrics.model.UserProfile

/**
 * Persistence boundary. Routes and services only see these interfaces, so any SQL database works by
 * changing DATABASE_URL, and a non-SQL store only needs new implementations of these two.
 */
interface UserStore {
    suspend fun get(uid: String): UserProfile?
    suspend fun upsert(profile: UserProfile)
}

interface MatchStore {
    /** Newest first. */
    suspend fun recent(limit: Int): List<Match>

    suspend fun details(matchId: String): MatchDetails?

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
