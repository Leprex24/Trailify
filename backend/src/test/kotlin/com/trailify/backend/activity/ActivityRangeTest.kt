package com.trailify.backend.activity

import com.trailify.backend.web.ApiException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant

class ActivityRangeTest {

    private val start = Instant.parse("2026-11-01T00:00:00Z")
    private val end = Instant.parse("2026-12-01T00:00:00Z")

    @Test
    fun `brak granic oznacza wszystkie aktywnosci`() {
        assertThat(activityRange(null, null)).isEqualTo(ActivityRange.ALL)
    }

    @Test
    fun `poprawny przedzial jest przyjmowany`() {
        assertThat(activityRange(start, end)).isEqualTo(ActivityRange(start, end))
    }

    @Test
    fun `mozna podac tylko jedna granice`() {
        assertThat(activityRange(start, null).to).isNull()
        assertThat(activityRange(null, end).from).isNull()
    }

    @Test
    fun `odwrocony albo pusty przedzial to blad 400`() {
        assertThatThrownBy { activityRange(end, start) }.isInstanceOf(ApiException::class.java)
        assertThatThrownBy { activityRange(start, start) }.isInstanceOf(ApiException::class.java)
    }
}
