package com.harsh.desicrics.ui.system

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

/** Opens the system share sheet with [text], e.g. a team invite for WhatsApp. */
@Composable
expect fun rememberShareText(): (text: String) -> Unit

/**
 * How a picture is written to a file. [PNG] keeps the alpha channel, for transparent overlays; [JPEG] is
 * much smaller, for opaque cards.
 */
enum class ImageFileFormat(val extension: String, val mimeType: String) {
    PNG("png", "image/png"),
    JPEG("jpg", "image/jpeg"),
}

/**
 * Opens the system share sheet with a picture, e.g. a scorecard card for WhatsApp status, written in
 * the given format. Encoding happens off the main thread, so the returned function suspends.
 */
@Composable
expect fun rememberShareImage(): suspend (image: ImageBitmap, format: ImageFileFormat) -> Unit

/**
 * Saves a picture to the phone's gallery under [name] (no extension), asking for storage permission
 * first where the OS needs it. The returned function says whether it was saved.
 */
@Composable
expect fun rememberSaveImage(): suspend (image: ImageBitmap, format: ImageFileFormat, name: String) -> Boolean
