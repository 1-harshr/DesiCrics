package com.harsh.desicrics.ui.signin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.harsh.desicrics.auth.PhoneCodeResult
import com.harsh.desicrics.auth.PhoneVerifier
import com.harsh.desicrics.auth.SocialProvider
import com.harsh.desicrics.auth.SocialSignIn
import com.harsh.desicrics.auth.SignInError
import com.harsh.desicrics.auth.toSignInError
import com.harsh.desicrics.data.AuthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SignInMethod { GOOGLE, APPLE, EMAIL, PHONE }

sealed interface PhoneStep {
    data object EnterNumber : PhoneStep
    data class EnterCode(val phoneNumber: String, val verificationId: String) : PhoneStep
}

/** Non-error feedback shown under the form. */
sealed interface SignInMessage {
    data class ResetEmailSent(val email: String) : SignInMessage
    data class CodeSent(val phoneNumber: String) : SignInMessage
}

data class SignInUiState(
    /** The method with a request in flight; everything is disabled until it finishes. */
    val inProgress: SignInMethod? = null,
    val error: SignInError? = null,
    val message: SignInMessage? = null,
    val phoneStep: PhoneStep = PhoneStep.EnterNumber,
)

class SignInViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _state = MutableStateFlow(SignInUiState())
    val state: StateFlow<SignInUiState> = _state

    /** Clears feedback when the user switches between sign-in options. */
    fun clearFeedback() = _state.update { it.copy(error = null, message = null) }

    fun signIn(provider: SocialProvider, socialSignIn: SocialSignIn) {
        val method = if (provider == SocialProvider.APPLE) SignInMethod.APPLE else SignInMethod.GOOGLE
        launchAuth(method) {
            // Null means the user closed the sign-in sheet.
            val result = socialSignIn.signIn(provider) ?: return@launchAuth
            // SessionViewModel picks up the new user from the auth state flow.
            authRepository.signIn(result.credential, result.displayName)
        }
    }

    fun signInWithEmail(email: String, password: String) {
        val error = validateEmail(email) ?: if (password.isEmpty()) SignInError.PASSWORD_MISSING else null
        if (error != null) return showError(error)
        launchAuth(SignInMethod.EMAIL) { authRepository.signInWithEmail(email.trim(), password) }
    }

    fun createAccountWithEmail(email: String, password: String, confirmPassword: String) {
        val error = validateEmail(email) ?: when {
            password.length < 6 -> SignInError.PASSWORD_SHORT
            password != confirmPassword -> SignInError.PASSWORDS_DIFFER
            else -> null
        }
        if (error != null) return showError(error)
        launchAuth(SignInMethod.EMAIL) { authRepository.createAccountWithEmail(email.trim(), password) }
    }

    fun sendPasswordReset(email: String) {
        validateEmail(email)?.let { return showError(it) }
        launchAuth(SignInMethod.EMAIL) {
            authRepository.sendPasswordReset(email.trim())
            _state.update { it.copy(message = SignInMessage.ResetEmailSent(email.trim())) }
        }
    }

    fun sendPhoneCode(rawNumber: String, verifier: PhoneVerifier) {
        val phoneNumber = normalizePhoneNumber(rawNumber) ?: return showError(SignInError.PHONE_INVALID)
        launchAuth(SignInMethod.PHONE) {
            when (val result = verifier.sendCode(phoneNumber)) {
                is PhoneCodeResult.CodeSent -> _state.update {
                    it.copy(phoneStep = PhoneStep.EnterCode(phoneNumber, result.verificationId), message = SignInMessage.CodeSent(phoneNumber))
                }
                is PhoneCodeResult.AutoVerified -> authRepository.signIn(result.credential)
            }
        }
    }

    fun verifyPhoneCode(code: String) {
        val step = _state.value.phoneStep as? PhoneStep.EnterCode ?: return
        val digits = code.filter { it.isDigit() }
        if (digits.length != 6) return showError(SignInError.CODE_INCOMPLETE)
        // A wrong code leaves us on this step, so the user can retype without another SMS.
        launchAuth(SignInMethod.PHONE) { authRepository.signIn(authRepository.phoneCredential(step.verificationId, digits)) }
    }

    fun changePhoneNumber() = _state.update { it.copy(phoneStep = PhoneStep.EnterNumber, error = null, message = null) }

    private fun launchAuth(method: SignInMethod, block: suspend () -> Unit) {
        if (_state.value.inProgress != null) return
        _state.update { it.copy(inProgress = method, error = null, message = null) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = e.toSignInError()) }
            } finally {
                _state.update { it.copy(inProgress = null) }
            }
        }
    }

    private fun showError(error: SignInError) = _state.update { it.copy(error = error, message = null) }

    private fun validateEmail(email: String): SignInError? = when {
        email.isBlank() -> SignInError.EMAIL_MISSING
        !EMAIL_PATTERN.matches(email.trim()) -> SignInError.EMAIL_INVALID
        else -> null
    }

    /** Accepts "98765 43210", "+91 98765-43210", etc. Bare 10-digit numbers are treated as Indian. */
    private fun normalizePhoneNumber(raw: String): String? {
        val digits = raw.filter { it.isDigit() }
        return when {
            raw.trim().startsWith("+") && digits.length in 8..15 -> "+$digits"
            digits.length == 10 -> "+91$digits"
            else -> null
        }
    }

    private companion object {
        val EMAIL_PATTERN = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}
