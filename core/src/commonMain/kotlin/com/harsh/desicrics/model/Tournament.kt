package com.harsh.desicrics.model

import kotlinx.serialization.Serializable

/** How a tournament is played, and the fewest teams it works with. */
@Serializable
enum class TournamentFormat(val minTeams: Int) {
    /** Lose and you're out. Top seeds get byes when the teams don't fill a bracket. */
    KNOCKOUT(2),
    /** Everyone plays everyone once, then the top [Tournament.advance] play off (0 for no playoff). */
    ROUND_ROBIN(3),
    /** Round robin within [Tournament.groups] groups, then the top [Tournament.advance] of each go to knockouts. */
    GROUPS_KNOCKOUT(4),
    /** A league whose top four play semi-finals and a final. */
    BOX_LEAGUE(4),
    /** Out after two losses. Each round pairs teams with the same number of losses. */
    DOUBLE_OUT(4),
    /** Two teams, first to two wins. */
    BEST_OF_THREE(2),
    /** The organiser adds every fixture by hand. */
    CUSTOM(2),
}

@Serializable
enum class Stage { LEAGUE, ROUND, QUARTERFINAL, SEMIFINAL, FINAL, MATCH }

/** Where a match sits in a tournament. [round] counts from 1 across the whole tournament; [number] within the round. */
@Serializable
data class FixtureInfo(
    val tournamentId: String,
    val stage: Stage,
    val round: Int,
    val number: Int,
    /** 0-based group, for league fixtures. */
    val group: Int? = null,
)

@Serializable
enum class OrganiserRole { ORGANISER, CO_ORGANISER }

@Serializable
data class Organiser(val uid: String, val name: String, val role: OrganiserRole)

@Serializable
enum class EntryStatus { CONFIRMED, PENDING }

/** A saved team entered in a tournament. Lower [seed] is stronger. */
@Serializable
data class TournamentTeam(
    val teamId: String,
    val name: String,
    val seed: Int,
    /** 0-based, for group formats; assigned when fixtures are made. */
    val group: Int? = null,
    /** Teams that registered themselves wait for an organiser to confirm them. */
    val status: EntryStatus = EntryStatus.CONFIRMED,
)

@Serializable
data class Tournament(
    val id: String,
    val name: String,
    val city: String = "",
    val format: TournamentFormat,
    val oversPerInnings: Int,
    val rules: MatchRules = MatchRules(),
    val groups: Int = 1,
    /** Teams per group that go through to the knockouts. */
    val advance: Int = 0,
    val createdBy: String,
    val createdAt: Long = 0,
    val startsAt: Long? = null,
    /** Team admins can enter their own team, pending an organiser's say-so. */
    val registrationOpen: Boolean = false,
    val organisers: List<Organiser> = emptyList(),
    val teams: List<TournamentTeam> = emptyList(),
    /** Fixtures have been made; the team list is fixed from then on. */
    val started: Boolean = false,
) {
    val confirmedTeams: List<TournamentTeam> get() = teams.filter { it.status == EntryStatus.CONFIRMED }

    fun isOrganiser(uid: String?): Boolean = organisers.any { it.uid == uid }
}

/** One team's line in a points table. Two points for a win, one for a tie. */
@Serializable
data class PointsRow(
    val teamId: String,
    val name: String,
    val played: Int = 0,
    val won: Int = 0,
    val lost: Int = 0,
    val tied: Int = 0,
    val runsFor: Int = 0,
    val ballsFaced: Int = 0,
    val runsAgainst: Int = 0,
    val ballsBowled: Int = 0,
) {
    val points: Int get() = won * 2 + tied

    /** Net run rate: runs per over scored minus runs per over conceded. */
    val netRunRate: Double
        get() {
            val scored = if (ballsFaced == 0) 0.0 else runsFor * 6.0 / ballsFaced
            val conceded = if (ballsBowled == 0) 0.0 else runsAgainst * 6.0 / ballsBowled
            return scored - conceded
        }
}

/** A player at the top of one of a tournament's stat lists. */
@Serializable
data class StatLeader(val name: String, val team: String, val value: Int, val profileId: String? = null)

@Serializable
data class KeyStats(
    val mostRuns: List<StatLeader> = emptyList(),
    val mostWickets: List<StatLeader> = emptyList(),
    val mostSixes: List<StatLeader> = emptyList(),
)

/** A tournament with its fixtures and everything worked out from them. */
@Serializable
data class TournamentDetails(
    val tournament: Tournament,
    val fixtures: List<Match> = emptyList(),
    /** One table per group, best first. Empty for formats without a league. */
    val tables: List<List<PointsRow>> = emptyList(),
    val keyStats: KeyStats = KeyStats(),
    /** The winner's team ID once the tournament is over. */
    val championId: String? = null,
)

@Serializable
data class TournamentSummary(
    val id: String,
    val name: String,
    val city: String,
    val format: TournamentFormat,
    val teams: Int,
    val isOrganiser: Boolean = false,
)
