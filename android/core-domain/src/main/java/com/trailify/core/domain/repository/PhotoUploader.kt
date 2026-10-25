package com.trailify.core.domain.repository

import com.trailify.core.domain.model.PhotoRef

/**
 * Wysyła zdjęcie z telefonu do Supabase Storage. Wynik ([PhotoRef]) dołącza się do
 * [com.trailify.core.domain.model.RouteDraft], recenzji albo propozycji punktu.
 * Zdjęcie jest zmniejszane (dłuższy bok do 1600 px) i zapisywane jako JPEG.
 * Rzuca AppException: NETWORK, UNAUTHORIZED, INVALID_IMAGE albo UPLOAD_FAILED.
 */
interface PhotoUploader {
    /** [imageUri] to adres wybranego zdjęcia (np. content://... ze selektora zdjęć). */
    suspend fun upload(imageUri: String): PhotoRef
}
