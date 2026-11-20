package com.harsh.desicrics.service
import com.harsh.desicrics.api.EndBeforeStartRequest

import com.harsh.desicrics.api.AddOrganiserRequest
import com.harsh.desicrics.api.AddTournamentPlayerRequest
import com.harsh.desicrics.api.EntrySettingsRequest
import com.harsh.desicrics.api.PickupTeamsRequest
import com.harsh.desicrics.api.PlacePlayerRequest
import com.harsh.desicrics.api.CreateTournamentRequest
import com.harsh.desicrics.api.CustomFixtureRequest
import com.harsh.desicrics.api.MatchDetails
import com.harsh.desicrics.api.StartFixtureRequest
import com.harsh.desicrics.api.TournamentError
import com.harsh.desicrics.auth.FirebasePrincipal
import com.harsh.desicrics.live.MatchListener
import com.harsh.desicrics.model.EntryPolicy
import com.harsh.desicrics.model.EntryStatus
import com.harsh.desicrics.model.FixtureInfo
import com.harsh.desicrics.model.KeyStats
import com.harsh.desicrics.model.Match
import com.harsh.desicrics.model.MatchStatus
import com.harsh.desicrics.model.NotificationKind
import com.harsh.desicrics.model.Organiser
import com.harsh.desicrics.model.PlayerSummary
import com.harsh.desicrics.model.PoolMode
import com.harsh.desicrics.model.SavedTeam
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
import com.harsh.desicrics.tournament.SquadSplit
import com.harsh.desicrics.tournament.TournamentEngine
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** What [TournamentService] tells people: the entry's [name] (a team or a player) and whether it's a "team" or "player". */
typealias TournamentNotice =
    suspend (uids: Set<String>, kind: NotificationKind, tournamentId: String, tournamentName: String, name: String, detail: String, value: Int?) -> Unit

