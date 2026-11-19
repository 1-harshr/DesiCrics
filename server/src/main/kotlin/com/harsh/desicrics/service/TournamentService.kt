package com.harsh.desicrics.service

import com.harsh.desicrics.api.AddOrganiserRequest
import com.harsh.desicrics.api.CreateTournamentRequest
import com.harsh.desicrics.api.CustomFixtureRequest
import com.harsh.desicrics.api.MatchDetails
import com.harsh.desicrics.api.StartFixtureRequest
import com.harsh.desicrics.api.TournamentError
import com.harsh.desicrics.auth.FirebasePrincipal
import com.harsh.desicrics.live.MatchListener
import com.harsh.desicrics.model.EntryStatus
import com.harsh.desicrics.model.FixtureInfo
import com.harsh.desicrics.model.KeyStats
import com.harsh.desicrics.model.Match
import com.harsh.desicrics.model.MatchStatus
import com.harsh.desicrics.model.Organiser
import com.harsh.desicrics.model.OrganiserRole
import com.harsh.desicrics.model.StatLeader
import com.harsh.desicrics.model.Team
import com.harsh.desicrics.model.TeamRole
import com.harsh.desicrics.model.TeamSide
import com.harsh.desicrics.model.TossDecision
import com.harsh.desicrics.model.Tournament
import com.harsh.desicrics.model.TournamentDetails
import com.harsh.desicrics.model.TournamentFormat
import com.harsh.desicrics.model.TournamentSummary
import com.harsh.desicrics.model.TournamentTeam
import com.harsh.desicrics.scoring.ScoringEngine
import com.harsh.desicrics.store.MatchStore
import com.harsh.desicrics.store.PlayerStore
import com.harsh.desicrics.store.StatsStore
import com.harsh.desicrics.store.TeamStore
import com.harsh.desicrics.store.TournamentStore
import com.harsh.desicrics.store.UserStore
import com.harsh.desicrics.tournament.PlannedFixture
import com.harsh.desicrics.tournament.TournamentEngine
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Tournaments: entering teams, making fixtures with [TournamentEngine], and working out tables, stats and
 * the champion from the fixtures played. Listens to match changes so the next round appears as soon as
 * the last fixture of a round finishes.
 */
