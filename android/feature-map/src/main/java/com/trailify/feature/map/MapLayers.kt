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

internal fun MapLayerType.color(): String = when (this) {
    MapLayerType.CYCLE_PATHS -> "#1565C0"
    MapLayerType.HIKING_TRAILS -> "#D32F2F"
    MapLayerType.FOREST_PATHS -> "#6D4C41"
}

internal fun MapLayerType.lineWidth(): Float = when (this) {
    MapLayerType.FOREST_PATHS -> 2f
    else -> 3f
}
