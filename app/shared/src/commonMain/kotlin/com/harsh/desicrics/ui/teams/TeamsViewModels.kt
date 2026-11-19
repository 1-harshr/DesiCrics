package com.harsh.desicrics.ui.teams

import com.harsh.desicrics.model.TeamInsights
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harsh.desicrics.data.AuthRepository
import com.harsh.desicrics.data.SocialRepository
import com.harsh.desicrics.data.TeamRepository
import com.harsh.desicrics.data.api.ApiException
import com.harsh.desicrics.model.FollowType
import com.harsh.desicrics.model.HeadToHead
import com.harsh.desicrics.model.PlayerSummary
import com.harsh.desicrics.model.SavedTeam
import com.harsh.desicrics.model.TeamRole
import com.harsh.desicrics.model.TeamSummary
import com.harsh.desicrics.ui.components.PhotoAvatar
import com.harsh.desicrics.ui.components.publicCodeIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Runs [block], returning failures instead of throwing them; cancellation still propagates. */
internal suspend fun <T> attempt(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}

/**
 * Why the server turned a request down ("Make someone else an admin first"), worth showing as it is; null
 * when the request never got an answer or the server itself failed, where the connection message fits.
 */
internal fun Throwable.refusal(): String? = (this as? ApiException)?.takeIf { it.status.value in 400..499 }?.message

data class TeamsUiState(
    val loading: Boolean = true,
    val teams: List<TeamSummary> = emptyList(),
    val loadFailed: Boolean = false,
    /** A create or join is in flight. */
    val saving: Boolean = false,
    val joinFailed: Boolean = false,
    val actionFailed: Boolean = false,
    /** Filters both lists, by name or code. */
    val query: String = "",
    /** Every team (or those matching [query]) as the server pages them, the caller's own included. */
    val browsed: List<TeamSummary> = emptyList(),
    val browsing: Boolean = true,
    /** The server has no more pages. */
    val browsedAll: Boolean = false,
    val browseFailed: Boolean = false,
) {
    /** The caller's teams that match the search. */
    val mine: List<TeamSummary>
        get() = query.trim().takeIf { it.isNotEmpty() }?.let { q -> teams.filter { it.name.contains(q, ignoreCase = true) || it.code == q } } ?: teams

    /** Everyone else's, which the caller can open to follow or ask to join. */
    val others: List<TeamSummary>
        get() {
            val ids = teams.map { it.id }.toSet()
            return browsed.filter { it.id !in ids }
        }
}

class TeamsViewModel(private val repository: TeamRepository) : ViewModel() {

    private val _state = MutableStateFlow(TeamsUiState())
    val state: StateFlow<TeamsUiState> = _state
    private var browseJob: Job? = null

    /** The screen calls this each time it comes into view, the first time included. */
    fun refresh() {
        viewModelScope.launch {
            attempt { repository.myTeams() }.fold(
                onSuccess = { teams -> _state.update { it.copy(loading = false, teams = teams, loadFailed = false) } },
                onFailure = { _state.update { it.copy(loading = false, loadFailed = true) } },
            )
        }
        browse(reset = true)
    }

    /** Searches as the user types, waiting briefly so each keystroke doesn't hit the server. */
    fun search(query: String) {
        _state.update { it.copy(query = query) }
        browse(reset = true, wait = true)
    }

    /** The next page, when the list scrolls to its end. */
    fun loadMore() {
        val s = _state.value
        if (s.browsing || s.browsedAll || s.browseFailed) return
        browse(reset = false)
    }

    fun retryBrowse() = browse(reset = _state.value.browsed.isEmpty())

