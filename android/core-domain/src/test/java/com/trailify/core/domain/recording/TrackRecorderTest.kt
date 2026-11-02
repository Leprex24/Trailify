package com.trailify.core.domain.recording

import com.trailify.core.domain.model.LatLng
import com.trailify.core.domain.model.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    // --- luki w sygnale GPS ---

    @Test
    fun `po dlugiej przerwie z przesunieciem wykrywa luke`() {
        val r = TrackRecorder()
        val a = point(0.0, 0)
        val b = point(0.0010, 60) // 111 m po 60 s
        r.add(a)
        assertNull(r.takeGap())
        r.add(b)
        val gap = r.takeGap()
        assertEquals(GpsGap(a, b), gap)
        assertNull(r.takeGap()) // luka jest zgłaszana tylko raz
    }

    @Test
    fun `zwykle punkty co kilka sekund nie sa luka`() {
        val r = TrackRecorder()
        r.add(point(0.0, 0))
        r.add(point(0.0010, 10)) // daleko, ale po 10 s
        assertNull(r.takeGap())
    }

    @Test
    fun `postoj bez przesuniecia nie jest luka`() {
        val r = TrackRecorder()
        r.add(point(0.0, 0))
        assertTrue(r.add(point(0.00004, 300))) // ok. 4,4 m po 5 minutach
        assertNull(r.takeGap())
    }

    @Test
    fun `po pauzie nie ma luki`() {
        val r = TrackRecorder()
        r.add(point(0.0, 0))
        r.breakSegment()
        r.add(point(0.0010, 600))
        assertNull(r.takeGap())
    }

    @Test
    fun `wstawienie odtworzonej trasy poprawia dystans i punkty`() {
        val r = TrackRecorder()
        val a = point(0.0, 0)
        val b = point(0.0010, 60)
        r.add(a)
        r.add(b)
        val gap = r.takeGap()!!
        val detour = TrackPoint(51.0005, 22.0010, recordedAt = t0.plusSeconds(30), isReconstructed = true)

        assertTrue(r.replaceGap(gap, listOf(detour)))

        assertEquals(listOf(a, detour, b), r.snapshot())
        val expected = distanceMeters(LatLng(51.0, 22.0), LatLng(51.0005, 22.0010)) +
            distanceMeters(LatLng(51.0005, 22.0010), LatLng(51.0010, 22.0))
        assertEquals(expected, r.distanceMeters, 0.01)
    }

    @Test
    fun `ponowne wstawienie zastepuje poprzednie odtworzone punkty`() {
        val r = TrackRecorder()
        val a = point(0.0, 0)
        val b = point(0.0010, 60)
        r.add(a)
        r.add(b)
        val gap = r.takeGap()!!
        val first = TrackPoint(51.0005, 22.0, recordedAt = t0.plusSeconds(30), isReconstructed = true)
        val second = TrackPoint(51.0005, 22.0010, recordedAt = t0.plusSeconds(30), isReconstructed = true)

        assertTrue(r.replaceGap(gap, listOf(first)))
        assertTrue(r.replaceGap(gap, listOf(second)))

        assertEquals(listOf(a, second, b), r.snapshot())
        val expected = distanceMeters(LatLng(51.0, 22.0), LatLng(51.0005, 22.0010)) +
            distanceMeters(LatLng(51.0005, 22.0010), LatLng(51.0010, 22.0))
        assertEquals(expected, r.distanceMeters, 0.01)
    }

    @Test
    fun `wstawienie do luki, ktorej nie ma w nagraniu, nic nie zmienia`() {
        val r = TrackRecorder()
        r.add(point(0.0, 0))
        r.add(point(0.0010, 60))
        val before = r.distanceMeters
        val unknown = GpsGap(point(0.5, 0), point(0.6, 60))

        assertFalse(r.replaceGap(unknown, listOf(point(0.55, 30))))

        assertEquals(2, r.size)
        assertEquals(before, r.distanceMeters, 0.0)
    }

    @Test
    fun `po wstawieniu trasy dystans liczy sie dalej`() {
        val r = TrackRecorder()
        r.add(point(0.0, 0))
        r.add(point(0.0010, 60))
        val gap = r.takeGap()!!
        r.replaceGap(gap, emptyList())
        val before = r.distanceMeters

        assertTrue(r.add(point(0.0020, 120)))

        assertEquals(before + 111.2, r.distanceMeters, 1.0)
        assertEquals(3, r.size)
    }
}