class TournamentService(
    private val tournaments: TournamentStore,
    private val matchStore: MatchStore,
    private val matches: MatchService,
    private val teams: TeamStore,
    private val players: PlayerStore,
    private val users: UserStore,
    private val stats: StatsStore,
    /** Tells followers when a fixture moves. */
    private val onRescheduled: suspend (Match) -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
) : MatchListener {

    /** One round at a time per tournament, so two fixtures finishing together can't both make the next round. */
    private val advancing = ConcurrentHashMap<String, Mutex>()

    suspend fun mine(user: FirebasePrincipal): List<TournamentSummary> = tournaments.forUser(user.uid)

    suspend fun create(user: FirebasePrincipal, request: CreateTournamentRequest): TournamentDetails {
        request.validate()?.let { throw ApiException(HttpStatusCode.BadRequest, it.message) }
        if (users.get(user.uid) == null) throw ApiException(HttpStatusCode.Conflict, "Set up your profile first")
        val tournament = Tournament(
            id = UUID.randomUUID().toString(),
            name = request.name.trim(),
            city = request.city.trim(),
            format = request.format,
            oversPerInnings = request.oversPerInnings,
            rules = request.rules,
            groups = request.groups,
            advance = request.advance,
            createdBy = user.uid,
            createdAt = clock(),
            startsAt = request.startsAt,
            registrationOpen = request.registrationOpen,
            organisers = listOf(Organiser(user.uid, "", OrganiserRole.ORGANISER)),
        )
        tournaments.create(tournament)
        return details(tournament.id)
    }

    suspend fun details(tournamentId: String): TournamentDetails {
        val tournament = tournament(tournamentId)
        val fixtures = matchStore.forTournament(tournamentId)
        val tables = when (tournament.format) {
            TournamentFormat.ROUND_ROBIN, TournamentFormat.BOX_LEAGUE, TournamentFormat.GROUPS_KNOCKOUT ->
                if (tournament.started) (0 until tournament.groups).map { TournamentEngine.pointsTable(tournament, fixtures, it) } else emptyList()
            else -> emptyList()
        }
        return TournamentDetails(
            tournament = tournament,
            fixtures = fixtures,
            tables = tables,
            keyStats = keyStats(fixtures),
            championId = if (tournament.started) TournamentEngine.champion(tournament, fixtures) else null,
            playerOfTournament = playerOfTournament(tournamentId),
        )
    }

    /** Organisers enter teams outright; a team's admin can enter it themselves while registration is open. */
    suspend fun enterTeam(user: FirebasePrincipal, tournamentId: String, teamId: String): TournamentDetails {
        val tournament = tournament(tournamentId)
        if (tournament.started) throw ApiException(HttpStatusCode.Conflict, "The tournament has started; its teams are fixed")
        val team = teams.get(teamId) ?: throw ApiException(HttpStatusCode.NotFound, "Team not found")
        val status = when {
            tournament.isOrganiser(user.uid) -> EntryStatus.CONFIRMED
            tournament.registrationOpen && team.roleOf(user.uid) == TeamRole.ADMIN -> EntryStatus.PENDING
            else -> throw ApiException(HttpStatusCode.Forbidden, "Only organisers, or the team's admins while registration is open, can enter a team")
        }
        val entry = TournamentTeam(teamId, team.name, seed = tournament.teams.size + 1, status = status)
        if (!tournaments.addTeam(tournamentId, entry)) throw ApiException(HttpStatusCode.Conflict, "That team is already in")
        return details(tournamentId)
    }

    suspend fun review(user: FirebasePrincipal, tournamentId: String, teamId: String, confirm: Boolean): TournamentDetails {
        val tournament = requireOrganiser(user, tournamentId)
        if (tournament.started) throw ApiException(HttpStatusCode.Conflict, "The tournament has started; its teams are fixed")
        if (confirm) tournaments.setTeamStatus(tournamentId, teamId, EntryStatus.CONFIRMED) else tournaments.removeTeam(tournamentId, teamId)
        return details(tournamentId)
    }

    suspend fun removeTeam(user: FirebasePrincipal, tournamentId: String, teamId: String): TournamentDetails = review(user, tournamentId, teamId, confirm = false)

    suspend fun addOrganiser(user: FirebasePrincipal, tournamentId: String, request: AddOrganiserRequest): TournamentDetails {
        requireOrganiser(user, tournamentId)
        val person = players.summaries(listOf(request.profileId))[request.profileId]
        if (person == null || person.isPlaceholder) throw ApiException(HttpStatusCode.NotFound, "Organisers need to be on the app")
        tournaments.addOrganiser(tournamentId, request.profileId, request.role)
        return details(tournamentId)
    }

    /** Fixes the teams (and groups) and makes the first fixtures. */
    suspend fun start(user: FirebasePrincipal, tournamentId: String): TournamentDetails {
        val tournament = requireOrganiser(user, tournamentId)
        val confirmed = tournament.confirmedTeams.sortedBy { it.seed }.mapIndexed { i, team -> team.copy(seed = i + 1) }
        val enough = when (tournament.format) {
            TournamentFormat.BEST_OF_THREE -> confirmed.size == 2
            TournamentFormat.GROUPS_KNOCKOUT -> confirmed.size >= maxOf(tournament.format.minTeams, tournament.groups * 2)
            else -> confirmed.size >= tournament.format.minTeams
        }
        if (!enough) throw ApiException(HttpStatusCode.Conflict, TournamentError.TOO_FEW_TEAMS.message)
        val placed = if (tournament.format == TournamentFormat.GROUPS_KNOCKOUT) TournamentEngine.assignGroups(confirmed, tournament.groups) else confirmed
        if (!tournaments.markStarted(tournamentId, placed)) throw ApiException(HttpStatusCode.Conflict, "The tournament has already started")
        val started = tournament.copy(teams = placed, started = true)
        createFixtures(started, TournamentEngine.firstRound(started))
        return details(tournamentId)
    }

    suspend fun addFixture(user: FirebasePrincipal, tournamentId: String, request: CustomFixtureRequest): TournamentDetails {
        val tournament = requireOrganiser(user, tournamentId)
        if (tournament.format != TournamentFormat.CUSTOM) throw ApiException(HttpStatusCode.Conflict, "Only custom tournaments take fixtures by hand")
        if (!tournament.started) throw ApiException(HttpStatusCode.Conflict, "Start the tournament first")
        val entered = tournament.confirmedTeams.map { it.teamId }
        if (request.teamAId == request.teamBId || request.teamAId !in entered || request.teamBId !in entered) {
            throw ApiException(HttpStatusCode.BadRequest, "Pick two different teams from the tournament")
        }
        val round = (matchStore.forTournament(tournamentId).maxOfOrNull { it.fixture!!.round } ?: 0) + 1
        createFixtures(tournament, listOf(PlannedFixture(request.stage, round, 1, request.teamAId, request.teamBId)), request.scheduledAt)
        return details(tournamentId)
    }

    suspend fun schedule(user: FirebasePrincipal, matchId: String, scheduledAt: Long?): MatchDetails {
        val fixture = matches.details(matchId).match
        val tournamentId = fixture.fixture?.tournamentId ?: throw ApiException(HttpStatusCode.Conflict, "Only tournament fixtures have a schedule")
        requireOrganiser(user, tournamentId)
        if (fixture.status != MatchStatus.SCHEDULED) throw ApiException(HttpStatusCode.Conflict, "That match has already started")
        val updated = matches.schedule(matchId, scheduledAt)
        if (fixture.scheduledAt != scheduledAt) onRescheduled(updated.match)
        return updated
    }

    /** Organisers start fixtures, and whoever starts one scores it. */
    suspend fun startFixture(user: FirebasePrincipal, matchId: String, request: StartFixtureRequest): MatchDetails {
        val match = matches.details(matchId).match
        val tournamentId = match.fixture?.tournamentId
        if (tournamentId != null) requireOrganiser(user, tournamentId)
        else if (match.createdBy != user.uid) throw ApiException(HttpStatusCode.Forbidden, "Only the organiser can start this match")
        return matches.startFixture(user, matchId, request)
    }

    /** When a fixture finishes, makes the next round if that completed one. */
    override suspend fun changed(before: MatchDetails?, after: MatchDetails) {
        val tournamentId = after.match.fixture?.tournamentId ?: return
        if (after.match.status != MatchStatus.COMPLETED || before?.match?.status == MatchStatus.COMPLETED) return
        advancing.getOrPut(tournamentId) { Mutex() }.withLock {
            val tournament = tournaments.get(tournamentId) ?: return
            createFixtures(tournament, TournamentEngine.nextRound(tournament, matchStore.forTournament(tournamentId)))
        }
    }

    private suspend fun createFixtures(tournament: Tournament, planned: List<PlannedFixture>, scheduledAt: Long? = null) {
        val names = tournament.teams.associate { it.teamId to it.name }
        for (fixture in planned) {
            matches.createFixture(
                Match(
                    id = UUID.randomUUID().toString(),
                    createdBy = tournament.createdBy,
                    createdAt = clock(),
                    teamA = Team(names.getValue(fixture.teamA), emptyList(), teamId = fixture.teamA),
                    teamB = Team(names.getValue(fixture.teamB), emptyList(), teamId = fixture.teamB),
                    oversPerInnings = tournament.oversPerInnings,
                    // Placeholders until the toss when the fixture starts.
                    tossWinner = TeamSide.A,
                    tossDecision = TossDecision.BAT,
                    status = MatchStatus.SCHEDULED,
                    rules = tournament.rules,
                    scheduledAt = scheduledAt,
                    fixture = FixtureInfo(tournament.id, fixture.stage, fixture.round, fixture.number, fixture.group),
                ),
            )
        }
    }

    /** Top five run scorers, wicket takers and six hitters across fixtures that have started. */
    private suspend fun keyStats(fixtures: List<Match>): KeyStats {
        data class Key(val profileId: String?, val name: String, val team: String)
        val runs = mutableMapOf<Key, Int>()
        val wickets = mutableMapOf<Key, Int>()
        val sixes = mutableMapOf<Key, Int>()
        for (fixture in fixtures.filter { it.status != MatchStatus.SCHEDULED }) {
            val details = matchStore.details(fixture.id) ?: continue
            val state = ScoringEngine.compute(details.match, details.deliveries, details.events)
            for (innings in state.innings) {
                for (card in innings.batters) {
                    val key = Key(card.player.profileId, card.player.name, innings.battingTeam.name)
                    runs.merge(key, card.runs, Int::plus)
                    sixes.merge(key, card.sixes, Int::plus)
                }
                for (card in innings.bowlers) {
                    wickets.merge(Key(card.player.profileId, card.player.name, innings.bowlingTeam.name), card.wickets, Int::plus)
                }
            }
        }
        // A linked player is one person across fixtures; a guest is known only by name and team.
        fun Map<Key, Int>.leaders() = entries
            .groupBy { it.key.profileId ?: "${it.key.name}|${it.key.team}" }
            .map { (_, entries) -> entries.first().key to entries.sumOf { it.value } }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(5)
            .map { (key, value) -> StatLeader(key.name, key.team, value, key.profileId) }
        return KeyStats(runs.leaders(), wickets.leaders(), sixes.leaders())
    }

    /** Most MVP points across the tournament, from the stats projection. */
    private suspend fun playerOfTournament(tournamentId: String): StatLeader? {
        val best = stats.forTournament(tournamentId).groupBy { it.profileId }
            .mapValues { (_, lines) -> lines.sumOf { it.mvpTenths } to lines.last().team }
            .maxByOrNull { it.value.first }?.takeIf { it.value.first > 0 } ?: return null
        val name = players.summaries(listOf(best.key))[best.key]?.name ?: return null
        return StatLeader(name, best.value.second, best.value.first, best.key)
    }

    private suspend fun tournament(id: String): Tournament =
        tournaments.get(id) ?: throw ApiException(HttpStatusCode.NotFound, "Tournament not found")

    private suspend fun requireOrganiser(user: FirebasePrincipal, tournamentId: String): Tournament {
        val tournament = tournament(tournamentId)
        if (!tournament.isOrganiser(user.uid)) throw ApiException(HttpStatusCode.Forbidden, "Only the tournament's organisers can do that")
        return tournament
    }
}
