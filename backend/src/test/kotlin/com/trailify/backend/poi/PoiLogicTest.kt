package com.trailify.backend.poi

import com.trailify.backend.account.ProfileRecord
import com.trailify.backend.admin.userActionRejection
import com.trailify.backend.route.PhotoInputRequest
import com.trailify.backend.web.ApiException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.time.OffsetDateTime
import java.util.UUID

class PoiLogicTest {

    private val userId = UUID.fromString("3f1c2b7e-0000-0000-0000-000000000000")
    private val otherId = UUID.fromString("9a9a9a9a-0000-0000-0000-000000000000")

    // --- GET /pois?bbox ---

    @Test
    fun `poprawny bbox jest odczytany`() {
        assertThat(parseBbox("22.45,51.20,22.65,51.30")).isEqualTo(BoundingBox(22.45, 51.20, 22.65, 51.30))
    }

    @Test
    fun `brak lub zly bbox daje VALIDATION_ERROR`() {
        listOf(null, "", "22.45,51.20,22.65", "a,b,c,d", "22.65,51.20,22.45,51.30", "200,0,201,1").forEach { value ->
            assertThatThrownBy { parseBbox(value) }
                .isInstanceOf(ApiException::class.java)
                .extracting("code").isEqualTo("VALIDATION_ERROR")
        }
    }

    @Test
    fun `za duzy obszar mapy jest odrzucony`() {
        assertThatThrownBy { parseBbox("20.0,50.0,23.0,53.0") }.isInstanceOf(ApiException::class.java)
    }

    // --- POST /pois/{id}/collect ---

    @Test
    fun `odleglosc liczona w metrach`() {
        // ok. 0,001 stopnia szerokości to ok. 111 m
        assertThat(distanceMeters(51.2477, 22.5675, 51.2487, 22.5675)).isBetween(105.0, 116.0)
    }

    @Test
    fun `w promieniu 50 m mozna zebrac punkt`() {
        assertThat(collectRejection(distance = 49.0, alreadyCollected = false)).isNull()
    }

    @Test
    fun `za daleko daje 422 POI_TOO_FAR`() {
        val e = collectRejection(distance = 51.0, alreadyCollected = false)!!
        assertThat(e.status).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT)
        assertThat(e.code).isEqualTo(CODE_POI_TOO_FAR)
    }

    @Test
    fun `ponowne zebranie daje 409`() {
        val e = collectRejection(distance = 0.0, alreadyCollected = true)!!
        assertThat(e.status).isEqualTo(HttpStatus.CONFLICT)
        assertThat(e.code).isEqualTo(CODE_ALREADY_COLLECTED)
    }

    // --- POST /pois: zdjęcie ---

    private fun create(photo: PhotoInputRequest?) =
        PoiCreateRequest(name = "Brama", description = "Opis", latitude = 51.0, longitude = 22.0, photo = photo)

    @Test
    fun `zgloszenie bez zdjecia jest poprawne`() {
        assertThat(validatePoiCreate(userId, create(null))).isEmpty()
    }

    @Test
    fun `zdjecie z cudzego folderu jest odrzucone`() {
        val photo = PhotoInputRequest("$otherId/a.jpg", "https://example.com/a.jpg")
        assertThat(validatePoiCreate(userId, create(photo)).map { it.field }).containsExactly("photo")
    }

    // --- PATCH /admin/pois/{id} ---

    @Test
    fun `edycja zmienia tylko przeslane pola`() {
        val update = parsePoiUpdate(mapOf("name" to " Nowa nazwa ", "latitude" to 51.5))
        assertThat(update).isEqualTo(PoiUpdate(name = "Nowa nazwa", description = null, latitude = 51.5, longitude = null))
    }

    @Test
    fun `pusta nazwa i zla wspolrzedna sa odrzucone`() {
        assertThatThrownBy { parsePoiUpdate(mapOf("name" to " ", "longitude" to 300)) }
            .isInstanceOf(ApiException::class.java)
            .extracting("code").isEqualTo("VALIDATION_ERROR")
    }

    @Test
    fun `edycja moze zmienic kategorie`() {
        assertThat(parsePoiUpdate(mapOf("category" to "VIEWPOINT")).category).isEqualTo("VIEWPOINT")
    }

    @Test
    fun `nieznana kategoria jest odrzucona`() {
        assertThatThrownBy { parsePoiUpdate(mapOf("category" to "ZAMEK")) }
            .isInstanceOf(ApiException::class.java)
            .extracting("code").isEqualTo("VALIDATION_ERROR")
    }

    @Test
    fun `parsowanie kategorii rozpoznaje tylko znane nazwy`() {
        assertThat(parsePoiCategory("NATURE")).isEqualTo(PoiCategory.NATURE)
        assertThat(parsePoiCategory("nature")).isNull()
        assertThat(parsePoiCategory(null)).isNull()
    }

    // --- /admin/users ---

    private fun profile(id: UUID, role: String) =
        ProfileRecord(id, "a@b.pl", "A", null, role, "ACTIVE", OffsetDateTime.parse("2026-10-01T10:00:00Z"))

    @Test
    fun `administrator moze zablokowac zwyklego uzytkownika`() {
        assertThat(userActionRejection(userId, profile(otherId, "USER"))).isNull()
    }

    @Test
    fun `nie mozna zablokowac siebie ani innego administratora`() {
        assertThat(userActionRejection(userId, profile(userId, "ADMIN"))!!.status).isEqualTo(HttpStatus.FORBIDDEN)
        assertThat(userActionRejection(userId, profile(otherId, "ADMIN"))!!.status).isEqualTo(HttpStatus.FORBIDDEN)
    }
}
