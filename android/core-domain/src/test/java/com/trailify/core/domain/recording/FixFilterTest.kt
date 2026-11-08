package com.trailify.core.domain.recording

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FixFilterTest {

    @Test
    fun `pierwszy odczyt po starcie jest pomijany, drugi dokladny przechodzi`() {
        val filter = FixFilter()
        assertFalse(filter.accept(nowMs = 0, ageMs = 0, accuracyMeters = 8f))
        assertTrue(filter.accept(nowMs = 1_000, ageMs = 0, accuracyMeters = 8f))
        assertTrue(filter.accept(nowMs = 2_000, ageMs = 0, accuracyMeters = 25f)) // po rozgrzewce działa zwykły limit
    }

    @Test
    fun `niedokladne odczyty nie przechodza rozgrzewki i przerywaja serie`() {
        val filter = FixFilter()
        assertFalse(filter.accept(0, 0, 8f))
        assertFalse(filter.accept(1_000, 0, 28f)) // seria dokładnych odczytów zaczyna się od nowa
        assertFalse(filter.accept(2_000, 0, 8f))
        assertTrue(filter.accept(3_000, 0, 8f))
    }

    @Test
    fun `stary odczyt z pamieci systemu jest odrzucany i nie liczy sie do rozgrzewki`() {
        val filter = FixFilter()
        assertFalse(filter.accept(0, ageMs = 60_000, accuracyMeters = 5f))
        assertFalse(filter.accept(1_000, ageMs = 60_000, accuracyMeters = 5f))
        assertFalse(filter.accept(2_000, 0, 5f))
        assertTrue(filter.accept(3_000, 0, 5f))
    }

    @Test
    fun `po dlugiej ciszy rozgrzewka zaczyna sie od nowa`() {
        val filter = FixFilter()
        filter.accept(0, 0, 5f)
        assertTrue(filter.accept(1_000, 0, 5f))
        assertTrue(filter.accept(2_000, 0, 5f))
        // GPS znika na dwie minuty, potem wraca
        assertFalse(filter.accept(122_000, 0, 5f))
        assertTrue(filter.accept(123_000, 0, 5f))
    }

    @Test
    fun `reset po wznowieniu wymaga nowej rozgrzewki`() {
        val filter = FixFilter()
        filter.accept(0, 0, 5f)
        assertTrue(filter.accept(1_000, 0, 5f))
        filter.reset()
        assertFalse(filter.accept(5_000, 0, 5f))
        assertTrue(filter.accept(6_000, 0, 5f))
    }

    @Test
    fun `slaby sygnal nie blokuje nagrywania na zawsze`() {
        val filter = FixFilter()
        assertFalse(filter.accept(0, 0, 25f))
        assertFalse(filter.accept(10_000, 0, 25f))
        assertTrue(filter.accept(20_000, 0, 25f)) // po 20 s przepuszczamy mimo słabej dokładności
    }

    @Test
    fun `nieznana dokladnosc liczy sie jako dobra`() {
        val filter = FixFilter()
        assertFalse(filter.accept(0, 0, null))
        assertTrue(filter.accept(1_000, 0, null))
    }
}
