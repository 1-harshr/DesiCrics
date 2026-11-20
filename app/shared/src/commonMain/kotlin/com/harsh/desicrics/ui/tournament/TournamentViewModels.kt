package com.harsh.desicrics.ui.tournament

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harsh.desicrics.api.CreateTournamentRequest
import com.harsh.desicrics.api.EntrySettingsRequest
import com.harsh.desicrics.data.AuctionRepository
import com.harsh.desicrics.data.AuthRepository
import com.harsh.desicrics.data.TeamRepository
import com.harsh.desicrics.data.TournamentRepository
import com.harsh.desicrics.model.AuctionDetails
import com.harsh.desicrics.model.EntryStatus
import com.harsh.desicrics.model.PlayerSummary
import com.harsh.desicrics.model.SavedTeam
import com.harsh.desicrics.model.TeamRole
import com.harsh.desicrics.model.TeamSummary
import com.harsh.desicrics.model.TournamentDetails
import com.harsh.desicrics.model.TournamentPlayer
import com.harsh.desicrics.model.TournamentTeam
import com.harsh.desicrics.model.TournamentSummary
import com.harsh.desicrics.ui.teams.attempt
import com.harsh.desicrics.util.nowMillis
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TournamentsUiState(
    val loading: Boolean = true,
    val tournaments: List<TournamentSummary> = emptyList(),
    /** Where to look for tournaments open to join; starts as the user's own city. */
    val city: String = "",
    /** Tournaments taking entries in [city], leaving out the user's own. */
    val open: List<TournamentSummary> = emptyList(),
    val loadError: String? = null,
    val saving: Boolean = false,
    val createFailed: Boolean = false,
)

class TournamentsViewModel(city: String, private val repository: TournamentRepository) : ViewModel() {
    private val _state = MutableStateFlow(TournamentsUiState(city = city))
    val state: StateFlow<TournamentsUiState> = _state
    private var openJob: Job? = null

    init {
        retry()
    }

    /** Loads the list, first time and after a failure. */
    fun retry() {
        _state.update { it.copy(loading = true, loadError = null) }
        viewModelScope.launch {
            attempt { repository.mine() }.fold(
                onSuccess = { list -> _state.update { it.copy(loading = false, tournaments = list) } },
                onFailure = { e -> _state.update { it.copy(loading = false, loadError = e.message ?: e::class.simpleName ?: "error") } },
            )
        }
        loadOpen()
    }

    /** Looks for open tournaments in another city, once the user stops typing. */
    fun setCity(city: String) {
        _state.update { it.copy(city = city.take(100)) }
        loadOpen(wait = true)
    }

    private fun loadOpen(wait: Boolean = false) {
        openJob?.cancel()
        openJob = viewModelScope.launch {
            if (wait) delay(400)
            attempt { repository.open(_state.value.city) }.getOrNull()?.let { list -> _state.update { it.copy(open = list) } }
        }
    }

    fun create(request: CreateTournamentRequest, onCreated: (String) -> Unit) {
        if (_state.value.saving) return
        if (request.validate() != null) {
            _state.update { it.copy(createFailed = true) }
            return
        }
        _state.update { it.copy(saving = true, createFailed = false) }
        viewModelScope.launch {
            val result = attempt { repository.create(request) }
            _state.update { it.copy(saving = false, createFailed = result.isFailure) }
            result.getOrNull()?.let { onCreated(it.tournament.id) }
        }
    }

    fun clearError() = _state.update { it.copy(createFailed = false) }
}