/**
 * Tournaments: entering teams and players, making fixtures with [TournamentEngine], and working out tables, stats and
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
    /** Tells organisers about entries, and entrants what became of them; see [com.harsh.desicrics.live.Notifier.tournamentNotice]. */
    private val notify: TournamentNotice = { _, _, _, _, _, _, _ -> },
    /** A player is confirmed in the pool, waiting for a team: the tournament's auction takes them too. */
    private val onPoolJoined: suspend (tournamentId: String, profileId: String) -> Unit = { _, _ -> },
    private val clock: () -> Long = System::currentTimeMillis,
) : MatchListener {

    /** One round at a time per tournament, so two fixtures finishing together can't both make the next round. */
    private val advancing = ConcurrentHashMap<String, Mutex>()

    suspend fun mine(user: FirebasePrincipal): List<TournamentSummary> = tournaments.forUser(user.uid)

    /** Tournaments anyone can ask to enter now, in [city] or anywhere. */
    suspend fun open(city: String): List<TournamentSummary> = tournaments.open(city.take(100), clock(), OPEN_PAGE)

    /** Every tournament in [city] unless it's blank, newest first, for the web app. */
    suspend fun browse(city: String): List<TournamentSummary> = tournaments.browse(city.take(100), BROWSE_PAGE)

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
            teamEntry = request.teamEntry,
            playerEntry = request.playerEntry,
            maxTeams = request.maxTeams,
            maxPlayers = request.maxPlayers,
            entryClosesAt = request.entryClosesAt,
            poolMode = request.poolMode,
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

    /**
     * Makes teams for players who came on their own, run by the organiser, and enters them. With
     * [PickupTeamsRequest.split] the pool is shared out evenly by [SquadSplit]: strength is career runs, plus
     * 20 a wicket.
     */
    suspend fun pickup(user: FirebasePrincipal, tournamentId: String, request: PickupTeamsRequest): TournamentDetails {
        request.validate()?.let { throw ApiException(HttpStatusCode.BadRequest, it.message) }
        val tournament = requireOrganiser(user, tournamentId)
        if (tournament.started) throw ApiException(HttpStatusCode.Conflict, "The tournament has started; its teams are fixed")
        if (tournament.format == TournamentFormat.BEST_OF_THREE && tournament.teams.size + request.names.size > 2) {
            throw ApiException(HttpStatusCode.Conflict, "Best of three is between two teams")
        }
        val made = request.names.mapIndexed { i, name ->
            val id = UUID.randomUUID().toString()
            withFreshCode { code ->
                val team = SavedTeam(id, name.trim(), tournament.city, user.uid, members = emptyList(), joinCode = code, code = newPublicCode())
                teams.create(team, adminId = user.uid, at = clock()).takeIf { it }
            }
            tournaments.addTeam(tournamentId, TournamentTeam(id, name.trim(), seed = tournament.teams.size + i + 1, status = EntryStatus.CONFIRMED))
            id to name.trim()
        }
        if (request.split) {
            val pool = tournament.pool.filter { it.status == EntryStatus.CONFIRMED }.map { it.player }
            val careers = stats.forProfiles(pool.map { it.profileId }).groupBy { it.profileId }
            val strength = { p: PlayerSummary -> careers[p.profileId].orEmpty().sumOf { it.runs + 20 * it.wickets } }
            val names = made.toMap()
            for ((profileId, teamId) in SquadSplit.split(pool, made.map { it.first }, strength)) {
                tournaments.setPlayer(tournamentId, profileId, teamId, EntryStatus.CONFIRMED)
                tell(setOf(profileId) - user.uid, NotificationKind.SQUAD_PLACED, tournament, names.getValue(teamId), PLAYER)
            }
        }
        return details(tournamentId)
    }

    /**
     * A day before entry closes, tells the organisers how many entries still wait for them, once per closing
     * time. Run every few minutes; tournaments with nothing waiting are passed over quietly.
     */
    suspend fun remindClosing() {
        val now = clock()
        for (id in tournaments.claimReminders(now, now + DAY_MILLIS)) {
            val t = tournaments.get(id) ?: continue
            val waiting = t.teams.count { it.status == EntryStatus.PENDING } + t.players.count { it.status == EntryStatus.PENDING }
            if (waiting > 0) tell(t.organiserIds(), NotificationKind.ENTRY_CLOSING, t, t.name, TEAM, value = waiting)
        }
    }

    /** Who can enter, how many, and until when. Fixed once the tournament starts. Raising a limit lets waiting entries in. */
    suspend fun setEntry(user: FirebasePrincipal, tournamentId: String, request: EntrySettingsRequest): TournamentDetails {
        request.validate()?.let { throw ApiException(HttpStatusCode.BadRequest, it.message) }
        val tournament = requireOrganiser(user, tournamentId)
        if (tournament.started) throw ApiException(HttpStatusCode.Conflict, "The tournament has started; entries are closed")
        tournaments.setEntry(tournamentId, request)
        promote(tournamentId)
        return details(tournamentId)
    }

    /**
     * Organisers enter teams outright, or [invite] them to accept. A team's admin can ask to enter it, or
     * enter it outright where entry is open, unless only organisers add teams. A confirmed team's members
     * make up its squad.
     */
    suspend fun enterTeam(user: FirebasePrincipal, tournamentId: String, teamId: String, invite: Boolean = false): TournamentDetails {
        val tournament = tournament(tournamentId)
        if (tournament.started) throw ApiException(HttpStatusCode.Conflict, "The tournament has started; its teams are fixed")
        if (tournament.full) throw ApiException(HttpStatusCode.Conflict, "Best of three is between two teams, and both are in")
        val team = teams.get(teamId) ?: throw ApiException(HttpStatusCode.NotFound, "Team not found")
        val organiser = tournament.isOrganiser(user.uid)
        val status = when {
            organiser -> if (invite) EntryStatus.INVITED else EntryStatus.CONFIRMED
            invite -> throw ApiException(HttpStatusCode.Forbidden, "Only organisers send invites")
            team.roleOf(user.uid) != TeamRole.ADMIN -> throw ApiException(HttpStatusCode.Forbidden, "Only the team's admins can enter it")
            tournament.teamEntry == EntryPolicy.ORGANISERS -> throw ApiException(HttpStatusCode.Forbidden, "Only organisers add teams to this tournament")
            tournament.entryClosed(clock()) -> throw ApiException(HttpStatusCode.Conflict, "Entries for this tournament have closed")
            tournament.teamsFull -> EntryStatus.WAITLISTED
            tournament.teamEntry == EntryPolicy.OPEN -> EntryStatus.CONFIRMED
            else -> EntryStatus.PENDING
        }
        val entry = TournamentTeam(teamId, team.name, seed = tournament.teams.size + 1, status = status)
        if (!tournaments.addTeam(tournamentId, entry)) throw ApiException(HttpStatusCode.Conflict, "That team is already in")
        if (status == EntryStatus.CONFIRMED) fillSquad(user.uid, tournamentId, team)
        when {
            status == EntryStatus.WAITLISTED -> Unit
            invite -> tell(team.admins() - user.uid, NotificationKind.ENTRY_INVITED, tournament, team.name, TEAM)
            organiser -> tell(team.admins() - user.uid, NotificationKind.ENTRY_CONFIRMED, tournament, team.name, TEAM)
            status == EntryStatus.PENDING -> tell(tournament.organiserIds() - user.uid, NotificationKind.ENTRY_REQUEST, tournament, team.name, TEAM)
            else -> tell(tournament.organiserIds() - user.uid, NotificationKind.ENTRY_JOINED, tournament, team.name, TEAM)
        }
        return details(tournamentId)
    }

    suspend fun review(user: FirebasePrincipal, tournamentId: String, teamId: String, confirm: Boolean): TournamentDetails {
        val tournament = requireOrganiser(user, tournamentId)
        if (tournament.started) throw ApiException(HttpStatusCode.Conflict, "The tournament has started; its teams are fixed")
        val entry = tournament.teams.firstOrNull { it.teamId == teamId } ?: throw ApiException(HttpStatusCode.NotFound, "That team isn't in the tournament")
        val team = teams.get(teamId)
        if (confirm) {
            tournaments.setTeamStatus(tournamentId, teamId, EntryStatus.CONFIRMED)
            if (entry.status != EntryStatus.CONFIRMED && team != null) {
                fillSquad(user.uid, tournamentId, team)
                tell(team.admins() - user.uid, NotificationKind.ENTRY_CONFIRMED, tournament, team.name, TEAM)
            }
        } else {
            tournaments.removeTeam(tournamentId, teamId)
            if (entry.status == EntryStatus.PENDING && team != null) {
                tell(team.admins() - user.uid, NotificationKind.ENTRY_REJECTED, tournament, team.name, TEAM)
            }
            promote(tournamentId)
        }
        return details(tournamentId)
    }

    /** Organisers take a team out; its admins can withdraw it, or turn down an invite, until the start. */
    suspend fun removeTeam(user: FirebasePrincipal, tournamentId: String, teamId: String): TournamentDetails {
        val tournament = tournament(tournamentId)
        if (tournament.isOrganiser(user.uid)) return review(user, tournamentId, teamId, confirm = false)
        val team = requireTeamAdmin(user, tournament, teamId)
        tournaments.removeTeam(tournamentId, teamId)
        tell(tournament.organiserIds() - user.uid, NotificationKind.ENTRY_WITHDRAWN, tournament, team.name, TEAM)
        promote(tournamentId)
        return details(tournamentId)
    }

    /** A team's admin accepts an organiser's invite; the team is in, with its members as its squad. */
    suspend fun acceptTeam(user: FirebasePrincipal, tournamentId: String, teamId: String): TournamentDetails {
        val tournament = tournament(tournamentId)
        val team = requireTeamAdmin(user, tournament, teamId)
        if (tournament.teams.first { it.teamId == teamId }.status != EntryStatus.INVITED) throw ApiException(HttpStatusCode.Conflict, "There's no invite to accept")
        tournaments.setTeamStatus(tournamentId, teamId, EntryStatus.CONFIRMED)
        fillSquad(user.uid, tournamentId, team)
        tell(tournament.organiserIds() - user.uid, NotificationKind.ENTRY_JOINED, tournament, team.name, TEAM)
        return details(tournamentId)
    }

    /** A player accepts an organiser's invite, into the pool or the squad they were invited to. */
    suspend fun acceptPlayer(user: FirebasePrincipal, tournamentId: String, profileId: String): TournamentDetails {
        val tournament = tournament(tournamentId)
        if (profileId != user.uid) throw ApiException(HttpStatusCode.Forbidden, "Only the player can accept their invite")
        if (tournament.started) throw ApiException(HttpStatusCode.Conflict, "The tournament has started")
        val entry = tournament.entryOf(profileId)
        if (entry?.status != EntryStatus.INVITED) throw ApiException(HttpStatusCode.Conflict, "There's no invite to accept")
        tournaments.setPlayer(tournamentId, profileId, entry.teamId, EntryStatus.CONFIRMED)
        if (entry.teamId == null) onPoolJoined(tournamentId, profileId)
        tell(tournament.organiserIds() - user.uid, NotificationKind.ENTRY_JOINED, tournament, entry.player.name, PLAYER)
        return details(tournamentId)
    }

    /**
     * Adds a player. With no profile ID the caller joins the pool, as [Tournament.playerEntry] allows.
     * Organisers add anyone, to the pool or straight into a squad, at any time; a team's admins add its
     * members to its squad until the tournament starts.
     */
    suspend fun addPlayer(user: FirebasePrincipal, tournamentId: String, request: AddTournamentPlayerRequest): TournamentDetails {
        val tournament = tournament(tournamentId)
        val organiser = tournament.isOrganiser(user.uid)
        val profileId = request.profileId ?: user.uid
        val person = players.summaries(listOf(profileId))[profileId] ?: throw ApiException(HttpStatusCode.NotFound, "Player not found")
        val squadTeam = request.teamId?.let { teamId ->
            if (tournament.confirmedTeams.none { it.teamId == teamId }) throw ApiException(HttpStatusCode.BadRequest, "That team isn't in the tournament")
            teams.get(teamId) ?: throw ApiException(HttpStatusCode.NotFound, "Team not found")
        }
        if (tournament.started && !organiser) throw ApiException(HttpStatusCode.Conflict, "The tournament has started; only organisers change squads now")
        val status = when {
            organiser && request.invite -> {
                if (request.profileId == null || request.profileId == user.uid) throw ApiException(HttpStatusCode.BadRequest, "Invite someone else")
                if (person.isPlaceholder) throw ApiException(HttpStatusCode.BadRequest, "Only players on the app can accept an invite; add them instead")
                EntryStatus.INVITED
            }
            organiser -> EntryStatus.CONFIRMED
            request.invite -> throw ApiException(HttpStatusCode.Forbidden, "Only organisers send invites")
            request.profileId == null && squadTeam == null -> {
                if (users.get(user.uid) == null) throw ApiException(HttpStatusCode.Conflict, "Set up your profile first")
                when {
                    tournament.playerEntry == EntryPolicy.ORGANISERS -> throw ApiException(HttpStatusCode.Forbidden, "Only organisers add players to this tournament")
                    tournament.entryClosed(clock()) -> throw ApiException(HttpStatusCode.Conflict, "Entries for this tournament have closed")
                    tournament.playersFull -> EntryStatus.WAITLISTED
                    tournament.playerEntry == EntryPolicy.APPROVAL -> EntryStatus.PENDING
                    else -> EntryStatus.CONFIRMED
                }
            }
            squadTeam != null && squadTeam.roleOf(user.uid) == TeamRole.ADMIN -> {
                if (squadTeam.roleOf(profileId) == null) throw ApiException(HttpStatusCode.Forbidden, "Add players from your team; ask an organiser for anyone else")
                EntryStatus.CONFIRMED
            }
            else -> throw ApiException(HttpStatusCode.Forbidden, "Only organisers, or the team's admins, can add players to a squad")
        }
        // Organisers' picks and players joining alone go back to the pool if their team leaves.
        val fromPool = organiser || squadTeam == null
        if (!tournaments.addPlayer(tournamentId, profileId, squadTeam?.id, status, fromPool, user.uid, clock())) {
            val where = tournament.entryOf(profileId)?.teamId?.let { id -> tournament.teams.firstOrNull { it.teamId == id }?.name }
            throw ApiException(HttpStatusCode.Conflict, if (where != null) "${person.name} is already in $where's squad" else "${person.name} is already in the tournament")
        }
        if (status == EntryStatus.CONFIRMED && squadTeam == null) onPoolJoined(tournamentId, profileId)
        if (tournament.started && squadTeam != null) tournaments.addSquadChange(tournamentId, squadTeam.id, profileId, added = true, user.uid, clock())
        when {
            status == EntryStatus.WAITLISTED -> Unit
            status == EntryStatus.INVITED -> tell(setOf(profileId), NotificationKind.ENTRY_INVITED, tournament, person.name, PLAYER)
            profileId != user.uid && organiser && squadTeam != null ->
                tell(setOf(profileId), NotificationKind.SQUAD_PLACED, tournament, squadTeam.name, PLAYER)
            profileId != user.uid && organiser -> tell(setOf(profileId), NotificationKind.ENTRY_CONFIRMED, tournament, person.name, PLAYER)
            profileId == user.uid && !organiser -> {
                val kind = if (status == EntryStatus.PENDING) NotificationKind.ENTRY_REQUEST else NotificationKind.ENTRY_JOINED
                tell(tournament.organiserIds() - user.uid, kind, tournament, person.name, PLAYER)
            }
        }
        return details(tournamentId)
    }

    /**
     * A team's admin asks a pool player to join its squad, while teams pick from the pool. Each team asks
     * once; the player takes one offer.
     */
    suspend fun offer(user: FirebasePrincipal, tournamentId: String, profileId: String, teamId: String): TournamentDetails {
        val tournament = tournament(tournamentId)
        if (tournament.poolMode != PoolMode.TEAMS) throw ApiException(HttpStatusCode.Conflict, "Organisers pick teams for players in this tournament")
        if (tournament.confirmedTeams.none { it.teamId == teamId }) throw ApiException(HttpStatusCode.BadRequest, "That team isn't in the tournament")
        val team = requireTeamAdmin(user, tournament, teamId)
        val entry = tournament.entryOf(profileId)
        if (entry == null || entry.teamId != null || entry.status != EntryStatus.CONFIRMED) {
            throw ApiException(HttpStatusCode.Conflict, "Only players waiting for a team can be asked")
        }
        if (!tournaments.addOffer(tournamentId, profileId, teamId, user.uid, clock())) throw ApiException(HttpStatusCode.Conflict, "Your team has already asked")
        tell(setOf(profileId) - user.uid, NotificationKind.SQUAD_OFFER, tournament, team.name, PLAYER)
        return details(tournamentId)
    }

    /** The player takes a team's offer and joins its squad; its admins hear, and other offers go. */
    suspend fun acceptOffer(user: FirebasePrincipal, tournamentId: String, profileId: String, teamId: String): TournamentDetails {
        val tournament = tournament(tournamentId)
        if (profileId != user.uid) throw ApiException(HttpStatusCode.Forbidden, "Only the player can take an offer")
        if (tournament.started) throw ApiException(HttpStatusCode.Conflict, "The tournament has started")
        if (tournament.offers.none { it.profileId == profileId && it.teamId == teamId }) throw ApiException(HttpStatusCode.NotFound, "That offer has gone")
        val entry = tournament.entryOf(profileId) ?: throw ApiException(HttpStatusCode.NotFound, "That player isn't in the tournament")
        tournaments.setPlayer(tournamentId, profileId, teamId, EntryStatus.CONFIRMED)
        teams.get(teamId)?.let { team -> tell(team.admins() - user.uid, NotificationKind.SQUAD_JOINED, tournament, entry.player.name, PLAYER) }
        return details(tournamentId)
    }

    /** The player turns an offer down, or the team's admin takes it back. */
    suspend fun removeOffer(user: FirebasePrincipal, tournamentId: String, profileId: String, teamId: String): TournamentDetails {
        val tournament = tournament(tournamentId)
        if (profileId != user.uid && teams.get(teamId)?.roleOf(user.uid) != TeamRole.ADMIN) {
            throw ApiException(HttpStatusCode.Forbidden, "Only the player or the team's admins can do that")
        }
        tournaments.removeOffer(tournamentId, profileId, teamId)
        return details(tournament.id)
    }

    /** Confirms a waiting player and puts them in a squad, or back in the pool. Organisers only. */
    suspend fun placePlayer(user: FirebasePrincipal, tournamentId: String, profileId: String, request: PlacePlayerRequest): TournamentDetails {
        val tournament = requireOrganiser(user, tournamentId)
        val entry = tournament.entryOf(profileId) ?: throw ApiException(HttpStatusCode.NotFound, "That player isn't in the tournament")
        val team = request.teamId?.let { teamId ->
            tournament.confirmedTeams.firstOrNull { it.teamId == teamId } ?: throw ApiException(HttpStatusCode.BadRequest, "That team isn't in the tournament")
        }
        tournaments.setPlayer(tournamentId, profileId, team?.teamId, EntryStatus.CONFIRMED)
        if (team == null && entry.status != EntryStatus.CONFIRMED) onPoolJoined(tournamentId, profileId)
        if (tournament.started && team?.teamId != entry.teamId) {
            entry.teamId?.let { tournaments.addSquadChange(tournamentId, it, profileId, added = false, user.uid, clock()) }
            team?.let { tournaments.addSquadChange(tournamentId, it.teamId, profileId, added = true, user.uid, clock()) }
        }
        when {
            team != null && team.teamId != entry.teamId -> tell(setOf(profileId) - user.uid, NotificationKind.SQUAD_PLACED, tournament, team.name, PLAYER)
            entry.status == EntryStatus.PENDING -> tell(setOf(profileId) - user.uid, NotificationKind.ENTRY_CONFIRMED, tournament, entry.player.name, PLAYER)
        }
        return details(tournamentId)
    }

    /**
     * Takes a player out. Organisers can at any time; until the tournament starts, players can leave and a
     * team's admins can drop players from its squad (anyone who joined on their own goes back to the pool).
     */
    suspend fun removePlayer(user: FirebasePrincipal, tournamentId: String, profileId: String): TournamentDetails {
        val tournament = tournament(tournamentId)
        val entry = tournament.entryOf(profileId) ?: throw ApiException(HttpStatusCode.NotFound, "That player isn't in the tournament")
        val organiser = tournament.isOrganiser(user.uid)
        val teamAdmin = entry.teamId?.let { teams.get(it) }?.roleOf(user.uid) == TeamRole.ADMIN
        when {
            organiser || profileId == user.uid -> {
                if (!organiser && tournament.started) throw ApiException(HttpStatusCode.Conflict, "The tournament has started; ask an organiser")
                tournaments.removePlayer(tournamentId, profileId)
                if (tournament.started) entry.teamId?.let { tournaments.addSquadChange(tournamentId, it, profileId, added = false, user.uid, clock()) }
                if (organiser && entry.status == EntryStatus.PENDING) {
                    tell(setOf(profileId) - user.uid, NotificationKind.ENTRY_REJECTED, tournament, entry.player.name, PLAYER)
                } else if (!organiser) {
                    tell(tournament.organiserIds(), NotificationKind.ENTRY_WITHDRAWN, tournament, entry.player.name, PLAYER)
                }
            }
            teamAdmin -> {
                if (tournament.started) throw ApiException(HttpStatusCode.Conflict, "The tournament has started; ask an organiser")
                if (entry.fromPool) tournaments.setPlayer(tournamentId, profileId, null, entry.status) else tournaments.removePlayer(tournamentId, profileId)
            }
            else -> throw ApiException(HttpStatusCode.Forbidden, "Only organisers, the player or their team's admins can do that")
        }
        if (!tournament.started) promote(tournamentId)
        return details(tournamentId)
    }

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
        if (!tournament.canStart) throw ApiException(HttpStatusCode.Conflict, TournamentError.TOO_FEW_TEAMS.message)
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

    /**
     * Organisers start fixtures, and whoever starts one scores it. A team with a tournament squad can only
     * field players from it, though guests (no profile) can stand in.
     */
    suspend fun startFixture(user: FirebasePrincipal, matchId: String, request: StartFixtureRequest): MatchDetails {
        val match = matches.details(matchId).match
        val tournamentId = match.fixture?.tournamentId
        if (tournamentId != null) {
            val tournament = requireOrganiser(user, tournamentId)
            for (side in listOf(request.teamA, request.teamB)) {
                val squad = side.teamId?.let { tournament.squad(it) }.orEmpty().map { it.player.profileId }.toSet()
                // Teams entered before squads existed have none, and play from their whole team.
                if (squad.isEmpty()) continue
                side.players.firstOrNull { it.profileId != null && it.profileId !in squad }?.let {
                    throw ApiException(HttpStatusCode.BadRequest, "${it.name} isn't in ${side.name}'s squad for this tournament")
                }
            }
        } else if (match.createdBy != user.uid) throw ApiException(HttpStatusCode.Forbidden, "Only the organiser can start this match")
        return matches.startFixture(user, matchId, request)
    }

    /** A walkover or call-off for a fixture that never started; same people as [startFixture]. */
    suspend fun endBeforeStart(user: FirebasePrincipal, matchId: String, request: EndBeforeStartRequest): MatchDetails {
        val match = matches.details(matchId).match
        val tournamentId = match.fixture?.tournamentId
        if (tournamentId != null) requireOrganiser(user, tournamentId)
        else if (match.createdBy != user.uid) throw ApiException(HttpStatusCode.Forbidden, "Only the organiser can end this match")
        return matches.endBeforeStart(user, matchId, request)
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

    private suspend fun fillSquad(addedBy: String, tournamentId: String, team: SavedTeam) {
        tournaments.fillSquad(tournamentId, team.id, team.members.map { it.player.profileId }, addedBy, clock())
    }

    /**
     * Moves waiting entries up, oldest first, while there's room: straight in where entry is open (and they're
     * told), otherwise on to the organisers' list to confirm (and both hear).
     */
    private suspend fun promote(tournamentId: String) {
        while (true) {
            val t = tournament(tournamentId)
            if (t.started) return
            val team = t.teams.firstOrNull { it.status == EntryStatus.WAITLISTED }?.takeIf { !t.teamsFull }
            val player = t.players.firstOrNull { it.status == EntryStatus.WAITLISTED }?.takeIf { !t.playersFull }
            if (team == null && player == null) return
            if (team != null) {
                val open = t.teamEntry == EntryPolicy.OPEN
                tournaments.setTeamStatus(tournamentId, team.teamId, if (open) EntryStatus.CONFIRMED else EntryStatus.PENDING)
                val saved = teams.get(team.teamId) ?: continue
                if (open) {
                    fillSquad(t.createdBy, tournamentId, saved)
                    tell(saved.admins(), NotificationKind.ENTRY_CONFIRMED, t, team.name, TEAM)
                } else {
                    tell(saved.admins(), NotificationKind.ENTRY_PROMOTED, t, team.name, TEAM)
                    tell(t.organiserIds(), NotificationKind.ENTRY_REQUEST, t, team.name, TEAM)
                }
            }
            if (player != null) {
                val open = t.playerEntry == EntryPolicy.OPEN
                val id = player.player.profileId
                tournaments.setPlayer(tournamentId, id, null, if (open) EntryStatus.CONFIRMED else EntryStatus.PENDING)
                if (open) {
                    onPoolJoined(tournamentId, id)
                    tell(setOf(id), NotificationKind.ENTRY_CONFIRMED, t, player.player.name, PLAYER)
                } else {
                    tell(setOf(id), NotificationKind.ENTRY_PROMOTED, t, player.player.name, PLAYER)
                    tell(t.organiserIds(), NotificationKind.ENTRY_REQUEST, t, player.player.name, PLAYER)
                }
            }
        }
    }

    private suspend fun tell(uids: Set<String>, kind: NotificationKind, tournament: Tournament, name: String, detail: String, value: Int? = null) {
        if (uids.isNotEmpty()) notify(uids, kind, tournament.id, tournament.name, name, detail, value)
    }

    /** The caller runs [teamId], which is entered, and the tournament hasn't started. */
    private suspend fun requireTeamAdmin(user: FirebasePrincipal, tournament: Tournament, teamId: String): SavedTeam {
        if (tournament.teams.none { it.teamId == teamId }) throw ApiException(HttpStatusCode.NotFound, "That team isn't in the tournament")
        val team = teams.get(teamId) ?: throw ApiException(HttpStatusCode.NotFound, "Team not found")
        if (team.roleOf(user.uid) != TeamRole.ADMIN) throw ApiException(HttpStatusCode.Forbidden, "Only organisers or the team's admins can do that")
        if (tournament.started) throw ApiException(HttpStatusCode.Conflict, "The tournament has started; its teams are fixed")
        return team
    }

    private fun Tournament.organiserIds(): Set<String> = organisers.map { it.uid }.toSet()

    /** Admins on the app; placeholders can't be told anything. */
    private fun SavedTeam.admins(): Set<String> =
        members.filter { it.role == TeamRole.ADMIN && !it.player.isPlaceholder }.map { it.player.profileId }.toSet()

    private suspend fun tournament(id: String): Tournament =
        tournaments.get(id) ?: throw ApiException(HttpStatusCode.NotFound, "Tournament not found")

    private companion object {
        const val TEAM = "team"
        const val PLAYER = "player"
        const val DAY_MILLIS = 24 * 60 * 60 * 1000L
        /** The most tournaments open to join listed at once. */
        const val OPEN_PAGE = 30
        const val BROWSE_PAGE = 60
    }

    private suspend fun requireOrganiser(user: FirebasePrincipal, tournamentId: String): Tournament {
        val tournament = tournament(tournamentId)
        if (!tournament.isOrganiser(user.uid)) throw ApiException(HttpStatusCode.Forbidden, "Only the tournament's organisers can do that")
        return tournament
    }
}
