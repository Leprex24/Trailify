package com.trailify.core.domain.model

/** Warstwy tematyczne na mapie (W1). */
enum class MapLayerType {
    /** Ścieżki rowerowe i trasy rowerowe. */
    CYCLE_PATHS,

    /** Szlaki i trasy piesze: znakowane (PTTK) oraz tematyczne, miejskie i kulturowe. */
    HIKING_TRAILS,

    /** Ścieżki i drogi w lasach i parkach (bez zwykłych chodników). */
    FOREST_PATHS,
}
