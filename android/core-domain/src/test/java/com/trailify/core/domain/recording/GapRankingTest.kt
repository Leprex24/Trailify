package com.trailify.core.domain.recording

import com.trailify.core.domain.model.ActivityType
import com.trailify.core.domain.model.LatLng
import com.trailify.core.domain.model.PlannedRoute
import com.trailify.core.domain.model.TrackPoint
import com.trailify.core.domain.repository.RoutePlanner
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class GapRankingTest {

    private val t0 = Instant.parse("2026-11-03T10:00:00Z")

    /** Planer łączy punkty prostymi liniami (jak atrapa bez backendu), więc długość trasy jest znana z góry. */
    private class EchoPlanner : RoutePlanner {
        var calls = 0
        override suspend fun plan(type: ActivityType, waypoints: List<LatLng>): PlannedRoute {
            calls++
            return PlannedRoute(pathLengthMeters(waypoints), 0, waypoints)
        }
    }

    private class FailingPlanner : RoutePlanner {
        override suspend fun plan(type: ActivityType, waypoints: List<LatLng>): PlannedRoute =
            throw RuntimeException("brak internetu")
    }

    private fun point(lat: Double, seconds: Long) =
        TrackPoint(latitude = lat, longitude = 22.0, recordedAt = t0.plusSeconds(seconds))

    private val a = point(51.0, 0)

    // B jest ok. 111 m na północ od A
    private fun gap(seconds: Long) = GpsGap(a, point(51.001, seconds))

    private val walkingNorth = GapContext(priorSpeedMps = 1.4, entryHeadingDegrees = 0.0)

    // --- rank ---

    @Test
    fun `dluga przerwa przy marszu daje objazd dopasowany do tempa, a nie najkrotsza droge`() {
        // 10 minut marszu 1,4 m/s = ok. 840 m, a A i B dzieli 111 m
        val ranked = runBlocking {
            GapReconstructor(EchoPlanner()).rank(gap(600), ActivityType.WALKING, walkingNorth)
        }

        val best = ranked.first()
        assertEquals(CandidateKind.DETOUR, best.kind)
        assertTrue("długość ${best.lengthMeters}", best.lengthMeters in 600.0..750.0)
        // najkrótsza droga (111 m) oznaczałaby 0,2 m/s: wyraźnie gorsza
        val shortest = ranked.first { it.kind == CandidateKind.SHORTEST_ROUTE }
        assertTrue(shortest.probability < 0.01)
        assertTrue(best.probability > 0.2)
    }

    @Test
    fun `przy remisie dlugosci wygrywa objazd, ktory nie zawraca`() {
        val ranked = runBlocking {
            GapReconstructor(EchoPlanner()).rank(gap(600), ActivityType.WALKING, walkingNorth)
        }
        // użytkownik szedł na północ: wersja wychodząca dalej na północ (za B) jest lepsza niż ta zawracająca na południe
        val scores = ranked.filter { it.kind == CandidateKind.DETOUR }.map { it.score }
        assertEquals(4, scores.size)
        assertEquals(scores.max(), ranked.first().score, 0.0)
        assertTrue(scores.max() - scores.min() > 0.5)
    }

    @Test
    fun `punkty najlepszej propozycji sa odtworzone i leza w czasie luki`() {
        val g = gap(600)
        val best = runBlocking { GapReconstructor(EchoPlanner()).rank(g, ActivityType.WALKING, walkingNorth) }.first()

        assertTrue(best.points.isNotEmpty())
        assertTrue(best.points.all { it.isReconstructed })
        assertTrue(best.points.all { it.recordedAt.isAfter(g.from.recordedAt) && it.recordedAt.isBefore(g.to.recordedAt) })
        // tempo na odtworzonym odcinku ok. 672 m / 600 s, czyli bliskie temu sprzed luki (1,4 m/s) w granicach tolerancji
        assertEquals(best.lengthMeters / 600.0, best.points.first().speed!!, 0.01)
        assertTrue(best.points.first().speed!! in 1.0..1.7)
    }

    @Test
    fun `prawdopodobienstwa sumuja sie do jednego, a lista jest posortowana`() {
        val ranked = runBlocking {
            GapReconstructor(EchoPlanner()).rank(gap(600), ActivityType.WALKING, walkingNorth)
        }
        assertEquals(1.0, ranked.sumOf { it.probability }, 1e-9)
        assertEquals(ranked.sortedByDescending { it.score }, ranked)
    }

    @Test
    fun `gdy dystans pasuje do najkrotszej drogi nie szukamy objazdow`() {
        val planner = EchoPlanner()
        // 80 s marszu = 112 m, tyle samo co linia prosta
        val ranked = runBlocking { GapReconstructor(planner).rank(gap(80), ActivityType.WALKING, walkingNorth) }

        assertEquals(1, planner.calls)
        assertEquals(1, ranked.size) // linia prosta i najkrótsza droga mają tę samą geometrię
        assertEquals(CandidateKind.SHORTEST_ROUTE, ranked.single().kind)
        assertEquals(1.0, ranked.single().probability, 1e-9)
    }

    @Test
    fun `bez dzialajacego routingu zostaje linia prosta`() {
        val ranked = runBlocking {
            GapReconstructor(FailingPlanner()).rank(gap(600), ActivityType.WALKING, walkingNorth)
        }
        assertEquals(1, ranked.size)
        assertEquals(CandidateKind.STRAIGHT, ranked.single().kind)
        assertEquals(1.0, ranked.single().probability, 1e-9)
        assertTrue(ranked.single().points.all { it.isReconstructed })
    }

    // --- oczekiwany dystans ---

    @Test
    fun `oczekiwany dystans z tempa`() {
        val e = expectedMeters(gap(600), ActivityType.WALKING, walkingNorth, GapWeights())
        assertEquals(840.0, e, 0.01)
    }

    @Test
    fun `kroki skracaja oczekiwany dystans, gdy ktos prawie stal`() {
        val context = walkingNorth.copy(stepsInGap = 300, strideMeters = 0.5) // 150 m z kroków
        val e = expectedMeters(gap(600), ActivityType.WALKING, context, GapWeights())
        assertEquals(0.6 * 150 + 0.4 * 840, e, 0.01)
    }

    @Test
    fun `dla roweru kroki sie nie licza`() {
        val context = walkingNorth.copy(stepsInGap = 300, strideMeters = 0.5)
        val e = expectedMeters(gap(600), ActivityType.CYCLING, context, GapWeights())
        assertEquals(840.0, e, 0.01)
    }

    @Test
    fun `oczekiwany dystans nie jest krotszy niz linia prosta`() {
        val context = GapContext(priorSpeedMps = 0.3, stepsInGap = 0, strideMeters = 0.7)
        val g = gap(600)
        assertEquals(g.straightMeters, expectedMeters(g, ActivityType.WALKING, context, GapWeights()), 0.01)
    }

    // --- geometria ---

    @Test
    fun `punkty objazdu powstaja tylko gdy oczekiwany dystans jest wiekszy niz linia prosta`() {
        val from = LatLng(51.0, 22.0)
        val to = LatLng(51.001, 22.0)
        assertTrue(detourViaPoints(from, to, 100.0, 1.25).isEmpty())
        val vias = detourViaPoints(from, to, 840.0, 1.25)
        assertEquals(4, vias.size)
        // każdy objazd w linii prostej ma mieć ok. 840 / 1,25 = 672 m
        val perpendicular = vias[0]
        assertEquals(672.0, distanceMeters(from, perpendicular) + distanceMeters(perpendicular, to), 2.0)
        val beyond = vias[2]
        assertEquals(672.0, distanceMeters(from, beyond) + distanceMeters(beyond, to), 2.0)
    }

    @Test
    fun `kierunki i roznica katow`() {
        assertEquals(0.0, bearingDegrees(LatLng(51.0, 22.0), LatLng(51.001, 22.0)), 0.5)
        assertEquals(90.0, bearingDegrees(LatLng(51.0, 22.0), LatLng(51.0, 22.001)), 0.5)
        assertEquals(20.0, angleDifferenceDegrees(350.0, 10.0), 1e-9)
        assertEquals(180.0, angleDifferenceDegrees(0.0, 180.0), 1e-9)
        assertEquals(180.0, angleDifferenceDegrees(90.0, 270.0), 1e-9)
    }

    @Test
    fun `softmax daje prawdopodobienstwa`() {
        assertTrue(softmax(emptyList()).isEmpty())
        val p = softmax(listOf(0.0, 0.0, -50.0))
        assertEquals(1.0, p.sum(), 1e-9)
        assertEquals(0.5, p[0], 1e-6)
    }
}
