package com.trailify.core.ui

import com.trailify.core.domain.model.LatLng
import com.trailify.core.domain.model.PoiCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class FeedUiTest {

    private val now = Instant.parse("2026-11-16T12:00:00Z")
    private val zone = ZoneId.of("UTC")

    private fun ago(minutes: Long) = formatTimeAgo(now, now.minusSeconds(minutes * 60), zone)

    @Test
    fun swiezyCzasToPrzedChwila() {
        assertEquals("przed chwilą", ago(0))
        assertEquals("przed chwilą", formatTimeAgo(now, now.plusSeconds(120), zone))
    }

    @Test
    fun minutyGodzinyDni() {
        assertEquals("5 min temu", ago(5))
        assertEquals("59 min temu", ago(59))
        assertEquals("3 godz. temu", ago(3 * 60))
        assertEquals("23 godz. temu", ago(23 * 60 + 59))
        assertEquals("wczoraj", ago(30 * 60))
        assertEquals("4 dni temu", ago(4 * 24 * 60))
    }

    @Test
    fun poTygodniuDataKalendarzowa() {
        assertEquals("1.11.2026", formatTimeAgo(now, Instant.parse("2026-11-01T08:00:00Z"), zone))
    }

    @Test
    fun kazdaKategoriaMaNazwe() {
        PoiCategory.entries.forEach { assertTrue(it.label().isNotBlank()) }
        assertEquals("Punkt widokowy", PoiCategory.VIEWPOINT.label())
    }

    @Test
    fun obrysMiesciSieWKwadracieIZachowujeProporcje() {
        // trasa 4 razy szersza niż wysoka (przy szerokości 0 skrócenie długości jest równe 1)
        val points = outlineUnitPoints(listOf(LatLng(0.0, 0.0), LatLng(0.5, 1.0), LatLng(0.0, 2.0)))
        assertEquals(3, points.size)
        points.forEach { (x, y) ->
            assertTrue(x in 0f..1f)
            assertTrue(y in 0f..1f)
        }
        // szerokość wypełnia kwadrat, a wysokość to jego ćwierć, wyśrodkowana
        assertEquals(0f, points[0].first, 1e-3f)
        assertEquals(1f, points[2].first, 1e-3f)
        assertEquals(0.625f, points[0].second, 1e-3f) // południe jest niżej
        assertEquals(0.375f, points[1].second, 1e-3f) // północ jest u góry
    }

    @Test
    fun obrysBezTrasyJestPusty() {
        assertTrue(outlineUnitPoints(emptyList()).isEmpty())
        assertTrue(outlineUnitPoints(listOf(LatLng(1.0, 1.0))).isEmpty())
        assertTrue(outlineUnitPoints(listOf(LatLng(1.0, 1.0), LatLng(1.0, 1.0))).isEmpty())
    }
}
