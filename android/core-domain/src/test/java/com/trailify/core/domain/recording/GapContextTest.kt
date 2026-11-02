package com.trailify.core.domain.recording

import com.trailify.core.domain.model.ActivityType
import com.trailify.core.domain.model.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import kotlin.math.cos

class GapContextTest {

    private val t0 = Instant.parse("2026-11-03T10:00:00Z")
    private val metersPerDegree = 111_194.9

    /** Punkt [northMeters] na północ od punktu odniesienia, w danej sekundzie. */
    private fun north(northMeters: Double, seconds: Long, reconstructed: Boolean = false) = TrackPoint(
        latitude = 51.0 + northMeters / metersPerDegree,
        longitude = 22.0,
        recordedAt = t0.plusSeconds(seconds),
        isReconstructed = reconstructed,
    )

    /** Marsz na północ ze stałą prędkością, punkt co 5 s: [count] punktów. */
    private fun walkNorth(count: Int, speed: Double = 1.4) =
        (0 until count).map { north(it * 5 * speed, it * 5L) }

    private fun gapAfter(points: List<TrackPoint>, seconds: Long = 600): GpsGap {
        val from = points.last()
        return GpsGap(from, north(5_000.0, from.recordedAt.epochSecond - t0.epochSecond + seconds))
    }

    @Test
    fun `tempo sprzed luki wynika ze zmierzonych odcinkow`() {
        val points = walkNorth(41)
        val context = GapContext.build(points, gapAfter(points), ActivityType.WALKING)
        assertEquals(1.4, context.priorSpeedMps, 0.02)
    }

    @Test
    fun `tempo pomija punkty odtworzone`() {
        // 20 zmierzonych (1,4 m/s), 10 odtworzonych szybko (5 m/s), 10 zmierzonych (1,4 m/s)
        val first = (0 until 20).map { north(it * 7.0, it * 5L) }
        val reconstructed = (20 until 30).map { north(19 * 7.0 + (it - 19) * 25.0, it * 5L, reconstructed = true) }
        val base = 19 * 7.0 + 10 * 25.0
        val second = (30 until 40).map { north(base + (it - 29) * 7.0, it * 5L) }
        val points = first + reconstructed + second

        val context = GapContext.build(points, gapAfter(points), ActivityType.WALKING)

        assertEquals(1.4, context.priorSpeedMps, 0.02)
    }

    @Test
    fun `za malo danych oznacza typowe tempo dla aktywnosci`() {
        val points = walkNorth(3) // tylko 10 s ruchu
        val context = GapContext.build(points, gapAfter(points), ActivityType.CYCLING)
        assertEquals(defaultSpeedMps(ActivityType.CYCLING), context.priorSpeedMps, 0.0)
    }

    @Test
    fun `postoje z duzymi odstepami w czasie nie sa ruchem`() {
        val points = (0 until 10).map { north(it * 5.0, it * 60L) } // punkt co minutę
        val context = GapContext.build(points, gapAfter(points), ActivityType.RUNNING)
        assertEquals(defaultSpeedMps(ActivityType.RUNNING), context.priorSpeedMps, 0.0)
    }

    @Test
    fun `kierunek ruchu na polnoc to ok 0 stopni`() {
        val points = walkNorth(10)
        val heading = GapContext.build(points, gapAfter(points), ActivityType.WALKING).entryHeadingDegrees
        assertNotNull(heading)
        assertEquals(0.0, heading!!, 1.0)
    }

    @Test
    fun `kierunek ruchu na wschod to ok 90 stopni`() {
        val stepDegrees = 7.0 / (metersPerDegree * cos(Math.toRadians(51.0)))
        val points = (0 until 10).map {
            TrackPoint(latitude = 51.0, longitude = 22.0 + it * stepDegrees, recordedAt = t0.plusSeconds(it * 5L))
        }
        val heading = GapContext.build(points, gapAfter(points), ActivityType.WALKING).entryHeadingDegrees
        assertNotNull(heading)
        assertEquals(90.0, heading!!, 1.0)
    }

    @Test
    fun `bez dosc dlugiego odcinka kierunek jest nieznany`() {
        val points = walkNorth(2) // 7 m
        assertNull(GapContext.build(points, gapAfter(points), ActivityType.WALKING).entryHeadingDegrees)
    }

    @Test
    fun `kontekst przenosi kroki i dlugosc kroku`() {
        val points = walkNorth(10)
        val context = GapContext.build(points, gapAfter(points), ActivityType.WALKING, stepsInGap = 120, strideMeters = 0.7)
        assertEquals(120, context.stepsInGap)
        assertEquals(0.7, context.strideMeters!!, 0.0)
    }

    // --- StrideEstimator ---

    @Test
    fun `dlugosc kroku jest znana dopiero po zebraniu dosc krokow`() {
        val e = StrideEstimator()
        e.update(21.0, 30) // 0,7 m
        assertNull(e.strideMeters)
        e.update(21.0, 30)
        assertEquals(0.7, e.strideMeters!!, 0.001)
    }

    @Test
    fun `nierealne odcinki sa pomijane`() {
        val e = StrideEstimator()
        e.update(2.0, 5) // za krótki dystans
        e.update(100.0, 10) // krok 10 m
        e.update(5.0, 2) // za mało kroków
        e.update(21.0, 30)
        e.update(21.0, 30)
        assertEquals(0.7, e.strideMeters!!, 0.001)
    }
}
