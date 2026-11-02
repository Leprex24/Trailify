package com.trailify.core.domain.recording

import com.trailify.core.domain.model.ActivityType
import com.trailify.core.domain.model.AppException
import com.trailify.core.domain.model.LatLng
import com.trailify.core.domain.model.PlannedRoute
import com.trailify.core.domain.model.TrackPoint
import com.trailify.core.domain.repository.RoutePlanner
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class GapReconstructorTest {

    private val t0 = Instant.parse("2026-11-03T10:00:00Z")

    /** Atrapa planera: zwraca zadaną trasę albo rzuca zadany błąd. */
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

    // A i B oddalone o ok. 111 m, 60 s przerwy (1,85 m/s: normalny marsz)
    private val a = point(51.0, 22.0, 0)
    private val b = point(51.001, 22.0, 60)
    private val gap = GpsGap(a, b)

    private fun route(vararg path: LatLng) =
        PlannedRoute(distance = pathLengthMeters(path.toList()), durationSeconds = 60, geometry = path.toList())

    // --- isReconstructable ---

    @Test
    fun `zwykla luka podczas marszu jest odtwarzana`() {
        assertTrue(GapReconstructor(FakePlanner()).isReconstructable(gap, ActivityType.WALKING))
    }

    @Test
    fun `zbyt dluga przerwa nie jest odtwarzana`() {
        val long = GpsGap(a, point(51.001, 22.0, 25 * 60))
        assertFalse(GapReconstructor(FakePlanner()).isReconstructable(long, ActivityType.WALKING))
    }

    @Test
    fun `nierealna dla marszu predkosc oznacza brak odtwarzania, ale dla roweru jest ok`() {
        val fast = GpsGap(a, point(51.001, 22.0, 30)) // 111 m w 30 s = 3,7 m/s
        val reconstructor = GapReconstructor(FakePlanner())
        assertFalse(reconstructor.isReconstructable(fast, ActivityType.WALKING))
        assertTrue(reconstructor.isReconstructable(fast, ActivityType.RUNNING))
        assertTrue(reconstructor.isReconstructable(fast, ActivityType.CYCLING))
    }

    // --- linia prosta ---

    @Test
    fun `linia prosta daje punkty odtworzone z rosnacym czasem miedzy koncami luki`() {
        val points = GapReconstructor(FakePlanner()).straightLine(gap)

        assertEquals(5, points.size) // 111 m / 20 m = 6 odcinków
        assertTrue(points.all { it.isReconstructed })
        val times = points.map { it.recordedAt }
        assertTrue(times.zipWithNext().all { (x, y) -> x.isBefore(y) })
        assertTrue(times.first().isAfter(a.recordedAt))
        assertTrue(times.last().isBefore(b.recordedAt))
        // stałe tempo: punkty leżą na linii prostej, długość całej trasy zgadza się z odległością A-B
        val full = listOf(a) + points + b
        assertEquals(gap.straightMeters, trackLengthMeters(full), 0.5)
    }

    @Test
    fun `czas jest proporcjonalny do przebytej drogi`() {
        val points = GapReconstructor(FakePlanner()).straightLine(gap)
        val middle = points[points.size / 2] // środkowy z 5 punktów = połowa drogi (3. z 6 odcinków)
        assertEquals(30.0, (middle.recordedAt.toEpochMilli() - a.recordedAt.toEpochMilli()) / 1000.0, 1.0)
        assertEquals(gap.straightMeters / 60.0, middle.speed!!, 0.01)
    }

    // --- trasa po drogach ---

    @Test
    fun `trasa po drogach prowadzi przez wierzcholki wyznaczonej trasy`() {
        val corner = LatLng(51.0005, 22.0008) // ok. 56 m na wschód od linii prostej
        val planner = FakePlanner(route = route(LatLng(51.0, 22.0), corner, LatLng(51.001, 22.0)))

        val points = runBlocking { GapReconstructor(planner).byRouting(gap, ActivityType.WALKING) }

        assertNotNull(points)
        assertEquals(1, planner.calls)
        assertTrue(points!!.all { it.isReconstructed })
        assertTrue(points.any { distanceMeters(LatLng(it.latitude, it.longitude), corner) < 1.0 })
        // trasa jest dłuższa niż linia prosta (obchodzi dookoła)
        assertTrue(trackLengthMeters(listOf(a) + points + b) > gap.straightMeters + 30)
    }

    @Test
    fun `blad planera oznacza brak trasy po drogach`() {
        val planner = FakePlanner(failure = RuntimeException("brak internetu"))
        assertNull(runBlocking { GapReconstructor(planner).byRouting(gap, ActivityType.WALKING) })
    }

    @Test
    fun `pusta geometria oznacza brak trasy po drogach`() {
        val planner = FakePlanner(route = PlannedRoute(0.0, 0, emptyList()))
        assertNull(runBlocking { GapReconstructor(planner).byRouting(gap, ActivityType.WALKING) })
    }

    @Test
    fun `zbyt dlugi objazd jest odrzucany`() {
        // 1 km objazdu przy 111 m linii prostej
        val planner = FakePlanner(route = route(LatLng(51.0, 22.0), LatLng(51.0005, 22.0070), LatLng(51.001, 22.0)))
        assertNull(runBlocking { GapReconstructor(planner).byRouting(gap, ActivityType.WALKING) })
    }

    @Test
    fun `trasa wymagajaca nierealnej predkosci jest odrzucana`() {
        // 158 m w 30 s = 5,3 m/s: za szybko na marsz, w sam raz na bieg
        val shortGap = GpsGap(a, point(51.001, 22.0, 30))
        val corner = LatLng(51.0005, 22.0008)
        val planner = FakePlanner(route = route(LatLng(51.0, 22.0), corner, LatLng(51.001, 22.0)))

        assertNull(runBlocking { GapReconstructor(planner).byRouting(shortGap, ActivityType.WALKING) })
        assertNotNull(runBlocking { GapReconstructor(planner).byRouting(shortGap, ActivityType.RUNNING) })
    }

    // --- pomocnicze ---

    @Test
    fun `reconstructedMeters liczy tylko odcinki dotykajace punktow odtworzonych`() {
        val m1 = TrackPoint(51.0005, 22.0, recordedAt = t0.plusSeconds(30), isReconstructed = true)
        val points = listOf(a, m1, b, point(51.002, 22.0, 120))
        // A-M1 i M1-B to ok. 55,6 m każdy; B-C jest zmierzone
        assertEquals(111.2, reconstructedMeters(points), 0.5)
    }

    @Test
    fun `AppException z planera daje brak trasy, a nie wyjatek`() {
        val planner = FakePlanner(failure = AppException("ROUTING_UNAVAILABLE", "Silnik tras jest niedostępny"))
        assertNull(runBlocking { GapReconstructor(planner).byRouting(gap, ActivityType.CYCLING) })
    }
}
