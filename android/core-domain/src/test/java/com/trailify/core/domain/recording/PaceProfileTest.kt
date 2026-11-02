package com.trailify.core.domain.recording

import com.trailify.core.domain.model.ActivityType
import com.trailify.core.domain.model.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class PaceProfileTest {

    private val t0 = Instant.parse("2026-11-03T10:00:00Z")
    private val metersPerDegree = 111_194.9

    private fun north(northMeters: Double, seconds: Long) = TrackPoint(
        latitude = 51.0 + northMeters / metersPerDegree,
        longitude = 22.0,
        recordedAt = t0.plusSeconds(seconds),
    )

    private fun walkNorth(count: Int, speed: Double) = (0 until count).map { north(it * 5 * speed, it * 5L) }

    private fun gapAfter(points: List<TrackPoint>) =
        GpsGap(points.last(), north(5_000.0, points.last().recordedAt.epochSecond - t0.epochSecond + 600))

    // --- PaceProfile ---

    @Test
    fun `profil jest nieznany, dopoki za malo ruchu`() {
        val profile = PaceProfile()
        repeat(10) { profile.update(ActivityType.WALKING, 7.0, 5.0) } // 50 s
        assertNull(profile.speedMps)
    }

    @Test
    fun `profil to srednia predkosc w ruchu`() {
        val profile = PaceProfile()
        repeat(30) { profile.update(ActivityType.WALKING, 7.0, 5.0) } // 150 s, 1,4 m/s
        assertEquals(1.4, profile.speedMps!!, 1e-9)
    }

    @Test
    fun `postoje, skoki GPS i dlugie przerwy nie wchodza do profilu`() {
        val profile = PaceProfile()
        repeat(30) { profile.update(ActivityType.WALKING, 7.0, 5.0) }
        profile.update(ActivityType.WALKING, 1.0, 5.0) // 0,2 m/s: postój
        profile.update(ActivityType.WALKING, 100.0, 5.0) // 20 m/s: skok GPS
        profile.update(ActivityType.WALKING, 70.0, 60.0) // przerwa w sygnale
        profile.update(ActivityType.WALKING, 7.0, 0.0)
        assertEquals(150.0, profile.seconds, 1e-9)
        assertEquals(1.4, profile.speedMps!!, 1e-9)
    }

    @Test
    fun `starsze dane traca na wadze po przekroczeniu limitu`() {
        val profile = PaceProfile()
        repeat(600) { profile.update(ActivityType.WALKING, 5.0, 5.0) } // 3000 s po 1,0 m/s
        repeat(600) { profile.update(ActivityType.WALKING, 10.0, 5.0) } // 3000 s po 2,0 m/s
        // po przekroczeniu limitu stare tempo jest ważone coraz mniej, więc wynik jest bliżej nowego
        assertNotNull(profile.speedMps)
        assertEquals(true, profile.speedMps!! > 1.5)
        assertEquals(true, profile.seconds <= GapRules.PROFILE_MAX_SECONDS)
    }

    // --- GapContext z profilem ---

    @Test
    fun `bez swiezych danych tempo wynika z profilu`() {
        val points = walkNorth(3, 1.4) // tylko 10 s ruchu
        val context = GapContext.build(points, gapAfter(points), ActivityType.WALKING, profileSpeedMps = 1.8)
        assertEquals(1.8, context.priorSpeedMps, 1e-9)
    }

    @Test
    fun `swieze dane i profil sa laczone wedlug ilosci swiezych danych`() {
        val points = walkNorth(13, 1.0) // 60 s ruchu po 1,0 m/s, waga swiezych danych 50%
        val context = GapContext.build(points, gapAfter(points), ActivityType.WALKING, profileSpeedMps = 2.0)
        assertEquals(1.5, context.priorSpeedMps, 0.02)
    }

    @Test
    fun `duzo swiezych danych przewaza nad profilem`() {
        val points = walkNorth(37, 1.0) // 180 s ruchu po 1,0 m/s, waga swiezych 75%
        val context = GapContext.build(points, gapAfter(points), ActivityType.WALKING, profileSpeedMps = 2.0)
        assertEquals(1.25, context.priorSpeedMps, 0.02)
    }

    @Test
    fun `bez profilu tempo wynika tylko ze swiezych danych`() {
        val points = walkNorth(41, 1.4)
        val context = GapContext.build(points, gapAfter(points), ActivityType.WALKING, profileSpeedMps = null)
        assertEquals(1.4, context.priorSpeedMps, 0.02)
    }
}
