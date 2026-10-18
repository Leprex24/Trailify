package com.trailify.feature.map

import com.trailify.core.domain.model.BoundingBox
import kotlin.math.floor

// Mapę dzielimy na stałe kafelki i pobieramy każdy osobno. Przy dużym przybliżeniu używamy małych
// kafelków (ok. 5,5 x 5,5 km), a przy oddaleniu dużych (trzy razy większych w każdą stronę),
// żeby widok nie wymagał kilkudziesięciu zapytań.
internal const val TILE_LAT = 0.05
internal const val TILE_LNG = 0.08

/** Poziom 0 = małe kafelki, poziom 1 = duże. */
internal val LEVEL_SCALE = intArrayOf(1, 3)

/** Poniżej tego przybliżenia używamy dużych kafelków. */
internal const val LARGE_TILES_BELOW_ZOOM = 11.5

internal fun levelFor(zoom: Double): Int = if (zoom >= LARGE_TILES_BELOW_ZOOM) 0 else 1

internal data class TileKey(val level: Int, val x: Int, val y: Int) {
    fun box(): BoundingBox {
        val latSize = TILE_LAT * LEVEL_SCALE[level]
        val lngSize = TILE_LNG * LEVEL_SCALE[level]
        return BoundingBox(
            minLat = y * latSize,
            minLng = x * lngSize,
            maxLat = (y + 1) * latSize,
            maxLng = (x + 1) * lngSize,
        )
    }
}

/**
 * Kafelki danego poziomu pokrywające prostokąt, od najbliższych jego środka (najwyżej [maxTiles]).
 */
internal fun tilesFor(box: BoundingBox, level: Int, maxTiles: Int): List<TileKey> {
    val latSize = TILE_LAT * LEVEL_SCALE[level]
    val lngSize = TILE_LNG * LEVEL_SCALE[level]
    val x0 = floor(box.minLng / lngSize).toInt()
    val x1 = floor(box.maxLng / lngSize).toInt()
    val y0 = floor(box.minLat / latSize).toInt()
    val y1 = floor(box.maxLat / latSize).toInt()
    if ((x1 - x0 + 1).toLong() * (y1 - y0 + 1) > 2_000) return emptyList() // zabezpieczenie przed absurdalnie dużym obszarem

    val centerX = (box.minLng + box.maxLng) / 2 / lngSize
    val centerY = (box.minLat + box.maxLat) / 2 / latSize
    val tiles = ArrayList<TileKey>()
    for (x in x0..x1) {
        for (y in y0..y1) tiles.add(TileKey(level, x, y))
    }
    return tiles
        .sortedBy { (it.x + 0.5 - centerX) * (it.x + 0.5 - centerX) + (it.y + 0.5 - centerY) * (it.y + 0.5 - centerY) }
        .take(maxTiles)
}

/** Powiększa prostokąt o podany ułamek jego rozmiaru z każdej strony. */
internal fun BoundingBox.expanded(fraction: Double): BoundingBox {
    val dLat = (maxLat - minLat) * fraction
    val dLng = (maxLng - minLng) * fraction
    return BoundingBox(minLat - dLat, minLng - dLng, maxLat + dLat, maxLng + dLng)
}
