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
    /** Liczba ustawionych filtrów z arkusza "Filtry" (do plakietki na przycisku). */
    val sheetFilterCount: Int
        get() = listOf(distance != DistanceRange.ANY, minRating != null, touristOnly, scenicOnly).count { it }

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
        /** Jedna strona wyników wystarcza na listę i mapę (API pozwala maksymalnie 100). */
        const val PAGE_SIZE = 50

        /** Limit parametru q w openapi.yaml. */
        const val MAX_QUERY_LENGTH = 100
    }
}
