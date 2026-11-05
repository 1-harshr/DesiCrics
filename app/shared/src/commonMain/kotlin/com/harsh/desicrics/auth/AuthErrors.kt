package com.harsh.desicrics.auth

import dev.gitlive.firebase.FirebaseNetworkException
import dev.gitlive.firebase.FirebaseTooManyRequestsException
import dev.gitlive.firebase.auth.FirebaseAuthException
import dev.gitlive.firebase.auth.FirebaseAuthUserCollisionException
import dev.gitlive.firebase.auth.FirebaseAuthWeakPasswordException
import dev.gitlive.firebase.auth.code

/** Turns Firebase Auth failures into something a player can act on. */
fun Throwable.toAuthMessage(): String {
    val raw = message.orEmpty()
    val code = (this as? FirebaseAuthException)?.code.orEmpty()
    return when {
        this is FirebaseNetworkException -> "No internet connection. Check your network and try again."
        this is FirebaseTooManyRequestsException -> "Too many attempts. Please wait a few minutes and try again."
        this is FirebaseAuthWeakPasswordException -> "Choose a stronger password (at least 6 characters)."
        this is FirebaseAuthUserCollisionException -> "An account already exists with this email. Sign in instead."
        // New Firebase projects allow SMS to no regions until one is added under Auth → Settings → SMS region policy.
        "region enabled" in raw -> "We can't send codes to this country yet. Try another sign-in method."
        "BILLING_NOT_ENABLED" in raw ->
            "SMS codes need the Firebase Blaze plan. Use a test phone number from the Firebase console for now."
        code == "ERROR_OPERATION_NOT_ALLOWED" || "OPERATION_NOT_ALLOWED" in raw -> "This sign-in method isn't enabled yet."
        code == "ERROR_INVALID_EMAIL" -> "That email address doesn't look right."
        code == "ERROR_USER_NOT_FOUND" || code == "ERROR_WRONG_PASSWORD" || code == "ERROR_INVALID_CREDENTIAL" ||
            "INVALID_LOGIN_CREDENTIALS" in raw -> "Incorrect email or password."
        code == "ERROR_USER_DISABLED" -> "This account has been disabled."
        code == "ERROR_INVALID_PHONE_NUMBER" -> "Enter a valid mobile number, including the country code."
        code == "ERROR_INVALID_VERIFICATION_CODE" -> "That code is incorrect. Check the SMS and try again."
        code == "ERROR_SESSION_EXPIRED" -> "That code has expired. Send a new one."
        code == "ERROR_QUOTA_EXCEEDED" -> "We've hit today's SMS limit. Please try again later."
        else -> raw.ifBlank { "Something went wrong. Please try again." }
    }
}
