package com.trailify.core.domain.route

import com.trailify.core.domain.model.ActivityType
import com.trailify.core.domain.model.LatLng
import com.trailify.core.domain.model.RouteSummary
import com.trailify.core.domain.recording.distanceMeters
import java.text.Collator
import java.util.Locale

/** Przedziały długości trasy w filtrze (metry). */
enum class RouteLength(val label: String, val min: Double?, val max: Double?) {
    ANY("Dowolna", null, null),
    SHORT("Do 5 km", null, 5_000.0),
    MEDIUM("5-15 km", 5_000.0, 15_000.0),
    LONG("15-40 km", 15_000.0, 40_000.0),
    VERY_LONG("Ponad 40 km", 40_000.0, null),
}

/** Widoczność trasy (ma sens tylko na liście własnych tras). */
enum class RouteVisibility(val label: String) {
    ALL("Wszystkie"),
    PUBLIC("Publiczne"),
    PRIVATE("Prywatne"),
}

enum class RouteListSort(val label: String) {
    NEWEST("Najnowsze"),
    OLDEST("Najstarsze"),
    LONGEST("Najdłuższe"),
    SHORTEST("Najkrótsze"),
    BEST_RATED("Najwyżej oceniane"),
    NAME("Nazwa A-Z"),
    NEAREST("Najbliżej mnie"),
}

/** Promienie (km) do filtra "blisko mnie"; null = bez ograniczenia. */
val NEAR_RADII_KM = listOf<Int?>(null, 5, 10, 25, 50)

/** Wyszukiwanie, filtry i sortowanie list "Własne" i "Zapisane" na ekranie Trasy. */
data class RouteListFilters(
    val text: String = "",
    val activityTypes: Set<ActivityType> = emptySet(),
    val length: RouteLength = RouteLength.ANY,
    val visibility: RouteVisibility = RouteVisibility.ALL,
    /** Trasa ma zaczynać się w promieniu tylu km od użytkownika; null = bez ograniczenia. */
    val nearKm: Int? = null,
    val touristOnly: Boolean = false,
    val scenicOnly: Boolean = false,
    val sort: RouteListSort = RouteListSort.NEWEST,
) {
    /** Liczba ustawionych filtrów z arkusza "Filtry" (do plakietki na przycisku). */
    val sheetFilterCount: Int
        get() = listOf(
            length != RouteLength.ANY,
            visibility != RouteVisibility.ALL,
            nearKm != null,
            touristOnly,
            scenicOnly,
        ).count { it }

    /** Czy wyniki są zawężone (tekst, typ albo filtr z arkusza). */
    val isNarrowed: Boolean
        get() = text.isNotBlank() || activityTypes.isNotEmpty() || sheetFilterCount > 0
}

/**
 * Zwraca trasy spełniające [filters], posortowane. [userLocation] (null, gdy nieznana) jest potrzebna do filtra
 * odległości i sortowania "Najbliżej mnie": bez niej filtr jest pomijany, a sortowanie wraca do "Najnowsze".
 */
fun List<RouteSummary>.filterAndSort(filters: RouteListFilters, userLocation: LatLng?): List<RouteSummary> {
    val text = filters.text.trim()
    val filtered = filter { route ->
        (text.isEmpty() || route.name.contains(text, ignoreCase = true)) &&
            (filters.activityTypes.isEmpty() || route.activityType in filters.activityTypes) &&
            matchesLength(route, filters.length) &&
            when (filters.visibility) {
                RouteVisibility.ALL -> true
                RouteVisibility.PUBLIC -> route.isPublic
                RouteVisibility.PRIVATE -> !route.isPublic
            } &&
            (!filters.touristOnly || route.isTourist) &&
            (!filters.scenicOnly || route.isScenic) &&
            matchesNear(route, filters.nearKm, userLocation)
    }
    val polish = Collator.getInstance(Locale.forLanguageTag("pl-PL"))
    return when (filters.sort) {
        RouteListSort.NEWEST -> filtered.sortedByDescending { it.createdAt }
        RouteListSort.OLDEST -> filtered.sortedBy { it.createdAt }
        RouteListSort.LONGEST -> filtered.sortedByDescending { it.totalDistance ?: 0.0 }
        RouteListSort.SHORTEST -> filtered.sortedBy { it.totalDistance ?: Double.MAX_VALUE }
        // trasy bez ocen na końcu; przy równej ocenie wyżej ta z większą liczbą opinii
        RouteListSort.BEST_RATED -> filtered.sortedWith(
            compareByDescending<RouteSummary> { it.averageRating ?: -1.0 }
                .thenByDescending { it.reviewCount }
                .thenByDescending { it.createdAt },
        )
        RouteListSort.NAME -> filtered.sortedWith(Comparator { a, b -> polish.compare(a.name, b.name) })
        RouteListSort.NEAREST ->
            if (userLocation == null) {
                filtered.sortedByDescending { it.createdAt }
            } else {
                filtered.sortedBy { route -> route.startPoint?.let { distanceMeters(userLocation, it) } ?: Double.MAX_VALUE }
            }
    }
}

private fun matchesLength(route: RouteSummary, length: RouteLength): Boolean {
    if (length == RouteLength.ANY) return true
    val distance = route.totalDistance ?: return false
    return (length.min == null || distance >= length.min) && (length.max == null || distance <= length.max)
}

private fun matchesNear(route: RouteSummary, nearKm: Int?, userLocation: LatLng?): Boolean {
    if (nearKm == null || userLocation == null) return true
    val start = route.startPoint ?: return false
    return distanceMeters(userLocation, start) <= nearKm * 1000.0
}
