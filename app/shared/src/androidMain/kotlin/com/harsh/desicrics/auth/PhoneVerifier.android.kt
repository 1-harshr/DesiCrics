package com.harsh.desicrics.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.FirebaseException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.AuthCredential
import dev.gitlive.firebase.auth.android
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Composable
actual fun rememberPhoneVerifier(): PhoneVerifier {
    // Firebase falls back to a reCAPTCHA in a browser tab when Play Integrity can't verify the app,
    // which needs an Activity to launch from.
    val activity = LocalContext.current.findActivity()
    return remember(activity) { AndroidPhoneVerifier(checkNotNull(activity) { "Phone sign-in needs an Activity" }) }
}

private class AndroidPhoneVerifier(private val activity: Activity) : PhoneVerifier {

    override suspend fun sendCode(phoneNumber: String): PhoneCodeResult = suspendCancellableCoroutine { continuation ->
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
                if (continuation.isActive) continuation.resume(PhoneCodeResult.CodeSent(verificationId))
            }

            // Can also fire after onCodeSent; by then the user is already on the code screen, so it's ignored.
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                if (continuation.isActive) continuation.resume(PhoneCodeResult.AutoVerified(AuthCredential(credential)))
            }

            override fun onVerificationFailed(e: FirebaseException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
        }
        val options = PhoneAuthOptions.newBuilder(Firebase.auth.android)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
