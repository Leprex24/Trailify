package com.trailify.core.domain.route

import com.trailify.core.domain.model.ActivityType
import com.trailify.core.domain.model.LatLng
import com.trailify.core.domain.model.RouteSummary
import com.trailify.core.domain.model.UserSummary
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class RouteListFilterTest {

    private val lublin = LatLng(51.2465, 22.5684)

    private fun route(
        id: Long,
        name: String = "Trasa $id",
        type: ActivityType = ActivityType.WALKING,
        distance: Double? = 5_000.0,
        isPublic: Boolean = true,
        tourist: Boolean = false,
        scenic: Boolean = false,
        start: LatLng? = lublin,
        day: Int = id.toInt(),
    ) = RouteSummary(
        id = id,
        name = name,
        activityType = type,
        isTourist = tourist,
        isScenic = scenic,
        isPublic = isPublic,
        totalDistance = distance,
        durationSeconds = 1_800,
        averageRating = null,
        reviewCount = 0,
        recommendationScore = null,
        startPoint = start,
        thumbnailUrl = null,
        distanceFromQuery = null,
        owner = UserSummary(id = "u1", displayName = "Anna", avatarUrl = null),
        createdAt = Instant.parse("2026-10-%02dT10:00:00Z".format(day)),
    )

    private fun ids(list: List<RouteSummary>) = list.map { it.id }

    @Test
    fun `bez filtrow zwraca wszystko od najnowszej`() {
        val routes = listOf(route(1), route(3), route(2))
        assertEquals(listOf(3L, 2L, 1L), ids(routes.filterAndSort(RouteListFilters(), null)))
    }

    @Test
    fun `tekst szuka po nazwie bez wzgledu na wielkosc liter`() {
        val routes = listOf(route(1, name = "Zalew Zemborzycki"), route(2, name = "Park Saski"))
        val result = routes.filterAndSort(RouteListFilters(text = "  zALEW "), null)
        assertEquals(listOf(1L), ids(result))
    }

    @Test
    fun `filtr typu aktywnosci`() {
        val routes = listOf(route(1, type = ActivityType.CYCLING), route(2, type = ActivityType.RUNNING))
        val result = routes.filterAndSort(RouteListFilters(activityTypes = setOf(ActivityType.RUNNING)), null)
        assertEquals(listOf(2L), ids(result))
    }

    @Test
    fun `filtr dlugosci uwzglednia granice przedzialu`() {
        val routes = listOf(route(1, distance = 5_000.0), route(2, distance = 5_001.0), route(3, distance = 20_000.0), route(4, distance = null))
        val medium = routes.filterAndSort(RouteListFilters(length = RouteLength.MEDIUM, sort = RouteListSort.OLDEST), null)
        assertEquals(listOf(1L, 2L), ids(medium))
        val veryLong = routes.filterAndSort(RouteListFilters(length = RouteLength.VERY_LONG), null)
        assertEquals(emptyList<Long>(), ids(veryLong))
    }

    @Test
    fun `filtr widocznosci`() {
        val routes = listOf(route(1, isPublic = true), route(2, isPublic = false))
        assertEquals(listOf(1L), ids(routes.filterAndSort(RouteListFilters(visibility = RouteVisibility.PUBLIC), null)))
        assertEquals(listOf(2L), ids(routes.filterAndSort(RouteListFilters(visibility = RouteVisibility.PRIVATE), null)))
    }

    @Test
    fun `tylko turystyczne i widokowe`() {
        val routes = listOf(route(1, tourist = true), route(2, scenic = true), route(3, tourist = true, scenic = true))
        assertEquals(listOf(3L, 1L), ids(routes.filterAndSort(RouteListFilters(touristOnly = true), null)))
        assertEquals(listOf(3L), ids(routes.filterAndSort(RouteListFilters(touristOnly = true, scenicOnly = true), null)))
    }

    @Test
    fun `filtr odleglosci od uzytkownika`() {
        val near = route(1, start = LatLng(51.2500, 22.5700))
        val far = route(2, start = LatLng(52.2297, 21.0122)) // Warszawa
        val noStart = route(3, start = null)
        val result = listOf(near, far, noStart).filterAndSort(RouteListFilters(nearKm = 10), lublin)
        assertEquals(listOf(1L), ids(result))
    }

    @Test
    fun `bez lokalizacji filtr odleglosci jest pomijany`() {
        val routes = listOf(route(1), route(2, start = LatLng(52.2297, 21.0122)))
        assertEquals(2, routes.filterAndSort(RouteListFilters(nearKm = 10), null).size)
    }

    @Test
    fun `sortowanie wedlug dlugosci i daty`() {
        val routes = listOf(route(1, distance = 3_000.0), route(2, distance = 9_000.0), route(3, distance = 6_000.0))
        assertEquals(listOf(2L, 3L, 1L), ids(routes.filterAndSort(RouteListFilters(sort = RouteListSort.LONGEST), null)))
        assertEquals(listOf(1L, 3L, 2L), ids(routes.filterAndSort(RouteListFilters(sort = RouteListSort.SHORTEST), null)))
        assertEquals(listOf(1L, 2L, 3L), ids(routes.filterAndSort(RouteListFilters(sort = RouteListSort.OLDEST), null)))
    }

    @Test
    fun `sortowanie po nazwie uwzglednia polskie litery`() {
        val routes = listOf(route(1, name = "Zalew"), route(2, name = "Łąka"), route(3, name = "Las"))
        val result = routes.filterAndSort(RouteListFilters(sort = RouteListSort.NAME), null)
        assertEquals(listOf("Las", "Łąka", "Zalew"), result.map { it.name })
    }

    @Test
    fun `sortowanie najblizej mnie i powrot do najnowszych bez lokalizacji`() {
        val a = route(1, start = LatLng(51.30, 22.60), day = 1)
        val b = route(2, start = LatLng(51.25, 22.57), day = 2)
        val c = route(3, start = null, day = 3)
        val routes = listOf(a, b, c)
        assertEquals(listOf(2L, 1L, 3L), ids(routes.filterAndSort(RouteListFilters(sort = RouteListSort.NEAREST), lublin)))
        assertEquals(listOf(3L, 2L, 1L), ids(routes.filterAndSort(RouteListFilters(sort = RouteListSort.NEAREST), null)))
    }

    @Test
    fun `plakietka liczy tylko filtry z arkusza`() {
        val filters = RouteListFilters(text = "a", activityTypes = setOf(ActivityType.CYCLING), length = RouteLength.SHORT, touristOnly = true)
        assertEquals(2, filters.sheetFilterCount)
        assertEquals(true, filters.isNarrowed)
        assertEquals(false, RouteListFilters().isNarrowed)
    }
}
