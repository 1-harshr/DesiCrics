package com.harsh.desicrics.store

import com.harsh.desicrics.api.MatchDetails
import com.harsh.desicrics.model.Delivery
import com.harsh.desicrics.model.Match
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

    /** Removes ball [seq] and saves the refreshed match row in one transaction. Returns false if it didn't exist. */
    suspend fun removeDelivery(match: Match, seq: Int): Boolean

    /** Matches with balls bowled but no per-innings scores, i.e. scored before scores were stored. */
    suspend fun idsMissingInningsScores(): List<String>

    /** Saves the match row's score fields (summary, status, result, innings). */
    suspend fun updateScore(match: Match)
}
