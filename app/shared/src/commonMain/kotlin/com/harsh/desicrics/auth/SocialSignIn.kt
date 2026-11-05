package com.harsh.desicrics.auth

import androidx.compose.runtime.Composable
import dev.gitlive.firebase.auth.AuthCredential

enum class SocialProvider(val displayName: String) {
    GOOGLE("Google"),
    APPLE("Apple"),
}

/**
 * Runs the platform's native sign-in UI (Credential Manager on Android, Google / Apple SDKs on iOS)
 * and turns the result into a Firebase credential.
 */
interface SocialSignIn {
    val providers: List<SocialProvider>

    /** Returns null if the user cancelled; throws on failure. */
    suspend fun signIn(provider: SocialProvider): SocialSignInResult?
}

class SocialSignInResult(
    val credential: AuthCredential,
    /** Some providers (Apple) only share the user's name here, and only on first sign-in. */
    val displayName: String? = null,
)

@Composable
expect fun rememberSocialSignIn(): SocialSignIn
