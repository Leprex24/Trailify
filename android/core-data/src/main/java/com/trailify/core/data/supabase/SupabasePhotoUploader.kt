package com.trailify.core.data.supabase

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Log
import com.trailify.core.domain.model.AppException
import com.trailify.core.domain.model.PhotoRef
import com.trailify.core.domain.repository.AuthErrorCodes
import com.trailify.core.domain.repository.PhotoUploader
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Zdjęcia w Supabase Storage (bucket `photos`, patrz docs/supabase-storage.sql).
 * Plik trafia do folderu użytkownika: photos/<uuid-użytkownika>/<losowy-uuid>.jpg, a adres do odczytu jest publiczny.
 */
class SupabasePhotoUploader(
    private val context: Context,
    private val supabase: SupabaseClient,
    supabaseUrl: String,
    private val publishableKey: String,
) : PhotoUploader {

    private val baseUrl = supabaseUrl.trimEnd('/')

    private val client = HttpClient(OkHttp) {
        expectSuccess = false
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 60_000
        }
    }

    override suspend fun upload(imageUri: String): PhotoRef {
        val userId = supabase.auth.currentUserOrNull()?.id ?: throw AppException(UNAUTHORIZED, "Brak sesji")
        val token = supabase.auth.currentAccessTokenOrNull() ?: throw AppException(UNAUTHORIZED, "Brak sesji")

        val bytes = withContext(Dispatchers.IO) { compress(Uri.parse(imageUri)) }
        val key = "$userId/${UUID.randomUUID()}.jpg"

        val response = try {
            client.post("$baseUrl/storage/v1/object/$BUCKET/$key") {
                header("Authorization", "Bearer $token")
                header("apikey", publishableKey)
                contentType(ContentType.Image.JPEG)
                setBody(bytes)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Log.w(TAG, "Brak połączenia z magazynem zdjęć", e)
            throw AppException(AuthErrorCodes.NETWORK, e.message ?: "Brak połączenia")
        }
        if (response.status.value !in 200..299) {
            Log.w(TAG, "Wysyłanie zdjęcia -> ${response.status.value} ${response.bodyAsText().take(500)}")
            throw AppException(UPLOAD_FAILED, "Nie udało się wysłać zdjęcia (${response.status.value})")
        }
        return PhotoRef(storageKey = key, url = "$baseUrl/storage/v1/object/public/$BUCKET/$key")
    }

    /** Zmniejsza zdjęcie do [MAX_SIDE] px (dłuższy bok) i zapisuje jako JPEG. */
    private fun compress(uri: Uri): ByteArray {
        val bitmap = try {
            decode(uri)
        } catch (e: Exception) {
            Log.w(TAG, "Nie można odczytać zdjęcia $uri", e)
            throw AppException(INVALID_IMAGE, "Nie można odczytać zdjęcia")
        }
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }

    private fun decode(uri: Uri): Bitmap =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // ImageDecoder uwzględnia obrót zapisany w zdjęciu
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longest = max(info.size.width, info.size.height)
                if (longest > MAX_SIDE) {
                    val scale = MAX_SIDE.toFloat() / longest
                    decoder.setTargetSize((info.size.width * scale).roundToInt(), (info.size.height * scale).roundToInt())
                }
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE * 2) sample *= 2
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: throw IOException("Nie można otworzyć zdjęcia")
        }

    private companion object {
        const val TAG = "PhotoUploader"
        const val BUCKET = "photos"
        const val MAX_SIDE = 1600
        const val JPEG_QUALITY = 82
        const val UNAUTHORIZED = "UNAUTHORIZED"
        const val UPLOAD_FAILED = "UPLOAD_FAILED"
        const val INVALID_IMAGE = "INVALID_IMAGE"
    }
}
