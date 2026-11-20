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
enum class EntryStatus {
    CONFIRMED,
    /** Asked to enter; waiting for an organiser. */
    PENDING,
    /** An organiser asked them; waiting for the team's admin or the player to accept. */
    INVITED,
}

/** Who can enter a tournament, set separately for teams and for players. */
@Serializable
enum class EntryPolicy {
    /** Only organisers add entries. */
    ORGANISERS,
    /** Anyone can ask; an organiser confirms or rejects. */
    APPROVAL,
    /** Anyone is in straight away. */
    OPEN,
}

/**
 * A player in a tournament: in the pool while [teamId] is null, otherwise in that team's squad. A player is
 * in one squad per tournament. [fromPool] players entered on their own, so they go back to the pool if
 * their team leaves; the rest came in with their team and leave with it.
 */
@Serializable
data class TournamentPlayer(
    val player: PlayerSummary,
    val teamId: String? = null,
    val status: EntryStatus = EntryStatus.CONFIRMED,
    val fromPool: Boolean = false,
)

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
    /** Who can enter a team: its admins can ask, or join outright, unless only organisers add teams. */
    val teamEntry: EntryPolicy = EntryPolicy.ORGANISERS,
    /** Who can join as a player on their own, into the pool. */
    val playerEntry: EntryPolicy = EntryPolicy.ORGANISERS,
    val organisers: List<Organiser> = emptyList(),
    val teams: List<TournamentTeam> = emptyList(),
    /** The pool and every squad. */
    val players: List<TournamentPlayer> = emptyList(),
    /** Fixtures have been made; the team list is fixed from then on. */
    val started: Boolean = false,
) {
    val confirmedTeams: List<TournamentTeam> get() = teams.filter { it.status == EntryStatus.CONFIRMED }

    /** The fewest confirmed teams it can start with. Best of three takes exactly two. */
    val teamsNeeded: Int
        get() = when (format) {
            TournamentFormat.GROUPS_KNOCKOUT -> maxOf(format.minTeams, groups * 2)
            else -> format.minTeams
        }

    val canStart: Boolean
        get() = confirmedTeams.size.let { if (format == TournamentFormat.BEST_OF_THREE) it == 2 else it >= teamsNeeded }

    /** Best of three is between exactly two teams, so it takes no more once two are in. */
    val full: Boolean get() = format == TournamentFormat.BEST_OF_THREE && teams.size >= 2

    fun isOrganiser(uid: String?): Boolean = organisers.any { it.uid == uid }

    /** Teams can ask or join themselves, so it's worth inviting them. */
    val takesTeams: Boolean get() = !started && !full && teamEntry != EntryPolicy.ORGANISERS

    /** Players can ask or join the pool themselves. */
    val takesPlayers: Boolean get() = !started && playerEntry != EntryPolicy.ORGANISERS

    /** Players who entered on their own and aren't in a squad yet. */
    val pool: List<TournamentPlayer> get() = players.filter { it.teamId == null }

    fun squad(teamId: String): List<TournamentPlayer> = players.filter { it.teamId == teamId }

    fun entryOf(profileId: String?): TournamentPlayer? = players.firstOrNull { it.player.profileId == profileId }
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
    /** Abandoned: a point each, like a tie. */
    val noResult: Int = 0,
    val runsFor: Int = 0,
    val ballsFaced: Int = 0,
    val runsAgainst: Int = 0,
    val ballsBowled: Int = 0,
) {
    val points: Int get() = won * 2 + tied + noResult

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
    /** Most MVP points across the tournament's completed fixtures; value is tenths of a point. */
    val playerOfTournament: StatLeader? = null,
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
