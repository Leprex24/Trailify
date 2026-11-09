package com.trailify.feature.map

import com.trailify.core.domain.model.MapLayerType

/** Od jakiego przybliżenia warstwa jest widoczna. Warstwa ścieżek jest gęsta, więc działa dopiero z bliska. */
internal fun MapLayerType.minZoom(): Double = when (this) {
    MapLayerType.FOREST_PATHS -> 13.0
    else -> 10.0
}

internal fun MapLayerType.label(): String = when (this) {
    MapLayerType.CYCLE_PATHS -> "Ścieżki rowerowe"
    MapLayerType.HIKING_TRAILS -> "Szlaki piesze"
    MapLayerType.FOREST_PATHS -> "Ścieżki leśne i parkowe"
}

// Stonowane kolory i kreski zamiast pełnych, jaskrawych linii: warstwy nie przykrywają mapy.
internal fun MapLayerType.color(): String = when (this) {
    MapLayerType.CYCLE_PATHS -> "#3A78B5"
    MapLayerType.HIKING_TRAILS -> "#B5483F"
    MapLayerType.FOREST_PATHS -> "#8A6D4B"
}

internal fun MapLayerType.lineWidth(): Float = when (this) {
    MapLayerType.FOREST_PATHS -> 2f
    else -> 3f
}

/** Wzór kreskowania (w jednostkach szerokości linii) albo null dla linii ciągłej. */
internal fun MapLayerType.dash(): Array<Float>? = when (this) {
    MapLayerType.CYCLE_PATHS -> null
    MapLayerType.HIKING_TRAILS -> arrayOf(2.5f, 1.5f)
    MapLayerType.FOREST_PATHS -> arrayOf(1.2f, 1.8f)
}
