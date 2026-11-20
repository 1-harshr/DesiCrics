package com.harsh.desicrics.ui.tournament

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harsh.desicrics.api.CreateTournamentRequest
import com.harsh.desicrics.data.AuctionRepository
import com.harsh.desicrics.data.AuthRepository
import com.harsh.desicrics.data.TeamRepository
import com.harsh.desicrics.data.TournamentRepository
import com.harsh.desicrics.model.AuctionDetails
import com.harsh.desicrics.model.PlayerSummary
import com.harsh.desicrics.model.TeamRole
import com.harsh.desicrics.model.TeamSummary
import com.harsh.desicrics.model.TournamentDetails
import com.harsh.desicrics.model.TournamentSummary
import com.harsh.desicrics.ui.teams.attempt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TournamentsUiState(
    val loading: Boolean = true,
    val tournaments: List<TournamentSummary> = emptyList(),
    val loadFailed: Boolean = false,
    val saving: Boolean = false,
    val createFailed: Boolean = false,
)

class TournamentsViewModel(private val repository: TournamentRepository) : ViewModel() {
    private val _state = MutableStateFlow(TournamentsUiState())
    val state: StateFlow<TournamentsUiState> = _state

    init {
        retry()
    }

    /** Loads the list, first time and after a failure. */
    fun retry() {
        _state.update { it.copy(loading = true, loadFailed = false) }
        viewModelScope.launch {
            attempt { repository.mine() }.fold(
                onSuccess = { list -> _state.update { it.copy(loading = false, tournaments = list) } },
                onFailure = { _state.update { it.copy(loading = false, loadFailed = true) } },
            )
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
    val loadFailed: Boolean = false,
    val saving: Boolean = false,
    val actionFailed: Boolean = false,
    /** The caller's saved teams, to enter one. */
    val myTeams: List<TeamSummary> = emptyList(),
    /** Organisers find other people's teams by name or code. */
    val teamQuery: String = "",
    val teamResults: List<TeamSummary> = emptyList(),
    val organiserQuery: String = "",
    val organiserResults: List<PlayerSummary> = emptyList(),
    /** The tournament's player auction, once loaded; null when it has none. */
    val auction: AuctionDetails? = null,
    val auctionSaving: Boolean = false,
) {
    val isOrganiser: Boolean get() = details?.tournament?.isOrganiser(myId) == true

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
                onSuccess = { details -> _state.update { it.copy(loading = false, details = details, loadFailed = false) } },
                onFailure = { _state.update { it.copy(loading = false, loadFailed = it.details == null) } },
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

    fun enterTeam(teamId: String) = act { repository.enterTeam(tournamentId, teamId) }

    fun review(teamId: String, confirm: Boolean) = act { repository.review(tournamentId, teamId, confirm) }

    fun start() = act { repository.start(tournamentId) }

    fun addFixture(teamAId: String, teamBId: String) = act { repository.addFixture(tournamentId, teamAId, teamBId) }

    fun addOrganiser(profileId: String) = act { repository.addOrganiser(tournamentId, profileId) }

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

    fun searchOrganiser(query: String) {
        _state.update { it.copy(organiserQuery = query) }
        searchJob?.cancel()
        if (query.trim().length < 2) {
            _state.update { it.copy(organiserResults = emptyList()) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            val results = attempt { teams.search(query) }.getOrDefault(emptyList())
            _state.update { it.copy(organiserResults = results) }
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
