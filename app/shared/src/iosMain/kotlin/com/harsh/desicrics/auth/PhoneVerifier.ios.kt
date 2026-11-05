package com.harsh.desicrics.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.PhoneAuthProvider
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.auth.ios
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Composable
actual fun rememberPhoneVerifier(): PhoneVerifier = remember { IosPhoneVerifier() }

/**
 * Firebase verifies the app with a silent APNs push, falling back to a reCAPTCHA web view, which needs
 * the app's encoded-app-ID URL scheme registered in Xcode.
 */
private class IosPhoneVerifier : PhoneVerifier {

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun sendCode(phoneNumber: String): PhoneCodeResult = suspendCancellableCoroutine { continuation ->
        PhoneAuthProvider(Firebase.auth).ios.verifyPhoneNumber(phoneNumber, null) { verificationId, error ->
            when {
                verificationId != null -> continuation.resume(PhoneCodeResult.CodeSent(verificationId))
                else -> continuation.resumeWithException(
                    IllegalStateException(error?.localizedDescription ?: "Couldn't send the verification code"),
                )
            }
        }
    }
}