data class TournamentUiState(
    val myId: String? = null,
    val loading: Boolean = true,
    val details: TournamentDetails? = null,
    val loadError: String? = null,
    val saving: Boolean = false,
    val actionFailed: Boolean = false,
    /** The caller's saved teams, to enter one. */
    val myTeams: List<TeamSummary> = emptyList(),
    /** Organisers find other people's teams by name or code. */
    val teamQuery: String = "",
    val teamResults: List<TeamSummary> = emptyList(),
    /** People search, for adding an organiser or a player. */
    val peopleQuery: String = "",
    val peopleResults: List<PlayerSummary> = emptyList(),
    /** A team the caller runs, loaded to add its members to its squad. */
    val squadSource: SavedTeam? = null,
    /** The tournament's player auction, once loaded; null when it has none. */
    val auction: AuctionDetails? = null,
    val auctionSaving: Boolean = false,
) {
    val isOrganiser: Boolean get() = details?.tournament?.isOrganiser(myId) == true

    /** The caller's own place in the tournament as a player, if they have one. */
    val myEntry: TournamentPlayer? get() = details?.tournament?.entryOf(myId)

    /** Someone who isn't an organiser can ask to enter a team they run. */
    val canEnterTeam: Boolean get() = !isOrganiser && details?.tournament?.takesTeams(nowMillis()) == true && registrable.isNotEmpty()

    /** Anyone not in yet can join as a player while players can enter. */
    val canJoinAsPlayer: Boolean get() = myEntry == null && details?.tournament?.takesPlayers(nowMillis()) == true

    fun runs(teamId: String): Boolean = myTeams.any { it.id == teamId && it.myRole == TeamRole.ADMIN }

    /** Confirmed teams the caller runs, which can ask pool players to join while teams pick. */
    val myEnteredTeams: List<TournamentTeam> get() = details?.tournament?.confirmedTeams.orEmpty().filter { runs(it.teamId) }

    /** Teams asking the caller to join their squad. */
    val offersToMe: List<TournamentTeam>
        get() {
            val t = details?.tournament ?: return emptyList()
            return t.offers.filter { it.profileId == myId }.mapNotNull { offer -> t.teams.firstOrNull { it.teamId == offer.teamId } }
        }

    /** Teams the caller runs that an organiser invited, waiting for them to accept. */
    val myInvites: List<TournamentTeam> get() = details?.tournament?.teams.orEmpty().filter { it.status == EntryStatus.INVITED && runs(it.teamId) }

    /**
     * Teams that aren't in yet. Organisers can enter any team, so they also get what they searched for;
     * anyone else can only register a team they run.
     */
    val registrable: List<TeamSummary>
        get() {
            val entered = details?.tournament?.teams.orEmpty().map { it.teamId }.toSet()
            val candidates = if (isOrganiser) (teamResults + myTeams).distinctBy { it.id } else myTeams.filter { it.myRole == TeamRole.ADMIN }
            return candidates.filter { it.id !in entered }
        }
}

