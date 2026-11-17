package com.harsh.desicrics.auth

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.net.URI
import java.security.interfaces.RSAPublicKey
import java.util.concurrent.TimeUnit

/** The signed-in Firebase user making a request. */
data class FirebasePrincipal(
    val uid: String,
    val email: String? = null,
    val phone: String? = null,
)

fun interface TokenVerifier {
    /** Returns the user the token belongs to, or null if it isn't a valid token for this project. */
    fun verify(token: String): FirebasePrincipal?
}

/**
 * Verifies Firebase ID tokens the way the Admin SDK does, without needing a service account: check the
 * RS256 signature against Google's published keys, then the issuer, audience and expiry.
 * https://firebase.google.com/docs/auth/admin/verify-id-tokens#verify_id_tokens_using_a_third-party_jwt_library
 */
class FirebaseTokenVerifier(
    private val projectId: String,
    private val keys: JwkProvider = JwkProviderBuilder(URI(GOOGLE_KEYS_URL).toURL())
        .cached(10, 24, TimeUnit.HOURS)
        .rateLimited(10, 1, TimeUnit.MINUTES)
        .build(),
) : TokenVerifier {

    override fun verify(token: String): FirebasePrincipal? = runCatching {
        val keyId = JWT.decode(token).keyId ?: return null
        val algorithm = Algorithm.RSA256(keys.get(keyId).publicKey as RSAPublicKey, null)
        val jwt = JWT.require(algorithm)
            .withIssuer("https://securetoken.google.com/$projectId")
            .withAudience(projectId)
            .acceptLeeway(LEEWAY_SECONDS)
            .build()
            .verify(token)
        FirebasePrincipal(
            uid = jwt.subject?.takeIf { it.isNotBlank() } ?: return null,
            email = jwt.getClaim("email").asString(),
            phone = jwt.getClaim("phone_number").asString(),
        )
    }.getOrNull()

    private companion object {
        const val GOOGLE_KEYS_URL = "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com"
        const val LEEWAY_SECONDS = 5L
    }
}
