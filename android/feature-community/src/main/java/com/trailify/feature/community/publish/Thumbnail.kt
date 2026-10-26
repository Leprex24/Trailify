package com.trailify.feature.community.publish

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Miniatura zdjęcia z telefonu (content://...), wczytywana w tle i zmniejszana do ok. [sizePx]. */
@Composable
internal fun rememberThumbnail(uri: String, sizePx: Int): State<ImageBitmap?> {
    val context = LocalContext.current
    return produceState<ImageBitmap?>(initialValue = null, uri, sizePx) {
        value = withContext(Dispatchers.IO) {
            runCatching { decodeSampled(context, Uri.parse(uri), sizePx)?.asImageBitmap() }.getOrNull()
        }
    }
}

private fun decodeSampled(context: Context, uri: Uri, sizePx: Int): Bitmap? {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    // Wczytujemy zmniejszoną kopię, żeby kilka dużych zdjęć nie zajęło całej pamięci.
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= sizePx && bounds.outHeight / (sample * 2) >= sizePx) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
}
