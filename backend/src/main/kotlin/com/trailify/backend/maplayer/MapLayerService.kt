package com.trailify.backend.maplayer

import com.trailify.backend.web.ApiException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import kotlin.math.round

/** Linie warstwy w prostokącie. Każdy punkt to [szerokość, długość] zaokrąglone do 5 miejsc (ok. 1 m). */
data class MapLayerResponse(val lines: List<List<List<Double>>>)

/** O tyle metrów można odchylić uproszczoną linię od oryginału (na mapie niewidoczne). */
const val SIMPLIFY_TOLERANCE_METERS = 2.0

private const val CACHE_ENTRIES = 300
private const val CACHE_TTL_MILLIS = 6L * 60 * 60 * 1000

@Service
class MapLayerService(private val engine: OverpassEngine) {

    private val cache = TileCache<MapLayerResponse>(CACHE_ENTRIES, CACHE_TTL_MILLIS)

    fun lines(type: MapLayerType, box: GeoBox): MapLayerResponse {
        boxError(box)?.let { throw ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", it) }

        val key = "$type|${box.minLat.round4()}|${box.minLng.round4()}|${box.maxLat.round4()}|${box.maxLng.round4()}"
        cache.get(key)?.let { return it }

        val lines = try {
            engine.lines(buildOverpassQuery(type, box))
        } catch (e: OverpassUnavailableException) {
            throw ApiException(HttpStatus.SERVICE_UNAVAILABLE, "MAP_LAYER_UNAVAILABLE", "Warstwy mapy są chwilowo niedostępne")
        }
        val response = MapLayerResponse(
            lines.map { line -> simplifyLine(line, SIMPLIFY_TOLERANCE_METERS).map { listOf(it.latitude.round5(), it.longitude.round5()) } },
        )
        cache.put(key, response)
        return response
    }
}

private fun Double.round4() = round(this * 1e4) / 1e4
private fun Double.round5() = round(this * 1e5) / 1e5
