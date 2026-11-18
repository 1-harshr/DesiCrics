package com.harsh.desicrics.ui.system

import androidx.compose.runtime.Composable

/**
 * Once per signed-in session: asks to show notifications where the platform requires it, then hands
 * this device's push token to [onToken] for the server. Does nothing if push isn't set up.
 */
@Composable
expect fun RegisterForPush(onToken: suspend (String) -> Unit)
