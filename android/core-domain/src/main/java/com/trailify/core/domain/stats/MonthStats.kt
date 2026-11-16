package com.trailify.core.domain.stats

import com.trailify.core.domain.model.ActivitySummary
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Co pokazuje wykres statystyk miesiąca. */
enum class StatMetric(val label: String) {
    DISTANCE("Kilometry"),
    STEPS("Kroki"),
    DURATION("Czas"),
}

/** Aktywności jednego dnia (według daty rozpoczęcia, w strefie czasowej użytkownika). */
data class DayStats(val date: LocalDate, val activities: List<ActivitySummary>) {
    val distanceMeters: Double = activities.sumOf { it.distance ?: 0.0 }
    val steps: Int = activities.sumOf { it.stepCount ?: 0 }
    val durationSeconds: Int = activities.sumOf { it.durationSeconds ?: 0 }

    fun value(metric: StatMetric): Double = when (metric) {
        StatMetric.DISTANCE -> distanceMeters
        StatMetric.STEPS -> steps.toDouble()
        StatMetric.DURATION -> durationSeconds.toDouble()
    }
}

/** Jeden miesiąc: wszystkie dni (także puste), żeby wykres miał stałą liczbę słupków. */
data class MonthStats(val month: YearMonth, val days: List<DayStats>) {
    val totalDistanceMeters: Double = days.sumOf { it.distanceMeters }
    val totalSteps: Int = days.sumOf { it.steps }
    val totalDurationSeconds: Int = days.sumOf { it.durationSeconds }
    val activityCount: Int = days.sumOf { it.activities.size }
    val activeDays: Int = days.count { it.activities.isNotEmpty() }

    fun total(metric: StatMetric): Double = days.sumOf { it.value(metric) }

    /** Największa dzienna wartość (do skali wykresu); 0 przy pustym miesiącu. */
    fun maxValue(metric: StatMetric): Double = days.maxOfOrNull { it.value(metric) } ?: 0.0

    fun day(date: LocalDate): DayStats? = days.firstOrNull { it.date == date }
}

/** Granice miesiąca w UTC do zapytania o aktywności: początek włącznie, początek następnego miesiąca wyłącznie. */
fun monthBounds(month: YearMonth, zone: ZoneId): Pair<Instant, Instant> =
    month.atDay(1).atStartOfDay(zone).toInstant() to month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant()

/** Grupuje aktywności po dniach miesiąca [month]. Aktywności spoza miesiąca są pomijane. */
fun buildMonthStats(activities: List<ActivitySummary>, month: YearMonth, zone: ZoneId): MonthStats {
    val byDay = activities.groupBy { it.startTime.atZone(zone).toLocalDate() }
    val days = (1..month.lengthOfMonth()).map { dayOfMonth ->
        val date = month.atDay(dayOfMonth)
        DayStats(date, byDay[date].orEmpty().sortedBy { it.startTime })
    }
    return MonthStats(month, days)
}
