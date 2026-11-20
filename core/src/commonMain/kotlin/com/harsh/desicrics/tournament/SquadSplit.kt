package com.harsh.desicrics.tournament

import com.harsh.desicrics.model.PlayerRole
import com.harsh.desicrics.model.PlayerSummary

/** Shares players out between teams so the sides come out even, for pick-up tournaments. */
object SquadSplit {

    /**
     * Which team each player goes to, by profile ID. Wicket-keepers go out first so each side gets one where
     * there are enough; then, strongest first by [strength], each player joins the team with the fewest
     * players, and of those the weakest so far. Ties go to the earlier team, so the split is repeatable.
     */
    fun split(players: List<PlayerSummary>, teamIds: List<String>, strength: (PlayerSummary) -> Int): Map<String, String> {
        require(teamIds.isNotEmpty())
        val counts = teamIds.associateWith { 0 }.toMutableMap()
        val totals = teamIds.associateWith { 0 }.toMutableMap()
        val placed = mutableMapOf<String, String>()
        val (keepers, others) = players.partition { it.role == PlayerRole.WICKET_KEEPER }
        for (player in keepers.sortedByDescending(strength) + others.sortedByDescending(strength)) {
            val team = teamIds.minWith(compareBy<String> { counts.getValue(it) }.thenBy { totals.getValue(it) })
            placed[player.profileId] = team
            counts[team] = counts.getValue(team) + 1
            totals[team] = totals.getValue(team) + strength(player)
        }
        return placed
    }
}
