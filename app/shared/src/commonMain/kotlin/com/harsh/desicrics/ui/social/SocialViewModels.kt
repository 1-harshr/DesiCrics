package com.harsh.desicrics.ui.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harsh.desicrics.data.SocialRepository
import com.harsh.desicrics.model.FollowType
import com.harsh.desicrics.model.Notification
import com.harsh.desicrics.model.PlayerPage
import com.harsh.desicrics.model.SearchResults
import com.harsh.desicrics.ui.teams.attempt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(val query: String = "", val results: SearchResults = SearchResults(), val searching: Boolean = false)

class SearchViewModel(private val repository: SocialRepository) : ViewModel() {
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state
    private var job: Job? = null

    /** Searches once typing pauses, so each keystroke doesn't hit the server. */
    fun search(query: String) {
        _state.update { it.copy(query = query) }
        job?.cancel()
        if (query.trim().length < 2) {
            _state.update { it.copy(results = SearchResults(), searching = false) }
            return
        }
        job = viewModelScope.launch {
            _state.update { it.copy(searching = true) }
            delay(300)
            val results = attempt { repository.search(query) }.getOrDefault(SearchResults())
            _state.update { it.copy(results = results, searching = false) }
        }
    }
}

data class NotificationsUiState(val loading: Boolean = true, val notifications: List<Notification> = emptyList(), val loadFailed: Boolean = false)

/** The inbox. Opening it marks everything read. */
class NotificationsViewModel(private val repository: SocialRepository) : ViewModel() {
    private val _state = MutableStateFlow(NotificationsUiState())
    val state: StateFlow<NotificationsUiState> = _state

    init {
        viewModelScope.launch {
            attempt { repository.notifications() }.fold(
                onSuccess = { list -> _state.update { NotificationsUiState(loading = false, notifications = list) } },
                onFailure = { _state.update { it.copy(loading = false, loadFailed = true) } },
            )
            attempt { repository.markNotificationsRead() }
        }
    }
}

data class PlayerUiState(val loading: Boolean = true, val page: PlayerPage? = null, val loadFailed: Boolean = false, val isMe: Boolean = false)

class PlayerViewModel(
    private val profileId: String,
    private val repository: SocialRepository,
    myId: String?,
) : ViewModel() {
    private val _state = MutableStateFlow(PlayerUiState(isMe = profileId == myId))
    val state: StateFlow<PlayerUiState> = _state

    init {
        viewModelScope.launch {
            attempt { repository.playerPage(profileId) }.fold(
                onSuccess = { page -> _state.update { it.copy(loading = false, page = page) } },
                onFailure = { _state.update { it.copy(loading = false, loadFailed = true) } },
            )
        }
    }

    /** Flips straight away and puts it back if the server says no. */
    fun toggleFollow() {
        val page = _state.value.page ?: return
        val following = !page.following
        val followers = page.followers + if (following) 1 else -1
        _state.update { it.copy(page = page.copy(following = following, followers = followers)) }
        viewModelScope.launch {
            if (attempt { repository.setFollowing(FollowType.PLAYER, profileId, following) }.isFailure) {
                _state.update { it.copy(page = page) }
            }
        }
    }
}
