package com.trailify.core.domain.recording

import com.trailify.core.domain.model.LatLng
import com.trailify.core.domain.model.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import java.time.Instant
import org.junit.Test

class TrackRecorderTest {

    private val t0 = Instant.parse("2026-11-03T10:00:00Z")

    // 0.0001 stopnia szerokości to ok. 11,1 m
    private fun point(latOffset: Double, seconds: Long) =
        TrackPoint(latitude = 51.0 + latOffset, longitude = 22.0, recordedAt = t0.plusSeconds(seconds))

    @Test
    fun `odleglosc haversine jest rozsadna`() {
        val d = distanceMeters(LatLng(51.0, 22.0), LatLng(51.001, 22.0))
        assertEquals(111.2, d, 0.5)
    }

    @Test
    fun `pierwszy punkt jest zawsze zapisany`() {
        val r = TrackRecorder()
        assertTrue(r.add(point(0.0, 0)))
        assertEquals(0.0, r.distanceMeters, 0.0)
        assertEquals(1, r.size)
    }

    @Test
    fun `dystans sie sumuje`() {
        val r = TrackRecorder()
        r.add(point(0.0, 0))
        r.add(point(0.0010, 60))
        r.add(point(0.0020, 120))
        assertEquals(222.4, r.distanceMeters, 1.0)
        assertEquals(3, r.size)
    }

    @Test
    fun `drgania w miejscu sa pomijane`() {
        val r = TrackRecorder()
        r.add(point(0.0, 0))
        assertFalse(r.add(point(0.00001, 5))) // ok. 1 m
        assertEquals(1, r.size)
    }

    @Test
    fun `zla dokladnosc odrzuca punkt`() {
        val r = TrackRecorder()
        assertFalse(r.add(point(0.0, 0), accuracyMeters = 80f))
        assertEquals(0, r.size)
        assertTrue(r.add(point(0.0, 1), accuracyMeters = 10f))
    }

    @Test
    fun `nierealny skok jest odrzucany`() {
        val r = TrackRecorder()
        r.add(point(0.0, 0))
        assertFalse(r.add(point(0.05, 1))) // 5,5 km w sekunde
        assertEquals(1, r.size)
    }

    @Test
    fun `po przerwie nie liczymy dystansu przez luke`() {
        val r = TrackRecorder()
        r.add(point(0.0, 0))
        r.add(point(0.0010, 60))
        val before = r.distanceMeters
        r.breakSegment()
        assertTrue(r.add(point(0.0100, 3600)))
        assertEquals(before, r.distanceMeters, 0.001)
        assertEquals(3, r.size)
    }

    @Test
    fun `przywrocone nagranie liczy dalej od zapisanego dystansu`() {
        val r = TrackRecorder()
        r.restore(listOf(point(0.0, 0), point(0.0010, 60)), distanceMeters = 111.2)
        assertEquals(2, r.size)
        assertEquals(111.2, r.distanceMeters, 0.001)
        // pierwszy punkt po przywróceniu nie dolicza skoku od ostatniego zapisanego
        assertTrue(r.add(point(0.0500, 7200)))
        assertEquals(111.2, r.distanceMeters, 0.001)
        assertTrue(r.add(point(0.0510, 7260)))
        assertEquals(222.4, r.distanceMeters, 1.0)
    }
}
