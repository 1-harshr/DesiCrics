package com.harsh.desicrics.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harsh.desicrics.api.SupportCategory
import com.harsh.desicrics.data.AuthRepository
import com.harsh.desicrics.data.TeamRepository
import com.harsh.desicrics.model.Match
import com.harsh.desicrics.model.PlayerSummary
import com.harsh.desicrics.model.TeamSummary
import com.harsh.desicrics.ui.teams.attempt
import com.harsh.desicrics.util.AppInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Outcome of the last settings action, for the sheet that started it. */
enum class SettingsResult { CLAIMED, CLAIM_FAILED, SUPPORT_SENT, SUPPORT_FAILED, DELETE_FAILED }

data class ProfileUiState(
    val matches: List<Match>? = null,
    val teams: List<TeamSummary>? = null,
    val loadFailed: Boolean = false,
    /** Placeholders a team made with this user's verified number, waiting to be claimed. */
    val claimable: List<PlayerSummary> = emptyList(),
    val saving: Boolean = false,
    val result: SettingsResult? = null,
)

class ProfileViewModel(
    private val teams: TeamRepository,
    private val auth: AuthRepository,
    private val app: AppInfo,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state

    /** The screen calls this each time it comes into view, the first time included. */
    fun refresh() {
        viewModelScope.launch {
            val matches = attempt { teams.myMatches() }
            val myTeams = attempt { teams.myTeams() }
            val claimable = attempt { teams.claimable() }.getOrDefault(emptyList())
            _state.update {
                it.copy(
                    matches = matches.getOrNull() ?: it.matches,
                    teams = myTeams.getOrNull() ?: it.teams,
                    loadFailed = matches.isFailure || myTeams.isFailure,
                    claimable = claimable,
                )
            }
        }
    }

    fun claimByCode(code: String) = claim { teams.claimByCode(code) }

    fun claim(profileId: String) = claim { teams.claim(profileId) }

    fun contactSupport(category: SupportCategory, message: String) = run(SettingsResult.SUPPORT_SENT, SettingsResult.SUPPORT_FAILED) {
        teams.contactSupport(category, message, app.version)
    }

    /** Clears the account on the server, then deletes the Firebase user, then signs out. */
    fun deleteAccount(onDeleted: () -> Unit) {
        if (_state.value.saving) return
        _state.update { it.copy(saving = true, result = null) }
        viewModelScope.launch {
            val deleted = attempt { teams.deleteAccount() }.isSuccess
            if (!deleted) {
                _state.update { it.copy(saving = false, result = SettingsResult.DELETE_FAILED) }
                return@launch
            }
            if (!auth.deleteFirebaseUser()) auth.signOut()
            onDeleted()
        }
    }

    fun clearResult() = _state.update { it.copy(result = null) }

    private fun claim(call: suspend () -> PlayerSummary) = run(SettingsResult.CLAIMED, SettingsResult.CLAIM_FAILED) {
        call()
        refresh()
    }

    private fun run(success: SettingsResult, failure: SettingsResult, call: suspend () -> Unit) {
        if (_state.value.saving) return
        _state.update { it.copy(saving = true, result = null) }
        viewModelScope.launch {
            val ok = attempt { call() }.isSuccess
            _state.update { it.copy(saving = false, result = if (ok) success else failure) }
        }
    }
}
