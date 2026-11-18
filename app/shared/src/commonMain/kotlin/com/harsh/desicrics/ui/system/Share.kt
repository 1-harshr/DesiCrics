package com.harsh.desicrics.ui.system

import androidx.compose.runtime.Composable

/** Opens the system share sheet with [text], e.g. a team invite for WhatsApp. */
@Composable
expect fun rememberShareText(): (text: String) -> Unit
