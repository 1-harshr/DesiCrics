package com.harsh.desicrics.ui.system

import androidx.compose.runtime.Composable

/**
 * Once per signed-in session: asks to show notifications where the platform requires it (unless
 * [askPermission] is false because onboarding just asked), then hands this device's push token to
 * [onToken] for the server. Does nothing if push isn't set up.
 */
@Composable
expect fun RegisterForPush(onToken: suspend (String) -> Unit, askPermission: Boolean = true)

/**
 * Asks to show notifications and reports whether they were allowed. Null when there's nothing to ask:
 * they're already allowed, or the platform doesn't need permission.
 */
@Composable
expect fun rememberNotificationPermission(onResult: (allowed: Boolean) -> Unit): (() -> Unit)?
