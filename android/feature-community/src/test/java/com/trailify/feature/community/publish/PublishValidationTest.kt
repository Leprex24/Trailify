package com.trailify.feature.community.publish

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Granice pól formularza publikacji zgodne z RouteCreate w docs/openapi.yaml. */
class PublishValidationTest {

    @Test
    fun pustaNazwaDajeBlad() {
        assertEquals("Podaj nazwę trasy", validateRouteName("   "))
    }

    @Test
    fun nazwaNaGranicyPrzechodzi() {
        assertNull(validateRouteName("a".repeat(ROUTE_NAME_MAX)))
    }

    @Test
    fun zaDlugaNazwaDajeBlad() {
        assertNotNull(validateRouteName("a".repeat(ROUTE_NAME_MAX + 1)))
    }

    @Test
    fun pustyOpisJestDozwolony() {
        assertNull(validateRouteDescription(""))
    }

    @Test
    fun zaDlugiOpisDajeBlad() {
        assertNull(validateRouteDescription("a".repeat(ROUTE_DESCRIPTION_MAX)))
        assertNotNull(validateRouteDescription("a".repeat(ROUTE_DESCRIPTION_MAX + 1)))
    }

    @Test
    fun limitZdjecMaksymalnieDziesiec() {
        assertEquals(3, photosThatFit(current = 0, picked = 3))
        assertEquals(2, photosThatFit(current = 8, picked = 5))
        assertEquals(0, photosThatFit(current = 10, picked = 1))
    }
}
