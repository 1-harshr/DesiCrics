package com.harsh.desicrics.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harsh.desicrics.data.AuthRepository
import com.harsh.desicrics.data.MatchRepository
import com.harsh.desicrics.model.Match
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val loading: Boolean = true,
    val matches: List<Match> = emptyList(),
    val error: String? = null,
)

class HomeViewModel(
    matchRepository: MatchRepository,
    authRepository: AuthRepository,
) : ViewModel() {

    val currentUid: String? = authRepository.currentUser?.uid

    val state: StateFlow<HomeUiState> = matchRepository.observeRecentMatches()
        // Keep showing the last list we got if a later refresh fails.
        .scan(HomeUiState()) { previous, result ->
            result.fold(
                onSuccess = { HomeUiState(loading = false, matches = it) },
                onFailure = { previous.copy(loading = false, error = it.message ?: "Couldn't load matches") },
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())
}
