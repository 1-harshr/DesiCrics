package com.harsh.desicrics.auth

import androidx.compose.runtime.Composable
import dev.gitlive.firebase.auth.AuthCredential

sealed interface PhoneCodeResult {
    /** An SMS code is on its way; sign in with the code the user types and this ID. */
    data class CodeSent(val verificationId: String) : PhoneCodeResult

    /** Android verified the number without the user typing anything (instant verification / auto-read SMS). */
    class AutoVerified(val credential: AuthCredential) : PhoneCodeResult
}

/**
 * Sends the SMS verification code through the platform SDK. This is split from building the credential
 * (`PhoneAuthProvider.credential(verificationId, code)`) so a mistyped code can be retried without
 * sending another SMS.
 */
interface PhoneVerifier {
    /** [phoneNumber] must be in E.164 format, e.g. +919876543210. Throws if Firebase rejects the request. */
    suspend fun sendCode(phoneNumber: String): PhoneCodeResult
}

@Composable
expect fun rememberPhoneVerifier(): PhoneVerifier
