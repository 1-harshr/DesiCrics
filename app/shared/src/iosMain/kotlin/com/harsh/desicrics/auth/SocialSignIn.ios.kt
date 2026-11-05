package com.harsh.desicrics.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.gitlive.firebase.auth.GoogleAuthProvider
import dev.gitlive.firebase.auth.OAuthProvider
import kotlinx.coroutines.suspendCancellableCoroutine
import org.koin.compose.koinInject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Implemented in Swift (`SocialSignInBridge.swift`) because the Google and Apple sign-in SDKs are
 * Swift-first. Each callback gets either tokens, an error message, or neither when the user cancelled.
 */
interface NativeSignInBridge {
    fun signInWithGoogle(completion: (GoogleTokens?, String?) -> Unit)
    fun signInWithApple(completion: (AppleTokens?, String?) -> Unit)
}

class GoogleTokens(val idToken: String, val accessToken: String, val displayName: String?)

class AppleTokens(val idToken: String, val rawNonce: String, val displayName: String?)

@Composable
actual fun rememberSocialSignIn(): SocialSignIn {
    val bridge = koinInject<NativeSignInBridge>()
    return remember(bridge) { IosSocialSignIn(bridge) }
}

private class IosSocialSignIn(private val bridge: NativeSignInBridge) : SocialSignIn {

    override val providers = listOf(SocialProvider.APPLE, SocialProvider.GOOGLE)

    override suspend fun signIn(provider: SocialProvider): SocialSignInResult? = when (provider) {
        SocialProvider.GOOGLE -> awaitTokens(bridge::signInWithGoogle)?.let {
            SocialSignInResult(GoogleAuthProvider.credential(it.idToken, it.accessToken), it.displayName)
        }
        SocialProvider.APPLE -> awaitTokens(bridge::signInWithApple)?.let {
            SocialSignInResult(
                OAuthProvider.credential(providerId = "apple.com", idToken = it.idToken, rawNonce = it.rawNonce),
                it.displayName,
            )
        }
    }

    private suspend fun <T> awaitTokens(start: ((T?, String?) -> Unit) -> Unit): T? =
        suspendCancellableCoroutine { continuation ->
            start { tokens, error ->
                when {
                    error != null -> continuation.resumeWithException(IllegalStateException(error))
                    else -> continuation.resume(tokens)
                }
            }
        }
}