    private fun browse(reset: Boolean, wait: Boolean = false) {
        if (reset) browseJob?.cancel()
        _state.update { it.copy(browsing = true, browseFailed = false) }
        browseJob = viewModelScope.launch {
            if (wait) delay(300)
            val offset = if (reset) 0 else _state.value.browsed.size
            attempt { repository.browse(_state.value.query, offset) }.fold(
                onSuccess = { page ->
                    _state.update {
                        it.copy(browsed = if (reset) page else it.browsed + page, browsing = false, browsedAll = page.isEmpty())
                    }
                },
                onFailure = { _state.update { it.copy(browsing = false, browseFailed = true) } },
            )
        }
    }

    fun create(name: String, city: String, onCreated: (teamId: String) -> Unit) = save(onCreated, joining = false) {
        repository.create(name, city)
    }

    /**
     * An invite code joins straight away. A team's public code is on its page for anyone to see, so it only
     * finds the team: [onJoined] opens it, where the caller can ask to join.
     */
    fun join(code: String, onJoined: (teamId: String) -> Unit) {
        val publicCode = publicCodeIn(code)
        if (publicCode == null) {
            save(onJoined, joining = true) { repository.join(code) }
            return
        }
        if (_state.value.saving) return
        _state.update { it.copy(saving = true, joinFailed = false, actionFailed = false) }
        viewModelScope.launch {
            val teamId = attempt { repository.teamIdByCode(publicCode) }.getOrNull()
            _state.update { it.copy(saving = false, joinFailed = teamId == null) }
            teamId?.let(onJoined)
        }
    }

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
    /** The server's reason the last action failed, if it gave one; see [refusal]. */
    val actionError: String? = null,
    val searchQuery: String = "",
    val searchResults: List<PlayerSummary> = emptyList(),
    val searching: Boolean = false,
    /** Players added from search whose add is still on its way, so their row shows added straight away. */
    val adding: Set<String> = emptySet(),
    /** The caller's other teams, to compare records against. */
    val otherTeams: List<TeamSummary> = emptyList(),
    val headToHead: HeadToHead? = null,
    /** The last record picked didn't load; picking it again retries. */
    val headToHeadFailed: Boolean = false,
    /** The organiser view, loaded for admins only. */
    val insights: TeamInsights? = null,
    /** Null until known. */
    val following: Boolean? = null,
) {
    val myRole: TeamRole? get() = team?.roleOf(myId)
    val isAdmin: Boolean get() = myRole == TeamRole.ADMIN

    /** The only admin, with others in the team: the server won't let them leave until someone else is one. */
    val mustHandOver: Boolean
        get() = isAdmin && team != null && team.members.size > 1 && team.members.count { it.role == TeamRole.ADMIN } == 1
}

