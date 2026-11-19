package com.harsh.desicrics.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harsh.desicrics.data.CommunityRepository
import com.harsh.desicrics.data.SocialRepository
import com.harsh.desicrics.data.TeamRepository
import com.harsh.desicrics.model.FollowType
import com.harsh.desicrics.model.PlayerSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The onboarding steps in order. The first two build the profile, which is saved on leaving [GAME]; the
 * rest need it and can each be skipped. [CLAIM], [FOLLOW] and [NOTIFY] only show when they have something
 * to offer.
 */
enum class OnboardingStep { ABOUT, GAME, PHOTO, CLAIM, TEAM, FOLLOW, NOTIFY }

/** Something to follow in the player's city, and whether they've ticked it. */
data class Suggestion(val type: FollowType, val id: String, val name: String, val subtitle: String, val photoUrl: String?, val following: Boolean = false)

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.ABOUT,
    /** Placeholders a team made with this player's verified number; null until checked. */
    val claimable: List<PlayerSummary>? = null,
    val claimed: PlayerSummary? = null,
    val claimFailed: Boolean = false,
    /** The name of the team joined or created here, shown as done on the team step. */
    val teamName: String? = null,
    /** The team code was wrong. */
    val teamFailed: Boolean = false,
    /** Creating a team failed, usually for want of a connection. */
    val createFailed: Boolean = false,
    /** Teams and players in the player's city; null until loaded for [suggestionsCity]. */
    val suggestions: List<Suggestion>? = null,
    val suggestionsCity: String? = null,
    /** Notification permission still needs asking; set by the screen, which knows the platform. */
    val needsNotifications: Boolean = true,
    val working: Boolean = false,
    val finished: Boolean = false,
) {
    /** The steps this player will see, so the header can count them. */
    val steps: List<OnboardingStep>
        get() = OnboardingStep.entries.filter { step ->
            when (step) {
                // Unknown counts as shown, so the count only ever drops.
                OnboardingStep.CLAIM -> claimable == null || claimable.isNotEmpty() || claimed != null
                OnboardingStep.FOLLOW -> suggestions == null || suggestions.isNotEmpty()
                OnboardingStep.NOTIFY -> needsNotifications
                else -> true
            }
        }
}

/**
 * Everything onboarding does after the profile form: claiming past matches, joining a team, following
 * people nearby. The profile itself is saved through [com.harsh.desicrics.ui.profile.EditProfileViewModel].
 */
class OnboardingViewModel(
    private val teams: TeamRepository,
    private val social: SocialRepository,
    private val community: CommunityRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state

    init {
        // Only needs the verified number on the sign-in, not a profile, so it can load straight away.
        viewModelScope.launch {
            val found = runCatching { teams.claimable() }.getOrDefault(emptyList())
            _state.update { it.copy(claimable = found) }
        }
    }

    fun setNeedsNotifications(needed: Boolean) = _state.update { it.copy(needsNotifications = needed) }

    /** Called once the profile is saved, with the city it was saved with. */
    fun loadSuggestions(city: String) {
        val trimmed = city.trim()
        if (trimmed == _state.value.suggestionsCity) return
        _state.update { it.copy(suggestionsCity = trimmed, suggestions = if (trimmed.isEmpty()) emptyList() else null) }
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val teamsNearby = async { runCatching { community.teamRankings(trimmed) }.getOrDefault(emptyList()) }
            val playersNearby = async { runCatching { community.playerRankings(trimmed) }.getOrDefault(emptyList()) }
            val suggestions = teamsNearby.await().take(SUGGESTIONS).map {
                Suggestion(FollowType.TEAM, it.team.id, it.team.name, it.team.city, it.team.logoUrl)
            } + playersNearby.await().take(SUGGESTIONS).map {
                Suggestion(FollowType.PLAYER, it.player.profileId, it.player.name, it.player.city, it.player.photoUrl)
            }
            _state.update { if (it.suggestionsCity == trimmed) it.copy(suggestions = suggestions) else it }
        }
    }

    fun goTo(step: OnboardingStep) = _state.update { it.copy(step = step) }

    /** Moves to the next step this player sees, or finishes after the last. */
    fun next() = _state.update { state ->
        val steps = state.steps
        val after = steps.getOrNull(steps.indexOf(state.step) + 1)
        if (after == null) state.copy(finished = true) else state.copy(step = after)
    }

    fun back() = _state.update { state ->
        val steps = state.steps
        steps.getOrNull(steps.indexOf(state.step) - 1)?.let { state.copy(step = it) } ?: state
    }

    fun claim(profileId: String) = claimWith { teams.claim(profileId) }

    fun claimByCode(code: String) = claimWith { teams.claimByCode(code) }

    private fun claimWith(call: suspend () -> PlayerSummary) = work(onFailure = { it.copy(claimFailed = true) }) {
        val player = call()
        _state.update { it.copy(claimed = player, claimFailed = false) }
    }

    fun join(code: String, onJoined: () -> Unit = {}) = work(onFailure = { it.copy(teamFailed = true) }) {
        val team = teams.join(code)
        _state.update { it.copy(teamName = team.name) }
        onJoined()
    }

    fun createTeam(name: String, city: String) = work(onFailure = { it.copy(createFailed = true) }) {
        val team = teams.create(name.trim(), city.trim())
        _state.update { it.copy(teamName = team.name) }
    }

    /** Ticks straight away and saves in the background; a failed save unticks it again. */
    fun toggleFollow(suggestion: Suggestion) {
        val following = !suggestion.following
        setFollowing(suggestion, following)
        viewModelScope.launch {
            try {
                social.setFollowing(suggestion.type, suggestion.id, following)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setFollowing(suggestion, !following)
            }
        }
    }

    private fun setFollowing(suggestion: Suggestion, following: Boolean) = _state.update { state ->
        state.copy(suggestions = state.suggestions?.map { if (it.type == suggestion.type && it.id == suggestion.id) it.copy(following = following) else it })
    }

    private fun work(onFailure: (OnboardingUiState) -> OnboardingUiState, block: suspend () -> Unit) {
        if (_state.value.working) return
        _state.update { it.copy(working = true, claimFailed = false, teamFailed = false, createFailed = false) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update(onFailure)
            } finally {
                _state.update { it.copy(working = false) }
            }
        }
    }

    private companion object {
        const val SUGGESTIONS = 4
    }
}
