package com.harsh.desicrics.auth

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import dev.gitlive.firebase.auth.GoogleAuthProvider
import org.koin.compose.koinInject

/**
 * Provided by the Android app, since the web client ID comes from its google-services.json. Null when
 * that file has no web OAuth client yet (Google sign-in not enabled in Firebase); signing in then fails
 * with [SignInNotConfiguredException], which the UI explains.
 */
class GoogleSignInConfig(val webClientId: String?)

@Composable
actual fun rememberSocialSignIn(): SocialSignIn {
    // Credential Manager needs an Activity context to show its bottom sheet.
    val context = LocalContext.current
    val config = koinInject<GoogleSignInConfig>()
    return remember(context, config) { AndroidSocialSignIn(context, config) }
}

private class AndroidSocialSignIn(
    private val context: Context,
    private val config: GoogleSignInConfig,
) : SocialSignIn {

    // Sign in with Apple on Android needs Firebase's web OAuth flow; left out of the MVP.
    override val providers = listOf(SocialProvider.GOOGLE)

    override suspend fun signIn(provider: SocialProvider): SocialSignInResult? {
        require(provider == SocialProvider.GOOGLE) { "$provider sign-in isn't supported on Android" }
        val webClientId = config.webClientId ?: throw SignInNotConfiguredException("No Google web client ID in google-services.json")

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(webClientId).build())
            .build()
        val credential = try {
            CredentialManager.create(context).getCredential(context, request).credential
        } catch (_: GetCredentialCancellationException) {
            return null
        }
        check(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Unexpected credential type: ${credential.type}"
        }
        val google = GoogleIdTokenCredential.createFrom(credential.data)
        return SocialSignInResult(
            credential = GoogleAuthProvider.credential(idToken = google.idToken, accessToken = null),
            displayName = google.displayName,
        )
    }
}
