package com.trailify.core.domain.recording

import com.trailify.core.domain.model.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class RecordingStatsTest {

    private val t0 = Instant.parse("2026-11-03T10:00:00Z")

    // 0.0001 stopnia szerokości to ok. 11,1 m
    private fun point(latOffset: Double, seconds: Long) =
        TrackPoint(latitude = 51.0 + latOffset, longitude = 22.0, recordedAt = t0.plusSeconds(seconds))

    @Test
    fun `srednia predkosc`() {
        assertEquals(2.0, averageSpeedMps(1000.0, 500)!!, 0.001)
        assertNull(averageSpeedMps(0.0, 100))
        assertNull(averageSpeedMps(100.0, 0))
    }

    @Test
    fun `tempo z predkosci`() {
        // 3 m/s = 5:33 min/km
        assertEquals(5.555, paceMinPerKm(3.0)!!, 0.01)
        assertNull(paceMinPerKm(0.0))
        assertNull(paceMinPerKm(null))
    }

    @Test
    fun `biezaca predkosc z ostatnich punktow`() {
        // co 10 s po ok. 11,1 m = 1,11 m/s
        val points = (0..6).map { point(it * 0.0001, it * 10L) }
        val speed = currentSpeedMps(points, now = t0.plusSeconds(61))
        assertNotNull(speed)
        assertEquals(1.11, speed!!, 0.05)
    }

    @Test
    fun `po dluzszej ciszy uznajemy ze stoimy`() {
        val points = (0..3).map { point(it * 0.0001, it * 10L) }
        assertEquals(0.0, currentSpeedMps(points, now = t0.plusSeconds(120))!!, 0.0)
    }

    @Test
    fun `za malo punktow to brak predkosci`() {
        assertNull(currentSpeedMps(emptyList(), t0))
        assertNull(currentSpeedMps(listOf(point(0.0, 0)), t0.plusSeconds(1)))
    }
}
