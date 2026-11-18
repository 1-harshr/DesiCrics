package com.harsh.desicrics.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harsh.desicrics.api.ProfileError
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

sealed interface ProfileFormError {
    data class Invalid(val error: ProfileError) : ProfileFormError
    data object SaveFailed : ProfileFormError
}

data class ProfileFormState(
    val loading: Boolean = true,
    val name: String = "",
    val phone: String = "",
    /** The number came from phone sign-in, so it's already verified and can't be edited here. */
    val phoneVerified: Boolean = false,
    val city: String = "",
    val role: PlayerRole = PlayerRole.BATTER,
    val battingStyle: BattingStyle = BattingStyle.RIGHT_HAND,
    val bowlingStyle: BowlingStyle = BowlingStyle.NONE,
    /** Kept as it is unless a new one is uploaded, so saving the form never drops an uploaded photo. */
    val photoUrl: String? = null,
    val uploadingPhoto: Boolean = false,
    val photoFailed: Boolean = false,
    val saving: Boolean = false,
    val error: ProfileFormError? = null,
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
                    phoneVerified = user.phone != null && user.phone == it.phone,
                    city = it.city,
                    role = it.role,
                    battingStyle = it.battingStyle,
                    bowlingStyle = it.bowlingStyle,
                    photoUrl = it.photoUrl,
                )
            } ?: ProfileFormState(
                loading = false,
                name = user.displayName ?: authRepository.displayNameHint.orEmpty(),
                phone = user.phone.orEmpty(),
                phoneVerified = user.phone != null,
                // A new profile starts with the sign-in account's photo, e.g. from Google.
                photoUrl = user.photoUrl,
            )
        }
    }

    fun update(transform: (ProfileFormState) -> ProfileFormState) = _state.update { transform(it).copy(error = null) }

    /** Uploads straight away, before the form is saved; needs a profile to attach to, so not during onboarding. */
    fun uploadPhoto(jpeg: ByteArray?) {
        if (jpeg == null) return
        _state.update { it.copy(uploadingPhoto = true, photoFailed = false) }
        viewModelScope.launch {
            try {
                val profile = userRepository.uploadPhoto(jpeg)
                _state.update { it.copy(photoUrl = profile.photoUrl, uploadingPhoto = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(uploadingPhoto = false, photoFailed = true) }
            }
        }
    }

    fun save(onSaved: () -> Unit) {
        val form = _state.value
        val request = UpdateProfileRequest(
            name = form.name.trim(),
            phone = form.phone.trim(),
            city = form.city.trim(),
            role = form.role,
            battingStyle = form.battingStyle,
            bowlingStyle = form.bowlingStyle,
            photoUrl = form.photoUrl,
        )
        // Same check the server runs, for instant feedback.
        request.validate()?.let { error ->
            _state.update { it.copy(error = ProfileFormError.Invalid(error)) }
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
                _state.update { it.copy(error = ProfileFormError.SaveFailed) }
            } finally {
                _state.update { it.copy(saving = false) }
            }
        }
    }
}
