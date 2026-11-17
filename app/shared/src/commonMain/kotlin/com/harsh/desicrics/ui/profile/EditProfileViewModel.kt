package com.harsh.desicrics.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harsh.desicrics.api.UpdateProfileRequest
import com.harsh.desicrics.data.AuthRepository
import com.harsh.desicrics.data.UserRepository
import com.harsh.desicrics.model.BattingStyle
import com.harsh.desicrics.model.BowlingStyle
import com.harsh.desicrics.model.PlayerRole
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileFormState(
    val loading: Boolean = true,
    val name: String = "",
    val phone: String = "",
    val city: String = "",
    val role: PlayerRole = PlayerRole.BATTER,
    val battingStyle: BattingStyle = BattingStyle.RIGHT_HAND,
    val bowlingStyle: BowlingStyle = BowlingStyle.NONE,
    val saving: Boolean = false,
    val error: String? = null,
)

/** Backs both first-time onboarding and later profile edits. */
class EditProfileViewModel(
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileFormState())
    val state: StateFlow<ProfileFormState> = _state

    init {
        viewModelScope.launch {
            val user = authRepository.currentUser ?: return@launch
            val existing = runCatching { userRepository.getProfile() }.getOrNull()
            _state.value = existing?.let {
                ProfileFormState(
                    loading = false,
                    name = it.name,
                    phone = it.phone,
                    city = it.city,
                    role = it.role,
                    battingStyle = it.battingStyle,
                    bowlingStyle = it.bowlingStyle,
                )
            } ?: ProfileFormState(
                loading = false,
                name = user.displayName ?: authRepository.displayNameHint.orEmpty(),
                phone = user.phone.orEmpty(),
            )
        }
    }

    fun update(transform: (ProfileFormState) -> ProfileFormState) = _state.update { transform(it).copy(error = null) }

    fun save(onSaved: () -> Unit) {
        val form = _state.value
        val request = UpdateProfileRequest(
            name = form.name.trim(),
            phone = form.phone.trim(),
            city = form.city.trim(),
            role = form.role,
            battingStyle = form.battingStyle,
            bowlingStyle = form.bowlingStyle,
            photoUrl = authRepository.currentUser?.photoUrl,
        )
        // Same check the server runs, for instant feedback.
        request.validationError()?.let { error ->
            _state.update { it.copy(error = error) }
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                userRepository.saveProfile(request)
                onSaved()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Couldn't save your profile") }
            } finally {
                _state.update { it.copy(saving = false) }
            }
        }
    }
}
