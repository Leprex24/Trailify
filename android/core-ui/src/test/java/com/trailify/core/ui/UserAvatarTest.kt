package com.trailify.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class UserAvatarTest {

    @Test
    fun inicjalToPierwszaLiteraWielka() {
        assertEquals("M", avatarInitial("Marek K."))
        assertEquals("Ł", avatarInitial("łukasz"))
    }

    @Test
    fun inicjalPomijaSpacjeISymbole() {
        assertEquals("A", avatarInitial("  @Anna"))
        assertEquals("7", avatarInitial("7up"))
    }

    @Test
    fun brakLiterDajePytajnik() {
        assertEquals("?", avatarInitial(""))
        assertEquals("?", avatarInitial("  ---"))
    }

    @Test
    fun kolorZalezyTylkoOdId() {
        val id = "00000000-0000-0000-0000-000000000001"
        assertEquals(avatarColor(id), avatarColor(id))
    }

    @Test
    fun rozneIdMajaZwyklePrzynajmniejDwaKolory() {
        val colors = (1..20).map { avatarColor("user-$it") }.toSet()
        assertNotEquals(1, colors.size)
    }
}
