package com.harsh.desicrics.data

import com.harsh.desicrics.data.api.IdTokenProvider
import dev.gitlive.firebase.auth.AuthCredential
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.FirebaseUser
import dev.gitlive.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class AuthUser(
    val uid: String,
    val displayName: String?,
    val email: String?,
    val phone: String?,
    val photoUrl: String?,
)

class AuthRepository(private val auth: FirebaseAuth) : IdTokenProvider {

    val currentUser: AuthUser? get() = auth.currentUser?.toAuthUser()

    val authState: Flow<AuthUser?> = auth.authStateChanged.map { it?.toAuthUser() }

    /**
     * Name the sign-in provider handed over outside the Firebase user. Apple shares it only once,
     * so it's kept here to prefill onboarding.
     */
    var displayNameHint: String? = null
        private set

    suspend fun signIn(credential: AuthCredential, displayNameHint: String? = null): AuthUser {
        this.displayNameHint = displayNameHint
        return checkNotNull(auth.signInWithCredential(credential).user) { "Sign-in returned no user" }.toAuthUser()
    }

    /** Builds the credential for an SMS code sent by [com.harsh.desicrics.auth.PhoneVerifier]. */
    fun phoneCredential(verificationId: String, code: String): AuthCredential =
        PhoneAuthProvider(auth).credential(verificationId, code)

    /** Firebase ID token the DesiCrics server uses to identify the user. */
    override suspend fun idToken(forceRefresh: Boolean): String? = auth.currentUser?.getIdToken(forceRefresh)

    suspend fun signOut() = auth.signOut()

    private fun FirebaseUser.toAuthUser() = AuthUser(
        uid = uid,
        displayName = displayName,
        email = email,
        phone = phoneNumber,
        photoUrl = photoURL,
    )
}
