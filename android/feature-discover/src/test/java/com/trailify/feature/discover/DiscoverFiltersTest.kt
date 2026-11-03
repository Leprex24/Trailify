package com.trailify.feature.discover

import com.trailify.core.domain.model.ActivityType
import com.trailify.core.domain.model.RouteSort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DiscoverFiltersTest {

    @Test
    fun domyslneFiltryNieOgraniczajaWynikow() {
        val query = DiscoverFilters().toQuery()
        assertEquals(RouteSort.RECOMMENDED, query.sort) // ranking rekomendacji jako domyślne sortowanie
        assertNull(query.minDistance)
        assertNull(query.maxDistance)
        assertNull(query.minRating)
        assertNull(query.isTourist)
        assertNull(query.text)
        assertEquals(DiscoverFilters.PAGE_SIZE, query.size)
    }

    @Test
    fun przedzialDlugosciZamienianyNaMetry() {
        val query = DiscoverFilters(distance = DistanceRange.MEDIUM).toQuery()
        assertEquals(5_000.0, query.minDistance!!, 0.0)
        assertEquals(15_000.0, query.maxDistance!!, 0.0)
    }

    @Test
    fun wylaczonaFlagaZnaczyObojetnie() {
        // "tylko turystyczne" wyłączone nie może szukać tras NIEturystycznych
        assertNull(DiscoverFilters(touristOnly = false).toQuery().isTourist)
        assertEquals(true, DiscoverFilters(touristOnly = true).toQuery().isTourist)
    }

    @Test
    fun tekstPrzycinanyIPustyPomijany() {
        assertEquals("Zalew", DiscoverFilters(text = "  Zalew ").toQuery().text)
        assertNull(DiscoverFilters(text = "   ").toQuery().text)
        assertEquals(DiscoverFilters.MAX_QUERY_LENGTH, DiscoverFilters(text = "a".repeat(150)).toQuery().text!!.length)
    }

    @Test
    fun licznikFiltrowZArkusza() {
        assertEquals(0, DiscoverFilters(activityTypes = setOf(ActivityType.CYCLING)).sheetFilterCount)
        assertEquals(2, DiscoverFilters(minRating = 4.0, scenicOnly = true).sheetFilterCount)
    }

    @Test
    fun odmianaLiczbyOcen() {
        assertEquals("1 ocena", reviewsLabel(1))
        assertEquals("3 oceny", reviewsLabel(3))
        assertEquals("5 ocen", reviewsLabel(5))
        assertEquals("12 ocen", reviewsLabel(12))
        assertEquals("22 oceny", reviewsLabel(22))
        assertEquals("Brak ocen", ratingText(null, 0))
    }
}
