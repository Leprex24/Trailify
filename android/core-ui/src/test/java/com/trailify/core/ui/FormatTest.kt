package com.trailify.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test
    fun `zegar krotszy niz godzina`() {
        assertEquals("00:00", formatClock(0))
        assertEquals("01:15", formatClock(75))
    }

    @Test
    fun `zegar z godzinami`() {
        assertEquals("1:02:05", formatClock(3725))
    }

    @Test
    fun `dystans na zywo`() {
        assertEquals("1,25 km", formatDistanceLive(1250.0))
        assertEquals("0,00 km", formatDistanceLive(0.0))
    }

    @Test
    fun `tempo i predkosc`() {
        assertEquals("5:30 /km", formatPace(5.5))
        assertEquals("-", formatPace(null))
        assertEquals("18,0 km/h", formatSpeed(5.0))
        assertEquals("-", formatSpeed(null))
    }
}
