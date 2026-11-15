package com.trailify.core.domain.poi

import com.trailify.core.domain.model.LatLng
import com.trailify.core.domain.model.Poi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyPoisTest {

    private val here = LatLng(51.2477, 22.5675) // Brama Krakowska
    private val gate = Poi(1, "Brama Krakowska", null, 51.2477, 22.5675, null)
    private val tower = Poi(2, "Wieża Trynitarska", null, 51.2471, 22.5658, null) // ok. 130 m
    private val castle = Poi(3, "Zamek", null, 51.2492, 22.5815, null) // ok. 1 km

    @Test
    fun punktySortowaneOdNajblizszego() {
        assertEquals(listOf(1L, 2L, 3L), nearbyPois(here, listOf(castle, tower, gate)).map { it.poi.id })
    }

    @Test
    fun wZasieguTylkoDo50Metrow() {
        val result = nearbyPois(here, listOf(gate, tower)).associateBy { it.poi.id }
        assertTrue(result.getValue(1).inRange)
        assertFalse(result.getValue(2).inRange)
        assertTrue(result.getValue(2).meters in 100.0..160.0)
    }

    @Test
    fun doZebraniaTylkoNiezebraneWZasiegu() {
        assertEquals(listOf(gate), poisToCollect(here, listOf(gate, tower, castle), collectedIds = emptySet()))
        assertTrue(poisToCollect(here, listOf(gate, tower), collectedIds = setOf(1L)).isEmpty())
    }
}
