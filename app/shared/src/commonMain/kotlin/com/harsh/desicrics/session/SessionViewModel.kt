package com.harsh.desicrics.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harsh.desicrics.data.AuthRepository
import com.harsh.desicrics.data.AuthUser
import com.harsh.desicrics.data.UserRepository
import com.harsh.desicrics.data.api.DesiCricsApi
import com.harsh.desicrics.model.UserProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface SessionState {
    data object Loading : SessionState
    data object SignedOut : SessionState
    /** Signed in with Firebase but hasn't created a cricket profile yet. */
    data class NeedsProfile(val user: AuthUser) : SessionState
    data class SignedIn(val profile: UserProfile) : SessionState
    /** Signed in, but the profile couldn't be loaded (usually no connection). */
    data object Error : SessionState
}

class SessionViewModel(
    private val authRepository: AuthRepository,
    userRepository: UserRepository,
    api: DesiCricsApi,
) : ViewModel() {

    init {
        // Starts a sleeping server waking while the splash and sign-in play, instead of on the first real request.
        viewModelScope.launch { api.wakeUp() }
    }

    private val retries = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<SessionState> = combine(authRepository.authState, retries) { user, _ -> user }
        .flatMapLatest { user ->
            if (user == null) {
                flowOf(SessionState.SignedOut)
            } else {
                userRepository.observeProfile(user.uid)
                    .map { profile -> if (profile == null) SessionState.NeedsProfile(user) else SessionState.SignedIn(profile) }
                    .onStart { emit(SessionState.Loading) }
                    .catch { e ->
                        println("DesiCrics: couldn't load the profile: $e")
                        emit(SessionState.Error)
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionState.Loading)

    fun retry() = retries.update { it + 1 }

    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }
}
