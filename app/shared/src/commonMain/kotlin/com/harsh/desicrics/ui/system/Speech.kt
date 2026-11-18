package com.harsh.desicrics.ui.system

import androidx.compose.runtime.Composable

/**
 * Listens once and hands back the best guess at what was said, or null if nothing was caught. Returns
 * null itself where speech input isn't available, so callers can hide the button.
 */
@Composable
expect fun rememberSpeechInput(onHeard: (String?) -> Unit): (() -> Unit)?
