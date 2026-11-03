package com.trailify.core.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.trailify.core.domain.model.PhotoRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Zdjęcie dołączane do formularza (trasa, recenzja): wybrane z telefonu ([localUri])
 * i wysyłane od razu do Storage przez PhotoUploader.
 */
data class PhotoItem(
    val localUri: String,
    val uploaded: PhotoRef? = null,
    val error: String? = null,
) {
    val isUploading: Boolean get() = uploaded == null && error == null
}

/** Ile z [picked] wybranych zdjęć zmieści się, gdy formularz ma już [current], a limit to [max]. */
fun photosThatFit(current: Int, picked: Int, max: Int): Int = picked.coerceAtMost(max - current).coerceAtLeast(0)

val PHOTO_TILE_SIZE = 96.dp

/** Kafelek wybranego zdjęcia: podgląd, kółko podczas wysyłania, ponowienie po błędzie i usunięcie. */
@Composable
fun PhotoTile(
    photo: PhotoItem,
    onRemove: () -> Unit,
    onRetry: () -> Unit,
    size: Dp = PHOTO_TILE_SIZE,
) {
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val thumbnail by rememberThumbnail(photo.localUri, sizePx)
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        thumbnail?.let {
            Image(it, contentDescription = "Wybrane zdjęcie", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        when {
            photo.isUploading -> Box(Modifier.fillMaxSize().background(SCRIM), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp, color = Color.White)
            }
            photo.error != null -> Box(Modifier.fillMaxSize().background(SCRIM), contentAlignment = Alignment.Center) {
                IconButton(onClick = onRetry) {
                    Icon(Icons.Default.Refresh, contentDescription = "Wyślij zdjęcie ponownie", tint = Color.White)
                }
            }
        }
        IconButton(
            onClick = onRemove,
            modifier = Modifier.align(Alignment.TopEnd).size(32.dp),
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Usuń zdjęcie",
                tint = Color.White,
                modifier = Modifier.background(SCRIM, RoundedCornerShape(50)).padding(2.dp),
            )
        }
    }
}

private val SCRIM = Color.Black.copy(alpha = 0.45f)

/** Miniatura zdjęcia z telefonu (content://...), wczytywana w tle i zmniejszana do ok. [sizePx]. */
@Composable
private fun rememberThumbnail(uri: String, sizePx: Int): State<ImageBitmap?> {
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
