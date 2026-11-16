package com.trailify.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SwipeTabsTest {
    @Test
    fun przesuniecieWLewoOtwieraNastepnaZakladke() = assertEquals(1, tabAfterSwipe(0, 3, -200f, 100f))

    @Test
    fun przesuniecieWPrawoOtwieraPoprzedniaZakladke() = assertEquals(1, tabAfterSwipe(2, 3, 200f, 100f))

    @Test
    fun krotkiRuchNicNieZmienia() {
        assertEquals(1, tabAfterSwipe(1, 3, -99f, 100f))
        assertEquals(1, tabAfterSwipe(1, 3, 99f, 100f))
    }

    @Test
    fun naBrzegachZostajeNaMiejscu() {
        assertEquals(0, tabAfterSwipe(0, 3, 500f, 100f))
        assertEquals(2, tabAfterSwipe(2, 3, -500f, 100f))
    }
}
