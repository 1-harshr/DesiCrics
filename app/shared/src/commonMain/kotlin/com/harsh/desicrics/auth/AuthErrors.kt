package com.harsh.desicrics.auth

import dev.gitlive.firebase.FirebaseNetworkException
import dev.gitlive.firebase.FirebaseTooManyRequestsException
import dev.gitlive.firebase.auth.FirebaseAuthException
import dev.gitlive.firebase.auth.FirebaseAuthUserCollisionException
import dev.gitlive.firebase.auth.code

/** Thrown when a sign-in method isn't configured for this build yet (e.g. no Google web client ID). */
class SignInNotConfiguredException(message: String) : IllegalStateException(message)

/** Everything that can go wrong while signing in. The UI picks the text for each one. */
enum class SignInError {
    PHONE_INVALID,
    CODE_INCOMPLETE,
    OFFLINE,
    TOO_MANY_ATTEMPTS,
    ACCOUNT_EXISTS,
    SMS_REGION,
    SMS_BILLING,
    METHOD_DISABLED,
    ACCOUNT_DISABLED,
    CODE_WRONG,
    CODE_EXPIRED,
    SMS_QUOTA,
    UNKNOWN,
}

/** Classifies a Firebase Auth failure so a player gets something they can act on. */
fun Throwable.toSignInError(): SignInError {
    val raw = message.orEmpty()
    val code = (this as? FirebaseAuthException)?.code.orEmpty()
    return when {
        this is FirebaseNetworkException -> SignInError.OFFLINE
        this is FirebaseTooManyRequestsException -> SignInError.TOO_MANY_ATTEMPTS
        this is FirebaseAuthUserCollisionException -> SignInError.ACCOUNT_EXISTS
        this is SignInNotConfiguredException -> SignInError.METHOD_DISABLED
        // New Firebase projects allow SMS to no regions until one is added under Auth → Settings → SMS region policy.
        "region enabled" in raw -> SignInError.SMS_REGION
        "BILLING_NOT_ENABLED" in raw -> SignInError.SMS_BILLING
        // CONFIGURATION_NOT_FOUND: Authentication was never set up for the Firebase project in google-services.json.
        code == "ERROR_OPERATION_NOT_ALLOWED" || "OPERATION_NOT_ALLOWED" in raw || "CONFIGURATION_NOT_FOUND" in raw ->
            SignInError.METHOD_DISABLED
        code == "ERROR_USER_DISABLED" -> SignInError.ACCOUNT_DISABLED
        code == "ERROR_INVALID_PHONE_NUMBER" -> SignInError.PHONE_INVALID
        code == "ERROR_INVALID_VERIFICATION_CODE" -> SignInError.CODE_WRONG
        code == "ERROR_SESSION_EXPIRED" -> SignInError.CODE_EXPIRED
        code == "ERROR_QUOTA_EXCEEDED" -> SignInError.SMS_QUOTA
        else -> SignInError.UNKNOWN
    }
}
