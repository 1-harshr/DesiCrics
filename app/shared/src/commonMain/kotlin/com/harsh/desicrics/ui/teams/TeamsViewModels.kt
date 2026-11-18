package com.harsh.desicrics.ui.teams

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harsh.desicrics.data.AuthRepository
import com.harsh.desicrics.data.TeamRepository
import com.harsh.desicrics.model.HeadToHead
import com.harsh.desicrics.model.PlayerSummary
import com.harsh.desicrics.model.SavedTeam
import com.harsh.desicrics.model.TeamRole
import com.harsh.desicrics.model.TeamSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Runs [block], returning failures instead of throwing them; cancellation still propagates. */
internal suspend fun <T> attempt(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}

data class TeamsUiState(
    val loading: Boolean = true,
    val teams: List<TeamSummary> = emptyList(),
    val loadFailed: Boolean = false,
    /** A create or join is in flight. */
    val saving: Boolean = false,
    val joinFailed: Boolean = false,
    val actionFailed: Boolean = false,
)

class TeamsViewModel(private val repository: TeamRepository) : ViewModel() {

    private val _state = MutableStateFlow(TeamsUiState())
    val state: StateFlow<TeamsUiState> = _state

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            attempt { repository.myTeams() }.fold(
                onSuccess = { teams -> _state.update { it.copy(loading = false, teams = teams, loadFailed = false) } },
                onFailure = { _state.update { it.copy(loading = false, loadFailed = true) } },
            )
        }
    }

    fun create(name: String, city: String, onCreated: (teamId: String) -> Unit) = save(onCreated, joining = false) {
        repository.create(name, city)
    }

    fun join(code: String, onJoined: (teamId: String) -> Unit) = save(onJoined, joining = true) { repository.join(code) }

    fun clearErrors() = _state.update { it.copy(joinFailed = false, actionFailed = false) }

    private fun save(onDone: (String) -> Unit, joining: Boolean, call: suspend () -> SavedTeam) {
        if (_state.value.saving) return
        _state.update { it.copy(saving = true, joinFailed = false, actionFailed = false) }
        viewModelScope.launch {
            val result = attempt { call() }
            _state.update { it.copy(saving = false, joinFailed = joining && result.isFailure, actionFailed = !joining && result.isFailure) }
            result.getOrNull()?.let {
                refresh()
                onDone(it.id)
            }
        }
    }
}

data class TeamUiState(
    /** The signed-in user's profile ID, to tell their own row and role apart. */
    val myId: String? = null,
    val loading: Boolean = true,
    val team: SavedTeam? = null,
    val loadFailed: Boolean = false,
    val saving: Boolean = false,
    val actionFailed: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<PlayerSummary> = emptyList(),
    val searching: Boolean = false,
    /** The caller's other teams, to compare records against. */
    val otherTeams: List<TeamSummary> = emptyList(),
    val headToHead: HeadToHead? = null,
) {
    val myRole: TeamRole? get() = team?.roleOf(myId)
    val isAdmin: Boolean get() = myRole == TeamRole.ADMIN
}

class TeamViewModel(
    private val teamId: String,
    private val repository: TeamRepository,
    authRepository: AuthRepository,
) : ViewModel() {

    val myId: String? = authRepository.currentUser?.uid

    private val _state = MutableStateFlow(TeamUiState(myId = myId))
    val state: StateFlow<TeamUiState> = _state

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            attempt { repository.team(teamId) }.fold(
                onSuccess = { team -> show(team) },
                onFailure = { _state.update { it.copy(loading = false, loadFailed = true) } },
            )
            attempt { repository.myTeams() }.getOrNull()?.let { teams ->
                _state.update { it.copy(otherTeams = teams.filter { team -> team.id != teamId }) }
            }
        }
    }

    /** Searches as the admin types, waiting briefly so each keystroke doesn't hit the server. */
    fun search(query: String) {
        _state.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        if (query.trim().length < 2) {
            _state.update { it.copy(searchResults = emptyList(), searching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            _state.update { it.copy(searching = true) }
            delay(300)
            val results = attempt { repository.search(query) }.getOrDefault(emptyList())
            _state.update { it.copy(searchResults = results, searching = false) }
        }
    }

    fun addPlayer(profileId: String) = act { repository.addPlayer(teamId, profileId) }

    fun addPlaceholder(name: String, phone: String) = act { repository.addPlaceholder(teamId, name, phone) }

    fun setRole(profileId: String, role: TeamRole) = act { repository.setRole(teamId, profileId, role) }

    fun remove(profileId: String) = act { repository.remove(teamId, profileId) }

    fun rename(name: String, city: String) = act { repository.rename(teamId, name, city) }

    fun newJoinCode() = act { repository.newJoinCode(teamId) }

    /** Calls [onLeft] once the caller is out of the team. */
    fun leave(onLeft: () -> Unit) {
        val me = myId ?: return
        viewModelScope.launch {
            _state.update { it.copy(saving = true, actionFailed = false) }
            val left = attempt { repository.remove(teamId, me) }.isSuccess
            _state.update { it.copy(saving = false, actionFailed = !left) }
            if (left) onLeft()
        }
    }

    fun compareWith(otherId: String) {
        viewModelScope.launch {
            attempt { repository.headToHead(teamId, otherId) }.getOrNull()?.let { h2h -> _state.update { it.copy(headToHead = h2h) } }
        }
    }

    fun joinLink(code: String): String = repository.joinLink(code)

    fun clearError() = _state.update { it.copy(actionFailed = false) }

    private fun act(call: suspend () -> SavedTeam) {
        if (_state.value.saving) return
        _state.update { it.copy(saving = true, actionFailed = false) }
        viewModelScope.launch {
            attempt { call() }.fold(
                onSuccess = { show(it) },
                onFailure = { _state.update { it.copy(saving = false, actionFailed = true) } },
            )
        }
    }

    private fun show(team: SavedTeam) = _state.update {
        it.copy(loading = false, team = team, saving = false, loadFailed = false, searchQuery = "", searchResults = emptyList())
    }
}
