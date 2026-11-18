package com.harsh.desicrics.ui.system

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

/** Opens the system share sheet with [text], e.g. a team invite for WhatsApp. */
@Composable
expect fun rememberShareText(): (text: String) -> Unit

/** Opens the system share sheet with a picture, e.g. a scorecard card for WhatsApp status. */
@Composable
expect fun rememberShareImage(): (image: ImageBitmap) -> Unit
