package com.trailify.feature.discover

import com.trailify.core.domain.model.ActivityType
import com.trailify.core.domain.model.RouteQuery
import com.trailify.core.domain.model.RouteSort

/** Przedziały długości trasy w filtrze (metry). */
enum class DistanceRange(val label: String, val min: Double?, val max: Double?) {
    ANY("Dowolna", null, null),
    SHORT("Do 5 km", null, 5_000.0),
    MEDIUM("5-15 km", 5_000.0, 15_000.0),
    LONG("15-40 km", 15_000.0, 40_000.0),
    VERY_LONG("Ponad 40 km", 40_000.0, null),
}

/** Sortowania dostępne w Odkrywaj. "W pobliżu" (NEAREST) wymaga lokalizacji, więc na razie go nie ma. */
val DISCOVER_SORTS = listOf(RouteSort.RECOMMENDED, RouteSort.RATING, RouteSort.NEWEST, RouteSort.DISTANCE)

fun RouteSort.label(): String = when (this) {
    RouteSort.RECOMMENDED -> "Polecane"
    RouteSort.RATING -> "Najwyżej oceniane"
    RouteSort.NEWEST -> "Najnowsze"
    RouteSort.NEAREST -> "W pobliżu"
    RouteSort.DISTANCE -> "Najkrótsze"
}

/** Minimalne oceny do wyboru w filtrze (null = bez ograniczenia). */
val MIN_RATING_OPTIONS = listOf<Double?>(null, 3.0, 4.0, 4.5)

/** Wszystko, co użytkownik ustawił w Odkrywaj. Zamieniane na [RouteQuery] dla GET /routes. */
data class DiscoverFilters(
    val text: String = "",
    val activityTypes: Set<ActivityType> = emptySet(),
    val distance: DistanceRange = DistanceRange.ANY,
    val minRating: Double? = null,
    val touristOnly: Boolean = false,
    val scenicOnly: Boolean = false,
    val sort: RouteSort = RouteSort.RECOMMENDED,
) {
    /** Liczba ustawień z arkusza "Filtruj" (do plakietki na przycisku); wpisany tekst się nie liczy. */
    val filterCount: Int
        get() = listOf(
            distance != DistanceRange.ANY,
            minRating != null,
            touristOnly,
            scenicOnly,
            activityTypes.isNotEmpty(),
            sort != RouteSort.RECOMMENDED,
        ).count { it }

    /** Czy użytkownik czegoś szuka: wtedy rząd kafelków pokazuje wyniki zamiast wyboru trasy. */
    val isActive: Boolean
        get() = text.isNotBlank() || filterCount > 0

    fun toQuery(size: Int = PAGE_SIZE): RouteQuery = RouteQuery(
        activityTypes = activityTypes,
        minDistance = distance.min,
        maxDistance = distance.max,
        minRating = minRating,
        // false w filtrze znaczy "obojętnie", a nie "tylko nieturystyczne"
        isTourist = true.takeIf { touristOnly },
        isScenic = true.takeIf { scenicOnly },
        text = text.trim().takeIf { it.isNotEmpty() }?.take(MAX_QUERY_LENGTH),
        sort = sort,
        page = 0,
        size = size,
    )

    companion object {
        /** Jedna strona wyników wystarcza na kafelki i mapę (API pozwala maksymalnie 100). */
        const val PAGE_SIZE = 100

        /** Limit parametru q w openapi.yaml. */
        const val MAX_QUERY_LENGTH = 100
    }
}