class TeamViewModel(
    private val teamId: String,
    private val repository: TeamRepository,
    private val social: SocialRepository,
    authRepository: AuthRepository,
) : ViewModel() {

    val myId: String? = authRepository.currentUser?.uid

    private val _state = MutableStateFlow(TeamUiState(myId = myId))
    val state: StateFlow<TeamUiState> = _state

    private var searchJob: Job? = null

    /** Adds run one after another, so an admin can tap through a whole squad without waiting on each. */
    private val adds = Mutex()

    init {
        load()
    }

    /** Loads the team again after it failed to load. */
    fun retry() = load()

    private fun load() {
        _state.update { it.copy(loading = true, loadFailed = false) }
        viewModelScope.launch {
            attempt { repository.team(teamId) }.fold(
                onSuccess = { team -> show(team) },
                onFailure = { _state.update { it.copy(loading = false, loadFailed = true) } },
            )
            if (_state.value.isAdmin) {
                attempt { repository.insights(teamId) }.getOrNull()?.let { insights -> _state.update { it.copy(insights = insights) } }
            }
            attempt { repository.myTeams() }.getOrNull()?.let { teams ->
                _state.update { it.copy(otherTeams = teams.filter { team -> team.id != teamId }) }
            }
            // If the follows don't load, show Follow anyway rather than no button; following twice is harmless.
            val follows = attempt { social.follows() }.getOrDefault(emptyList())
            _state.update { it.copy(following = follows.any { f -> f.type == FollowType.TEAM && f.targetId == teamId }) }
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

    fun addPlayer(profileId: String) {
        _state.update { it.copy(adding = it.adding + profileId) }
        queueAdd { repository.addPlayer(teamId, profileId) }.invokeOnCompletion { _state.update { it.copy(adding = it.adding - profileId) } }
    }

    fun addPlaceholder(name: String, phone: String) = queueAdd { repository.addPlaceholder(teamId, name, phone) }

    /** Like [act], but queued rather than dropped while another add runs, and the search stays as it was. */
    private fun queueAdd(call: suspend () -> SavedTeam) = viewModelScope.launch {
        adds.withLock {
            attempt { call() }.fold(
                onSuccess = { team -> _state.update { it.copy(team = team, actionFailed = false, actionError = null) } },
                onFailure = { e -> _state.update { it.copy(actionFailed = true, actionError = e.refusal()) } },
            )
        }
    }

    fun setRole(profileId: String, role: TeamRole) = act { repository.setRole(teamId, profileId, role) }

    fun remove(profileId: String) = act { repository.remove(teamId, profileId) }

    fun rename(name: String, city: String) = act { repository.rename(teamId, name, city) }

    fun newJoinCode() = act { repository.newJoinCode(teamId) }

    /** For someone outside the team: asks its admins, or takes the request back. */
    fun toggleJoinRequest() = act {
        if (_state.value.team?.requested == true) repository.cancelJoinRequest(teamId) else repository.requestToJoin(teamId)
    }

    fun acceptJoinRequest(profileId: String) = act { repository.acceptJoinRequest(teamId, profileId) }

    fun declineJoinRequest(profileId: String) = act { repository.declineJoinRequest(teamId, profileId) }

    fun uploadLogo(jpeg: ByteArray) = act { repository.uploadLogo(teamId, jpeg) }

    /** Calls [onLeft] once the caller is out of the team. */
    fun leave(onLeft: () -> Unit) {
        val me = myId ?: return
        viewModelScope.launch {
            _state.update { it.copy(saving = true, actionFailed = false, actionError = null) }
            val left = attempt { repository.remove(teamId, me) }
            _state.update { it.copy(saving = false, actionFailed = left.isFailure, actionError = left.exceptionOrNull()?.refusal()) }
            if (left.isSuccess) onLeft()
        }
    }

    fun compareWith(otherId: String) {
        _state.update { it.copy(headToHeadFailed = false) }
        viewModelScope.launch {
            attempt { repository.headToHead(teamId, otherId) }.fold(
                onSuccess = { h2h -> _state.update { it.copy(headToHead = h2h) } },
                onFailure = { _state.update { it.copy(headToHead = null, headToHeadFailed = true) } },
            )
        }
    }

    fun joinLink(code: String): String = repository.joinLink(code)

    /** Flips straight away and puts it back if the server says no. */
    fun toggleFollow() {
        val was = _state.value.following ?: return
        _state.update { it.copy(following = !was) }
        viewModelScope.launch {
            if (attempt { social.setFollowing(FollowType.TEAM, teamId, !was) }.isFailure) _state.update { it.copy(following = was) }
        }
    }

    fun clearError() = _state.update { it.copy(actionFailed = false, actionError = null) }

    private fun act(call: suspend () -> SavedTeam) {
        if (_state.value.saving) return
        _state.update { it.copy(saving = true, actionFailed = false, actionError = null) }
        viewModelScope.launch {
            attempt { call() }.fold(
                onSuccess = { show(it) },
                onFailure = { e -> _state.update { it.copy(saving = false, actionFailed = true, actionError = e.refusal()) } },
            )
        }
    }

    private fun show(team: SavedTeam) = _state.update {
        it.copy(loading = false, team = team, saving = false, loadFailed = false, searchQuery = "", searchResults = emptyList())
    }
}
