package com.trailify.backend.poi

/** Kategoria ciekawego punktu (kolumna point_of_interest.category, V7). Pokazywana tylko na karcie punktu. */
enum class PoiCategory {
    VIEWPOINT,
    NATURE,
    HISTORIC,
    CULTURE,
    RECREATION,
    OTHER,
}

/** Kategoria z tekstu albo null, gdy to nie jest znana kategoria. */
fun parsePoiCategory(value: Any?): PoiCategory? = PoiCategory.entries.firstOrNull { it.name == value }
