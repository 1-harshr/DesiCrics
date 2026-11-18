package com.harsh.desicrics.ui.system

import androidx.compose.runtime.Composable

/**
 * Opens the system photo picker and hands back the chosen picture as a JPEG no bigger than 512 pixels
 * on its long side, or null if nothing was picked or it couldn't be read.
 */
@Composable
expect fun rememberImagePicker(onPicked: (jpeg: ByteArray?) -> Unit): () -> Unit
