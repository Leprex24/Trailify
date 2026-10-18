package com.trailify.backend.activity

import com.trailify.backend.common.ActivityType
import com.trailify.backend.common.PageRequest
import com.trailify.backend.common.lineStringWkt
import com.trailify.backend.common.pageOf
import com.trailify.backend.common.parseLineStringWkt
import com.trailify.backend.web.ApiException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class ActivityLogicTest {

    private fun request(start: Instant, end: Instant) = ActivityCreateRequest(
        clientId = UUID.randomUUID(),
        type = ActivityType.WALKING,
        routeId = null,
        startTime = start,
        endTime = end,
        distance = 1000.0,
        durationSeconds = 600,
        avgPace = 10.0,
        stepCount = 1300,
        points = emptyList(),
    )

    @Test
    fun `WKT ma kolejnosc longitude latitude`() {
        val wkt = lineStringWkt(listOf(22.5684 to 51.2465, 22.5701 to 51.248))
        assertThat(wkt).isEqualTo("LINESTRING(22.5684 51.2465,22.5701 51.248)")
    }

    @Test
    fun `odczyt WKT z bazy daje GeoJSON`() {
        val geometry = parseLineStringWkt("LINESTRING(22.5684 51.2465,22.5701 51.248,22.6 51.3)")
        assertThat(geometry.type).isEqualTo("LineString")
        assertThat(geometry.coordinates).containsExactly(
            listOf(22.5684, 51.2465),
            listOf(22.5701, 51.248),
            listOf(22.6, 51.3),
        )
    }

    @Test
    fun `WKT przechodzi tam i z powrotem bez zmian`() {
        val points = listOf(22.1 to 51.1, 22.2 to 51.2, 1.0E-5 to -0.5)
        assertThat(parseLineStringWkt(lineStringWkt(points)).coordinates)
            .containsExactly(listOf(22.1, 51.1), listOf(22.2, 51.2), listOf(1.0E-5, -0.5))
    }

    @Test
    fun `tekst, ktory nie jest LINESTRING, jest odrzucany`() {
        assertThatThrownBy { parseLineStringWkt("POINT(1 2)") }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `koniec przed poczatkiem to blad walidacji`() {
        val start = Instant.parse("2026-10-20T10:00:00Z")
        val errors = validateActivityFields(request(start, start.minusSeconds(60)))
        assertThat(errors.map { it.field }).containsExactly("endTime")
    }

    @Test
    fun `poprawne daty nie daja bledow`() {
        val start = Instant.parse("2026-10-20T10:00:00Z")
        assertThat(validateActivityFields(request(start, start.plusSeconds(600)))).isEmpty()
        assertThat(validateActivityFields(request(start, start))).isEmpty()
    }

    @Test
    fun `strona poza zakresem jest odrzucana`() {
        assertThatThrownBy { PageRequest.of(-1, 20) }.isInstanceOf(ApiException::class.java)
        assertThatThrownBy { PageRequest.of(0, 0) }.isInstanceOf(ApiException::class.java)
        assertThatThrownBy { PageRequest.of(0, 101) }.isInstanceOf(ApiException::class.java)
    }

    @Test
    fun `liczba stron i przesuniecie`() {
        val page = PageRequest.of(2, 20)
        assertThat(page.offset).isEqualTo(40)
        assertThat(pageOf(page, 41L, emptyList<String>()).totalPages).isEqualTo(3)
        assertThat(pageOf(page, 0L, emptyList<String>()).totalPages).isEqualTo(0)
    }
}
