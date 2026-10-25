package com.trailify.core.domain.navigation

import com.trailify.core.domain.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouteProgressTest {
    // ok. 1112 m na północ
    private val north = listOf(LatLng(51.0, 22.0), LatLng(51.01, 22.0))

    @Test
    fun `position on the route gives half way`() {
        val p = routeProgress(north, LatLng(51.005, 22.0))!!
        assertEquals(556.0, p.alongMeters, 3.0)
        assertEquals(0.0, p.offRouteMeters, 1.0)
        assertEquals(1112.0, p.totalMeters, 3.0)
        assertEquals(556.0, p.remainingMeters, 3.0)
    }

    @Test
    fun `position beside the route is off route by its distance`() {
        // ok. 100 m na wschód od środka trasy
        val p = routeProgress(north, LatLng(51.005, 22.001427))!!
        assertEquals(100.0, p.offRouteMeters, 2.0)
        assertEquals(556.0, p.alongMeters, 3.0)
    }

    @Test
    fun `position before start and after end is clamped`() {
        assertEquals(0.0, routeProgress(north, LatLng(50.99, 22.0))!!.alongMeters, 0.001)
        val end = routeProgress(north, LatLng(51.02, 22.0))!!
        assertEquals(end.totalMeters, end.alongMeters, 0.001)
        assertEquals(0.0, end.remainingMeters, 0.001)
    }

    @Test
    fun `out and back route uses minAlong to tell the legs apart`() {
        val outAndBack = listOf(LatLng(51.0, 22.0), LatLng(51.01, 22.0), LatLng(51.0, 22.0))
        val here = LatLng(51.002, 22.0)
        // bez ograniczenia wygrywa pierwsze przejście
        assertEquals(222.0, routeProgress(outAndBack, here)!!.alongMeters, 3.0)
        // po przejściu 1500 m to już droga powrotna
        assertEquals(2002.0, routeProgress(outAndBack, here, minAlongMeters = 1500.0)!!.alongMeters, 3.0)
    }

    @Test
    fun `too short route gives null`() {
        assertNull(routeProgress(listOf(LatLng(51.0, 22.0)), LatLng(51.0, 22.0)))
    }
}
