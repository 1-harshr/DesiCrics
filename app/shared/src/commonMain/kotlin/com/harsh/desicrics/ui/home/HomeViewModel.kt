package com.harsh.desicrics.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harsh.desicrics.data.AuthRepository
import com.harsh.desicrics.data.MatchRepository
import com.harsh.desicrics.model.Match
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val loading: Boolean = true,
    val matches: List<Match> = emptyList(),
    /** The latest refresh failed; the last list we got stays on screen. */
    val loadFailed: Boolean = false,
)

/** Taking over scoring from another phone with its handover code. */
data class TakeOverState(val saving: Boolean = false, val failed: Boolean = false)

class HomeViewModel(
    private val matchRepository: MatchRepository,
    authRepository: AuthRepository,
) : ViewModel() {

    private val _takeOver = MutableStateFlow(TakeOverState())
    val takeOver: StateFlow<TakeOverState> = _takeOver

    /** Calls [onTaken] with the match to open once this user is its scorer. */
    fun takeOver(code: String, onTaken: (matchId: String) -> Unit) {
        if (_takeOver.value.saving) return
        _takeOver.value = TakeOverState(saving = true)
        viewModelScope.launch {
            val matchId = try {
                matchRepository.takeHandover(code.filter { it.isDigit() })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            _takeOver.value = TakeOverState(failed = matchId == null)
            matchId?.let(onTaken)
        }
    }

    fun resetTakeOver() {
        _takeOver.value = TakeOverState()
    }

    val currentUid: String? = authRepository.currentUser?.uid

    val state: StateFlow<HomeUiState> = matchRepository.observeRecentMatches()
        // Keep showing the last list we got if a later refresh fails.
        .scan(HomeUiState()) { previous, result ->
            result.fold(
                onSuccess = { HomeUiState(loading = false, matches = it) },
                onFailure = { previous.copy(loading = false, loadFailed = true) },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}
