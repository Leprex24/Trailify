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
// Na zdjęciach satelitarnych (jesienią brąz na brązie) kolory są jaskrawsze, a linie grubsze i z jasną obwódką.
internal fun MapLayerType.color(satellite: Boolean = false): String = when (this) {
    MapLayerType.CYCLE_PATHS -> if (satellite) "#00B8FF" else "#3A78B5"
    MapLayerType.HIKING_TRAILS -> if (satellite) "#FF1744" else "#B5483F"
    MapLayerType.FOREST_PATHS -> if (satellite) "#FFEA00" else "#8A6D4B"
}

internal fun MapLayerType.lineWidth(satellite: Boolean = false): Float {
    val width = if (this == MapLayerType.FOREST_PATHS) 2f else 3f
    return if (satellite) width + 1f else width
}

/** Wzór kreskowania (w jednostkach szerokości linii) albo null dla linii ciągłej. */
internal fun MapLayerType.dash(): Array<Float>? = when (this) {
    MapLayerType.CYCLE_PATHS -> null
    MapLayerType.HIKING_TRAILS -> arrayOf(2.5f, 1.5f)
    MapLayerType.FOREST_PATHS -> arrayOf(1.2f, 1.8f)
}
