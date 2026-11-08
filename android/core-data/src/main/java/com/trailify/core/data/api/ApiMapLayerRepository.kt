package com.trailify.core.data.api

import android.os.SystemClock
import android.util.Log
import com.trailify.core.domain.model.AppException
import com.trailify.core.domain.model.BoundingBox
import com.trailify.core.domain.model.LatLng
import com.trailify.core.domain.model.MapLayerType
import com.trailify.core.domain.repository.MapLayerRepository
import io.ktor.http.HttpMethod
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale

/**
 * Warstwy mapy z naszego backendu (GET /map-layers/{type}), który bierze je z własnego serwera Overpass.
 *
 * Gdy backend nie odpowiada (komputer z serwerem wyłączony, brak sesji, błąd), dane pobiera [fallback]
 * (publiczne serwery Overpass). Po błędzie backendu przez [BACKEND_PAUSE_MS] od razu używamy zapasowego źródła,
 * żeby każdy kafelek nie czekał osobno na przekroczenie czasu.
 */
class ApiMapLayerRepository(
    private val api: ApiClient,
    private val fallback: MapLayerRepository,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) : MapLayerRepository {

    @Volatile
    private var backendPausedUntil = 0L

    override suspend fun load(type: MapLayerType, box: BoundingBox): List<List<LatLng>> {
        if (clock() < backendPausedUntil) return fallback.load(type, box)
        val text = try {
            api.call(HttpMethod.Get, mapLayerPath(type, box))
        } catch (e: CancellationException) {
            throw e
        } catch (e: AppException) {
            Log.w(TAG, "Backend nie dał warstwy ${type.name} (${e.code}), używam zapasowego źródła")
            // błąd w naszym żądaniu nie oznacza, że backend nie działa
            if (e.code != VALIDATION_ERROR) backendPausedUntil = clock() + BACKEND_PAUSE_MS
            return fallback.load(type, box)
        }
        return try {
            withContext(Dispatchers.Default) { parseBackendLines(text) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Nie udało się odczytać odpowiedzi backendu dla warstwy ${type.name}", e)
            fallback.load(type, box)
        }
    }

    private companion object {
        const val TAG = "ApiMapLayers"
        const val VALIDATION_ERROR = "VALIDATION_ERROR"
        const val BACKEND_PAUSE_MS = 60_000L
    }
}

/** Ścieżka żądania. Liczby zawsze z kropką (Locale.US), bo w polskim ustawieniu telefonu format dałby przecinek. */
internal fun mapLayerPath(type: MapLayerType, box: BoundingBox): String = String.format(
    Locale.US,
    "/map-layers/%s?minLat=%.6f&minLng=%.6f&maxLat=%.6f&maxLng=%.6f",
    type.name, box.minLat, box.minLng, box.maxLat, box.maxLng,
)

/** Odpowiedź backendu: {"lines": [[[szerokość, długość], ...], ...]}. */
internal fun parseBackendLines(json: String): List<List<LatLng>> {
    val lines = Json.parseToJsonElement(json).jsonObject.getValue("lines").jsonArray
    return lines.map { line ->
        line.jsonArray.map { point ->
            val pair = point.jsonArray
            LatLng(pair[0].jsonPrimitive.double, pair[1].jsonPrimitive.double)
        }
    }
}
