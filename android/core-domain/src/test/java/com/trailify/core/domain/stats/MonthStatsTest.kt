package com.trailify.core.domain.stats

import com.trailify.core.domain.model.ActivitySummary
import com.trailify.core.domain.model.ActivityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class MonthStatsTest {

    private val warsaw = ZoneId.of("Europe/Warsaw")
    private val november = YearMonth.of(2026, 11)

    private fun activity(
        id: Long,
        start: String,
        distance: Double? = 1000.0,
        seconds: Int? = 600,
        steps: Int? = 1500,
    ) = ActivitySummary(id, ActivityType.WALKING, null, Instant.parse(start), null, distance, seconds, null, steps)

    @Test
    fun miesiacMaWszystkieDniTakzePuste() {
        val stats = buildMonthStats(emptyList(), november, warsaw)
        assertEquals(30, stats.days.size)
        assertEquals(LocalDate.of(2026, 11, 1), stats.days.first().date)
        assertEquals(LocalDate.of(2026, 11, 30), stats.days.last().date)
        assertEquals(0, stats.activityCount)
        assertEquals(0.0, stats.maxValue(StatMetric.DISTANCE), 0.0)
    }

    @Test
    fun aktywnosciTegoSamegoDniSaSumowane() {
        val stats = buildMonthStats(
            listOf(
                activity(1, "2026-11-10T08:00:00Z", distance = 3000.0, seconds = 1200, steps = 4000),
                activity(2, "2026-11-10T16:00:00Z", distance = 2000.0, seconds = 600, steps = null),
            ),
            november,
            warsaw,
        )
        val day = stats.day(LocalDate.of(2026, 11, 10))!!
        assertEquals(5000.0, day.distanceMeters, 0.0)
        assertEquals(1800, day.durationSeconds)
        assertEquals(4000, day.steps) // brak kroków (np. rower) liczy się jak 0
        assertEquals(2, day.activities.size)
        assertEquals(1, stats.activeDays)
    }

    @Test
    fun dzienLiczySieWStrefieUzytkownika() {
        // 23:30 UTC 10 listopada to już 00:30 11 listopada w Warszawie (UTC+1)
        val stats = buildMonthStats(listOf(activity(1, "2026-11-10T23:30:00Z")), november, warsaw)
        assertEquals(0, stats.day(LocalDate.of(2026, 11, 10))!!.activities.size)
        assertEquals(1, stats.day(LocalDate.of(2026, 11, 11))!!.activities.size)
    }

    @Test
    fun aktywnosciSpozaMiesiacaSaPomijane() {
        val stats = buildMonthStats(
            listOf(activity(1, "2026-10-31T10:00:00Z"), activity(2, "2026-12-01T10:00:00Z"), activity(3, "2026-11-05T10:00:00Z")),
            november,
            warsaw,
        )
        assertEquals(1, stats.activityCount)
    }

    @Test
    fun sumyIMaksimumDlaKazdejMetryki() {
        val stats = buildMonthStats(
            listOf(
                activity(1, "2026-11-02T10:00:00Z", distance = 4000.0, seconds = 1000, steps = 5000),
                activity(2, "2026-11-03T10:00:00Z", distance = 10_000.0, seconds = 3000, steps = 12_000),
            ),
            november,
            warsaw,
        )
        assertEquals(14_000.0, stats.total(StatMetric.DISTANCE), 0.0)
        assertEquals(17_000.0, stats.total(StatMetric.STEPS), 0.0)
        assertEquals(4000.0, stats.total(StatMetric.DURATION), 0.0)
        assertEquals(10_000.0, stats.maxValue(StatMetric.DISTANCE), 0.0)
        assertEquals(12_000.0, stats.maxValue(StatMetric.STEPS), 0.0)
    }

    @Test
    fun koniecPazdziernikaWStrefieLokalnejNieTrafiaDoListopada() {
        // 22:30 UTC 31 października to 23:30 czasu lokalnego (UTC+1), czyli nadal październik
        val stats = buildMonthStats(listOf(activity(1, "2026-10-31T22:30:00Z")), november, warsaw)
        assertEquals(0, stats.activityCount)
        assertNull(stats.day(LocalDate.of(2026, 10, 31)))
    }

    @Test
    fun granicePrzedzialuMiesiacaSaWUtcZUwzglednieniemStrefy() {
        val (from, to) = monthBounds(november, warsaw)
        assertEquals(Instant.parse("2026-10-31T23:00:00Z"), from)
        assertEquals(Instant.parse("2026-11-30T23:00:00Z"), to)
    }
}
