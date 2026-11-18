package com.harsh.desicrics.ui.community

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harsh.desicrics.api.DirectoryRequest
import com.harsh.desicrics.api.LookingForRequest
import com.harsh.desicrics.data.CommunityRepository
import com.harsh.desicrics.data.TeamRepository
import com.harsh.desicrics.model.DirectoryEntry
import com.harsh.desicrics.model.DirectoryKind
import com.harsh.desicrics.model.LookingForKind
import com.harsh.desicrics.model.LookingForPost
import com.harsh.desicrics.model.PlayerRanking
import com.harsh.desicrics.model.TeamRanking
import com.harsh.desicrics.model.TeamRole
import com.harsh.desicrics.model.TeamSummary
import com.harsh.desicrics.ui.teams.attempt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CommunityTab { LOOKING_FOR, RANKINGS, DIRECTORY }

data class CommunityUiState(
    val city: String = "",
    val tab: CommunityTab = CommunityTab.LOOKING_FOR,
    val loading: Boolean = false,
    val failed: Boolean = false,
    val actionFailed: Boolean = false,
    val saving: Boolean = false,
    val posts: List<LookingForPost> = emptyList(),
    val myPosts: List<LookingForPost> = emptyList(),
    /** Posts this user has answered in this session, to show "sent". */
    val answered: Set<String> = emptySet(),
    val rankPlayers: Boolean = false,
    val teamRankings: List<TeamRanking> = emptyList(),
    val playerRankings: List<PlayerRanking> = emptyList(),
    val directoryKind: DirectoryKind = DirectoryKind.GROUND,
    val directory: List<DirectoryEntry> = emptyList(),
    /** Teams the user runs, to post on their behalf. */
    val adminTeams: List<TeamSummary> = emptyList(),
)

/** Everything on the community screen, for the user's city unless they type another. */
class CommunityViewModel(
    city: String,
    private val repository: CommunityRepository,
    private val teams: TeamRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(CommunityUiState(city = city))
    val state: StateFlow<CommunityUiState> = _state
    private var job: Job? = null

    init {
        load()
        viewModelScope.launch {
            attempt { teams.myTeams() }.getOrNull()?.let { mine -> _state.update { it.copy(adminTeams = mine.filter { t -> t.myRole == TeamRole.ADMIN }) } }
        }
    }

    fun setCity(city: String) {
        _state.update { it.copy(city = city) }
        job?.cancel()
        job = viewModelScope.launch {
            delay(400)
            loadNow()
        }
    }

    fun setTab(tab: CommunityTab) = update { it.copy(tab = tab) }
    fun setRankPlayers(players: Boolean) = update { it.copy(rankPlayers = players) }
    fun setDirectoryKind(kind: DirectoryKind) = update { it.copy(directoryKind = kind) }

    fun post(kind: LookingForKind, date: Long, note: String, teamId: String?, onDone: () -> Unit) = act(onDone) {
        repository.post(LookingForRequest(kind, _state.value.city.trim(), date, note.trim(), teamId))
    }

    fun respond(postId: String, message: String, phone: String, onDone: () -> Unit) = act(onDone) {
        repository.respond(postId, message, phone)
        _state.update { it.copy(answered = it.answered + postId) }
    }

    fun close(postId: String) = act({}) { repository.close(postId) }

    fun addEntry(name: String, phone: String, notes: String, onDone: () -> Unit) = act(onDone) {
        repository.addEntry(DirectoryRequest(_state.value.directoryKind, name.trim(), _state.value.city.trim(), phone.trim(), notes.trim()))
    }

    fun removeEntry(id: String) = act({}) { repository.removeEntry(id) }

    fun clearError() = _state.update { it.copy(actionFailed = false) }

    private fun update(transform: (CommunityUiState) -> CommunityUiState) {
        _state.update(transform)
        load()
    }

    private fun load() {
        job?.cancel()
        job = viewModelScope.launch { loadNow() }
    }

    private suspend fun loadNow() {
        val ui = _state.value
        if (ui.city.isBlank()) return
        _state.update { it.copy(loading = true, failed = false) }
        val ok = attempt {
            when (ui.tab) {
                CommunityTab.LOOKING_FOR -> {
                    val posts = repository.lookingFor(ui.city, null)
                    val mine = repository.mine()
                    _state.update { it.copy(posts = posts, myPosts = mine) }
                }
                CommunityTab.RANKINGS ->
                    if (ui.rankPlayers) repository.playerRankings(ui.city).let { r -> _state.update { it.copy(playerRankings = r) } }
                    else repository.teamRankings(ui.city).let { r -> _state.update { it.copy(teamRankings = r) } }
                CommunityTab.DIRECTORY -> repository.directory(ui.city, ui.directoryKind).let { d -> _state.update { it.copy(directory = d) } }
            }
        }.isSuccess
        _state.update { it.copy(loading = false, failed = !ok) }
    }

    private fun act(onDone: () -> Unit, call: suspend () -> Any) {
        if (_state.value.saving) return
        _state.update { it.copy(saving = true, actionFailed = false) }
        viewModelScope.launch {
            val ok = attempt { call() }.isSuccess
            _state.update { it.copy(saving = false, actionFailed = !ok) }
            if (ok) {
                onDone()
                loadNow()
            }
        }
    }
}
