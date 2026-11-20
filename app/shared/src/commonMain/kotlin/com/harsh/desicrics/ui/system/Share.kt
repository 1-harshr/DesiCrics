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
 * Opens the system share sheet with one or more pictures, e.g. a scorecard card for WhatsApp status, or
 * every page of a long poster together, written in the given format. Encoding happens off the main
 * thread, so the returned function suspends.
 */
@Composable
expect fun rememberShareImage(): suspend (images: List<ImageBitmap>, format: ImageFileFormat) -> Unit

/**
 * Saves a picture to the phone's gallery under [name] (no extension), asking for storage permission
 * first where the OS needs it. The returned function says whether it was saved.
 */
@Composable
expect fun rememberSaveImage(): suspend (image: ImageBitmap, format: ImageFileFormat, name: String) -> Boolean

/**
 * Puts a picture on the clipboard, so it can be pasted straight into a chat. The returned function says
 * whether it was copied.
 */
@Composable
expect fun rememberCopyImage(): suspend (image: ImageBitmap, format: ImageFileFormat) -> Boolean
