package com.trailify.core.ui

import com.trailify.core.domain.model.AppException
import com.trailify.core.domain.repository.AuthErrorCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Testy jednostkowe walidacji formularzy konta i komunikatów błędów. */
class ValidationTest {

    @Test
    fun poprawnyEmailPrzechodzi() {
        assertNull(validateEmail("jan.kowalski@student.pl"))
    }

    @Test
    fun pustyEmailDajeBlad() {
        assertEquals("Wpisz adres e-mail", validateEmail(""))
    }

    @Test
    fun emailBezMalpyDajeBlad() {
        assertNotNull(validateEmail("jan.kowalski.pl"))
    }

    @Test
    fun zaKrotkieHasloDajeBlad() {
        assertEquals("Hasło musi mieć co najmniej 8 znaków", validateNewPassword("abc1"))
    }

    @Test
    fun hasloBezCyfryDajeBlad() {
        assertEquals("Dodaj co najmniej jedną cyfrę", validateNewPassword("abcdefgh"))
    }

    @Test
    fun dobreHasloPrzechodzi() {
        assertNull(validateNewPassword("gorki2026"))
    }

    @Test
    fun pustaNazwaDajeBlad() {
        assertNotNull(validateDisplayName("   "))
    }

    // Granice 2-50 znaków jak w ProfileUpdate z docs/openapi.yaml
    @Test
    fun nazwaJednoznakowaDajeBlad() {
        assertNotNull(validateDisplayName("A"))
    }

    @Test
    fun nazwaNaGranicachPrzechodzi() {
        assertNull(validateDisplayName("Al"))
        assertNull(validateDisplayName("a".repeat(50)))
    }

    @Test
    fun zaDlugaNazwaDajeBlad() {
        assertNotNull(validateDisplayName("a".repeat(51)))
    }

    @Test
    fun kodBleduZamienianyNaKomunikat() {
        val error = AppException(AuthErrorCodes.INVALID_CREDENTIALS, "Invalid login credentials")
        assertEquals("Nieprawidłowy e-mail lub hasło", error.toUserMessage())
    }

    @Test
    fun nieznanyBladDajeOgolnyKomunikat() {
        assertEquals("Coś poszło nie tak, spróbuj ponownie", IllegalStateException().toUserMessage())
    }
}
