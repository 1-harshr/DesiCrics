package com.harsh.desicrics.stats

import com.harsh.desicrics.model.Match
import com.harsh.desicrics.model.MatchStatus
import com.harsh.desicrics.model.PlayerMatchLine
import com.harsh.desicrics.model.TeamSide
import kotlin.math.pow
import kotlin.math.roundToInt

/** A team's Elo rating and how many rated matches it has played. */
data class TeamRating(val rating: Int, val played: Int)

/**
 * City rankings, weighted by opposition strength.
 *
 * Teams: Elo over completed matches between saved teams, oldest first. Everyone starts at 1000, K is 32, a
 * tie is half a win. Beating a strong side moves you up more than beating a weak one.
 *
 * Players: MVP points per match, multiplied by the opposing team's rating ÷ 1000, so 50 points against a
 * 1200 side count as 60. Against a side that isn't a saved team, the multiplier is 1.
 */
object Rankings {
    const val START = 1000
    private const val K = 32.0

    fun teams(matches: List<Match>): Map<String, TeamRating> {
        val ratings = mutableMapOf<String, Double>()
        val played = mutableMapOf<String, Int>()
        val rated = matches
            .filter { it.status == MatchStatus.COMPLETED && it.result != null && !it.result.isNoResult && it.teamA.teamId != null && it.teamB.teamId != null }
            .sortedBy { it.createdAt }
        for (match in rated) {
            val a = match.teamA.teamId!!
            val b = match.teamB.teamId!!
            if (a == b) continue
            val ra = ratings[a] ?: START.toDouble()
            val rb = ratings[b] ?: START.toDouble()
            val expectedA = 1 / (1 + 10.0.pow((rb - ra) / 400))
            val scoreA = when (match.result!!.winner) {
                TeamSide.A -> 1.0
                TeamSide.B -> 0.0
                null -> 0.5
            }
            ratings[a] = ra + K * (scoreA - expectedA)
            ratings[b] = rb + K * ((1 - scoreA) - (1 - expectedA))
            played.merge(a, 1, Int::plus)
            played.merge(b, 1, Int::plus)
        }
        return ratings.mapValues { (id, rating) -> TeamRating(rating.roundToInt(), played[id] ?: 0) }
    }

    /** A player's ranking score from their match lines; [teamRatings] from [teams]. */
    fun playerScore(lines: List<PlayerMatchLine>, teamRatings: Map<String, TeamRating>): Int =
        lines.sumOf { line ->
            val strength = line.opponentTeamId?.let { teamRatings[it]?.rating } ?: START
            line.mvpTenths / 10.0 * strength / START
        }.roundToInt()
}
