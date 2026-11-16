package com.trailify.backend.route

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

class SavedRouteRulesTest {

    @Test
    fun `wlasna trase mozna zapisac tylko jako wlasna - zapis daje OWN_ROUTE`() {
        val e = saveRejection(isOwner = true)!!
        assertThat(e.status).isEqualTo(HttpStatus.CONFLICT)
        assertThat(e.code).isEqualTo(CODE_OWN_ROUTE)
    }

    @Test
    fun `cudza trase mozna zapisac`() {
        assertThat(saveRejection(isOwner = false)).isNull()
    }
}
