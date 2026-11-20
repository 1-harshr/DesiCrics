package com.harsh.desicrics.ui.system

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream

/** The folder in Pictures that saved cards go to. */
private const val ALBUM = "DesiCrics"

@Composable
actual fun rememberShareText(): (text: String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { text ->
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
            context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

/**
 * Saves the pictures in the app's cache and shares them through the FileProvider declared in the app's
 * manifest (authority "<applicationId>.share"). Each share overwrites the last; nothing piles up.
 */
@Composable
actual fun rememberShareImage(): suspend (images: List<ImageBitmap>, format: ImageFileFormat) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { images, format ->
            val uris = withContext(Dispatchers.IO) {
                val dir = File(context.cacheDir, "shared").apply { mkdirs() }
                images.mapIndexed { i, image ->
                    // One file per format, so a PNG never goes out with a .jpg name or the other way round.
                    val file = File(dir, if (i == 0) "desicrics-score.${format.extension}" else "desicrics-score-${i + 1}.${format.extension}")
                    file.outputStream().use { image.write(format, it) }
                    FileProvider.getUriForFile(context, "${context.packageName}.share", file)
                }
            }
            val send = if (uris.size == 1) {
                Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first())
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            }
            send.setType(format.mimeType).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

/**
 * Android 10 and up write straight to MediaStore (Pictures/DesiCrics) with no permission. Older versions
 * ask for storage permission once, then write the file and tell the media scanner about it.
 */
@Composable
actual fun rememberSaveImage(): suspend (image: ImageBitmap, format: ImageFileFormat, name: String) -> Boolean {
    val context = LocalContext.current
    val waiting = remember { arrayOfNulls<CompletableDeferred<Boolean>>(1) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        waiting[0]?.complete(granted)
    }
    return remember(context, permission) {
        { image, format, name ->
            val allowed = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED ||
                CompletableDeferred<Boolean>().also {
                    waiting[0] = it
                    permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }.await()
            allowed && withContext(Dispatchers.IO) { runCatching { saveToGallery(context, image, format, name) }.getOrDefault(false) }
        }
    }
}

private fun saveToGallery(context: Context, image: ImageBitmap, format: ImageFileFormat, name: String): Boolean {
    val fileName = "$name.${format.extension}"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, format.mimeType)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values) ?: return false
        val written = runCatching { resolver.openOutputStream(uri)?.use { image.write(format, it) } ?: false }.getOrDefault(false)
        if (!written) {
            resolver.delete(uri, null, null)
            return false
        }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        return true
    }
    @Suppress("DEPRECATION")
    val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), ALBUM).apply { mkdirs() }
    val file = File(dir, fileName)
    if (!file.outputStream().use { image.write(format, it) }) return false
    MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(format.mimeType), null)
    return true
}

private fun ImageBitmap.write(format: ImageFileFormat, out: OutputStream): Boolean = when (format) {
    ImageFileFormat.PNG -> asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, out)
    ImageFileFormat.JPEG -> asAndroidBitmap().compress(Bitmap.CompressFormat.JPEG, 92, out)
}