class TournamentViewModel(
    private val tournamentId: String,
    private val repository: TournamentRepository,
    private val teams: TeamRepository,
    auth: AuthRepository,
    private val auctions: AuctionRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(TournamentUiState(myId = auth.currentUser?.uid))
    val state: StateFlow<TournamentUiState> = _state
    private var searchJob: Job? = null
    private var teamSearchJob: Job? = null

    val link: String = repository.link(tournamentId)

    init {
        refresh()
        viewModelScope.launch {
            attempt { teams.myTeams() }.getOrNull()?.let { mine -> _state.update { it.copy(myTeams = mine) } }
        }
    }

    /** Fixtures and tables move as matches are played, so the screen reloads when it comes back into view. */
    fun refresh() {
        viewModelScope.launch {
            attempt { repository.details(tournamentId) }.fold(
                onSuccess = { details -> _state.update { it.copy(loading = false, details = details, loadError = null) } },
                onFailure = { e -> _state.update { it.copy(loading = false, loadError = if (it.details == null) e.message ?: e::class.simpleName ?: "error" else null) } },
            )
        }
        viewModelScope.launch {
            attempt { auctions.forTournament(tournamentId) }.getOrNull().let { auction ->
                // A failed load keeps what was there; a 404 comes back as null and clears it.
                if (auction != null || _state.value.auction == null) _state.update { it.copy(auction = auction) }
            }
        }
    }

    /** Sets up the tournament's auction with the default rules, then opens it. */
    fun createAuction(onCreated: (String) -> Unit) = auctionAct({ auctions.create(tournamentId) }, onDone = { onCreated(it.auction.id) })

    /** The caller asks to be put up in the auction, or takes the request back. */
    fun requestAuction(requested: Boolean) {
        val id = _state.value.auction?.auction?.id ?: return
        auctionAct({ auctions.setRequest(id, requested) })
    }

    private fun auctionAct(call: suspend () -> AuctionDetails, onDone: (AuctionDetails) -> Unit = {}) {
        if (_state.value.auctionSaving) return
        _state.update { it.copy(auctionSaving = true, actionFailed = false) }
        viewModelScope.launch {
            attempt { call() }.fold(
                onSuccess = { auction ->
                    _state.update { it.copy(auctionSaving = false, auction = auction) }
                    onDone(auction)
                },
                onFailure = { _state.update { it.copy(auctionSaving = false, actionFailed = true) } },
            )
        }
    }

    fun enterTeam(teamId: String, invite: Boolean = false) = act { repository.enterTeam(tournamentId, teamId, invite) }

    fun acceptTeamInvite(teamId: String) = act { repository.acceptTeamInvite(tournamentId, teamId) }

    fun withdrawTeam(teamId: String) = act { repository.withdrawTeam(tournamentId, teamId) }

    fun acceptInvite() {
        val me = _state.value.myId ?: return
        act { repository.acceptPlayerInvite(tournamentId, me) }
    }

    fun offer(profileId: String, teamId: String) = act { repository.offer(tournamentId, profileId, teamId) }

    fun withdrawOffer(profileId: String, teamId: String) = act { repository.removeOffer(tournamentId, profileId, teamId) }

    fun acceptOffer(teamId: String) {
        val me = _state.value.myId ?: return
        act { repository.acceptOffer(tournamentId, me, teamId) }
    }

    fun declineOffer(teamId: String) {
        val me = _state.value.myId ?: return
        act { repository.removeOffer(tournamentId, me, teamId) }
    }

    /** The caller leaves the tournament as a player, or turns down an invite. */
    fun leave() {
        val me = _state.value.myId ?: return
        act { repository.removePlayer(tournamentId, me) }
    }

    fun review(teamId: String, confirm: Boolean) = act { repository.review(tournamentId, teamId, confirm) }

    fun start() = act { repository.start(tournamentId) }

    fun addFixture(teamAId: String, teamBId: String) = act { repository.addFixture(tournamentId, teamAId, teamBId) }

    fun addOrganiser(profileId: String) = act { repository.addOrganiser(tournamentId, profileId) }

    fun pickup(names: List<String>, split: Boolean) = act { repository.pickup(tournamentId, names, split) }

    fun setEntry(settings: EntrySettingsRequest) = act { repository.setEntry(tournamentId, settings) }

    fun joinAsPlayer() = act { repository.addPlayer(tournamentId) }

    /** An organiser adds anyone (to the pool, or a squad); a team's admin adds its members to its squad. */
    fun addPlayer(profileId: String, teamId: String? = null, invite: Boolean = false) = act { repository.addPlayer(tournamentId, profileId, teamId, invite) }

    fun placePlayer(profileId: String, teamId: String?) = act { repository.placePlayer(tournamentId, profileId, teamId) }

    fun removePlayer(profileId: String) = act { repository.removePlayer(tournamentId, profileId) }

    /** Loads a team the caller runs, so they can add its members to its squad. */
    fun loadSquadSource(teamId: String?) {
        _state.update { it.copy(squadSource = null) }
        if (teamId == null) return
        viewModelScope.launch {
            attempt { teams.team(teamId) }.getOrNull()?.let { team -> _state.update { it.copy(squadSource = team) } }
        }
    }

    fun schedule(matchId: String, at: Long?) {
        viewModelScope.launch {
            if (attempt { repository.schedule(matchId, at) }.isFailure) _state.update { it.copy(actionFailed = true) }
            refresh()
        }
    }

    fun searchTeams(query: String) {
        _state.update { it.copy(teamQuery = query) }
        teamSearchJob?.cancel()
        if (query.trim().length < 2) {
            _state.update { it.copy(teamResults = emptyList()) }
            return
        }
        teamSearchJob = viewModelScope.launch {
            delay(300)
            val results = attempt { teams.searchTeams(query.trim()) }.getOrDefault(emptyList())
            _state.update { it.copy(teamResults = results) }
        }
    }

    fun searchPeople(query: String) {
        _state.update { it.copy(peopleQuery = query) }
        searchJob?.cancel()
        if (query.trim().length < 2) {
            _state.update { it.copy(peopleResults = emptyList()) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            val results = attempt { teams.search(query) }.getOrDefault(emptyList())
            _state.update { it.copy(peopleResults = results) }
        }
    }

    fun clearError() = _state.update { it.copy(actionFailed = false) }

    private fun act(call: suspend () -> TournamentDetails) {
        if (_state.value.saving) return
        _state.update { it.copy(saving = true, actionFailed = false) }
        viewModelScope.launch {
            attempt { call() }.fold(
                onSuccess = { details -> _state.update { it.copy(saving = false, details = details) } },
                onFailure = { _state.update { it.copy(saving = false, actionFailed = true) } },
            )
        }
    }
}
