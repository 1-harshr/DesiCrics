package com.harsh.desicrics.ui.system

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch

@Composable
actual fun RegisterForPush(onToken: suspend (String) -> Unit, askPermission: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // The token is worth sending even if notifications are refused: they can be allowed later in settings.
    val register = {
        runCatching {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token -> scope.launch { runCatching { onToken(token) } } }
        }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { register() }
    LaunchedEffect(Unit) {
        if (askPermission && context.needsNotificationPermission()) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) else register()
    }
}

@Composable
actual fun rememberNotificationPermission(onResult: (allowed: Boolean) -> Unit): (() -> Unit)? {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), onResult)
    val needed = remember(context) { context.needsNotificationPermission() }
    return if (needed) ({ launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }) else null
}

/** Android 13 and up ask before showing notifications. */
private fun Context.needsNotificationPermission() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
