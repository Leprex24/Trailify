package com.trailify.core.data.mock

import com.trailify.core.domain.model.PhotoRef
import com.trailify.core.domain.repository.PhotoUploader
import kotlinx.coroutines.delay
import java.util.UUID

/** Atrapa bez Supabase: nic nie wysyła, zwraca adres zdjęcia zastępczego. */
class MockPhotoUploader : PhotoUploader {
    override suspend fun upload(imageUri: String): PhotoRef {
        delay(400)
        val id = UUID.randomUUID().toString()
        return PhotoRef(storageKey = "mock/$id.jpg", url = "https://picsum.photos/seed/$id/800/600")
    }
}
