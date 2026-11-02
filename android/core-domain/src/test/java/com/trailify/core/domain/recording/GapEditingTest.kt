package com.trailify.core.domain.recording

import com.trailify.core.domain.model.ActivityType
import com.trailify.core.domain.model.LatLng
import com.trailify.core.domain.model.PlannedRoute
import com.trailify.core.domain.model.TrackPoint
import com.trailify.core.domain.repository.RoutePlanner
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class GapEditingTest {

    private val t0 = Instant.parse("2026-11-03T10:00:00Z")

    private class FakePlanner(var route: PlannedRoute? = null, var failure: Exception? = null) : RoutePlanner {
        var calls = 0
        override suspend fun plan(type: ActivityType, waypoints: List<LatLng>): PlannedRoute {
            calls++
            failure?.let { throw it }
            return route ?: throw IllegalStateException("brak trasy w atrapie")
        }
    }

    private fun point(lat: Double, lng: Double, seconds: Long) =
        TrackPoint(latitude = lat, longitude = lng, recordedAt = t0.plusSeconds(seconds))

    // A i B oddalone o ok. 111 m, 60 s przerwy
    private val a = point(51.0, 22.0, 0)
    private val b = point(51.001, 22.0, 60)
    private val gap = GpsGap(a, b)

    // punkt ok. 56 m na wschód od linii prostej: trasa A-punkt-B ma ok. 158 m (2,6 m/s przy 60 s)
    private val corner = LatLng(51.0005, 22.0008)

    private fun routeVia(vararg path: LatLng) =
        PlannedRoute(distance = pathLengthMeters(path.toList()), durationSeconds = 60, geometry = path.toList())

    private val cornerRoute get() = routeVia(LatLng(51.0, 22.0), corner, LatLng(51.001, 22.0))

    // --- straightCandidate ---

    @Test
    fun `linia prosta jako propozycja ma dlugosc odleglosci A-B i pelne prawdopodobienstwo`() {
        val candidate = GapReconstructor(FakePlanner()).straightCandidate(gap)

        assertEquals(CandidateKind.STRAIGHT, candidate.kind)
        assertEquals(1.0, candidate.probability, 1e-9)
        assertEquals(gap.straightMeters, candidate.lengthMeters, 1e-6)
        assertTrue(candidate.points.isNotEmpty())
        assertTrue(candidate.points.all { it.isReconstructed })
    }

    // --- manual ---

    @Test
    fun `reczna trasa przez punkt jest przyjmowana, gdy tempo jest realne`() {
        val planner = FakePlanner(route = cornerRoute)

        val result = runBlocking { GapReconstructor(planner).manual(gap, ActivityType.WALKING, listOf(corner), 1.4) }

        assertEquals(ManualGapResult.APPLIED, result.result)
        assertEquals(1, planner.calls)
        assertTrue(result.points.isNotEmpty())
        assertTrue(result.points.all { it.isReconstructed })
        assertTrue(result.points.any { distanceMeters(LatLng(it.latitude, it.longitude), corner) < 1.0 })
        assertTrue(trackLengthMeters(listOf(a) + result.points + b) > gap.straightMeters + 30)
    }

    @Test
    fun `reczna trasa wymagajaca za duzego tempa jest odrzucana`() {
        val planner = FakePlanner(route = cornerRoute)

        // tempo sprzed luki 1,0 m/s: dopuszczone ok. 2,2 m/s, a trasa wymaga ok. 2,6 m/s
        val result = runBlocking { GapReconstructor(planner).manual(gap, ActivityType.WALKING, listOf(corner), 1.0) }

        assertEquals(ManualGapResult.TOO_FAST, result.result)
        assertTrue(result.points.isEmpty())
    }

    @Test
    fun `bledna liczba punktow nie wywoluje planera`() {
        val planner = FakePlanner(route = cornerRoute)
        val reconstructor = GapReconstructor(planner)

        val none = runBlocking { reconstructor.manual(gap, ActivityType.WALKING, emptyList(), 1.4) }
        val tooMany = runBlocking {
            reconstructor.manual(gap, ActivityType.WALKING, List(GapRules.MAX_MANUAL_POINTS + 1) { corner }, 1.4)
        }

        assertEquals(ManualGapResult.BAD_POINT_COUNT, none.result)
        assertEquals(ManualGapResult.BAD_POINT_COUNT, tooMany.result)
        assertEquals(0, planner.calls)
    }

    @Test
    fun `blad planera daje wynik NO_ROUTE zamiast wyjatku`() {
        val planner = FakePlanner(failure = RuntimeException("brak internetu"))

        val result = runBlocking { GapReconstructor(planner).manual(gap, ActivityType.WALKING, listOf(corner), 1.4) }

        assertEquals(ManualGapResult.NO_ROUTE, result.result)
    }

    @Test
    fun `pusta geometria trasy daje NO_ROUTE`() {
        val planner = FakePlanner(route = PlannedRoute(0.0, 0, emptyList()))

        val result = runBlocking { GapReconstructor(planner).manual(gap, ActivityType.WALKING, listOf(corner), 1.4) }

        assertEquals(ManualGapResult.NO_ROUTE, result.result)
    }

    @Test
    fun `rower dopuszcza dluzsza trase w tym samym czasie`() {
        val planner = FakePlanner(route = cornerRoute)

        val result = runBlocking { GapReconstructor(planner).manual(gap, ActivityType.CYCLING, listOf(corner), 4.5) }

        assertEquals(ManualGapResult.APPLIED, result.result)
    }

    // --- GapInfo ---

    private fun candidate(kind: CandidateKind, vararg seconds: Long) = GapCandidate(
        kind = kind,
        points = seconds.map { TrackPoint(51.0005, 22.0, recordedAt = t0.plusSeconds(it), isReconstructed = true) },
        lengthMeters = 111.0,
        score = 0.0,
        probability = 0.5,
    )

    @Test
    fun `currentPoints zwraca punkty wybranej propozycji`() {
        val first = candidate(CandidateKind.SHORTEST_ROUTE, 20, 40)
        val second = candidate(CandidateKind.STRAIGHT, 30)
        val info = GapInfo(id = 1, gap = gap, candidates = listOf(first, second), selected = 1, priorSpeedMps = 1.4)

        assertFalse(info.isManual)
        assertEquals(second.points, info.currentPoints)
    }

    @Test
    fun `currentPoints zwraca punkty reczne, gdy nie wybrano zadnej propozycji`() {
        val manual = candidate(CandidateKind.DETOUR, 15, 30, 45).points
        val info = GapInfo(
            id = 2,
            gap = gap,
            candidates = listOf(candidate(CandidateKind.STRAIGHT, 30)),
            selected = null,
            manualPoints = manual,
            priorSpeedMps = 1.4,
        )

        assertTrue(info.isManual)
        assertEquals(manual, info.currentPoints)
    }
}
