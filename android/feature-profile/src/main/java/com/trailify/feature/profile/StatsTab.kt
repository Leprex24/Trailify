package com.trailify.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.trailify.core.domain.stats.DayStats
import com.trailify.core.domain.stats.MonthStats
import com.trailify.core.domain.stats.StatMetric
import com.trailify.core.ui.UiState
import com.trailify.core.ui.formatDistance
import com.trailify.core.ui.formatDuration
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val PL = Locale.forLanguageTag("pl-PL")
private val MONTH_FORMAT = DateTimeFormatter.ofPattern("LLLL yyyy", PL)
private val DAY_FORMAT = DateTimeFormatter.ofPattern("EEEE, d MMMM", PL)

private val CHART_HEIGHT = 140.dp
private const val MIN_BAR_FRACTION = 0.03f

/** Zakładka Statystyki: wybór miesiąca, wykres dzienny (kilometry, kroki albo czas), sumy i szczegóły wybranego dnia. */
internal fun LazyListScope.statsTab(
    state: ProfileUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onMetric: (StatMetric) -> Unit,
    onSelectDay: (LocalDate) -> Unit,
    onOpenActivity: (Long) -> Unit,
    onRetry: () -> Unit,
) {
    item { MonthSwitcher(state.month, onPreviousMonth, onNextMonth) }
    item { MetricChips(state.metric, onMetric) }
    when (val stats = state.monthStats) {
        is UiState.Loading -> item { Hint("Wczytywanie...") }
        is UiState.Error -> item { RetryLine(stats.message, onRetry) }
        is UiState.Success -> {
            item { Totals(stats.data) }
            item {
                DayBars(
                    days = stats.data.days,
                    metric = state.metric,
                    selected = state.selectedDay,
                    onSelect = onSelectDay,
                )
            }
            if (state.metric == StatMetric.STEPS) {
                item {
                    Text(
                        "Kroki liczymy tylko podczas nagrywanych aktywności.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
            val selectedDay = state.selectedDay?.let { stats.data.day(it) }
            if (selectedDay != null) {
                item { DayDetailsHeader(selectedDay) }
                if (selectedDay.activities.isEmpty()) {
                    item { Hint("Tego dnia nie było aktywności.") }
                } else {
                    items(selectedDay.activities.size) { index ->
                        val activity = selectedDay.activities[index]
                        ActivityRow(activity, onClick = { onOpenActivity(activity.id) })
                    }
                }
            } else if (stats.data.activityCount == 0) {
                item { Hint("W tym miesiącu nie ma aktywności.") }
            }
        }
    }
}

@Composable
private fun MonthSwitcher(month: YearMonth, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        TextButton(onClick = onPrevious) { Text("‹", style = MaterialTheme.typography.headlineSmall) }
        Text(
            month.format(MONTH_FORMAT).replaceFirstChar { it.titlecase(PL) },
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        TextButton(onClick = onNext) { Text("›", style = MaterialTheme.typography.headlineSmall) }
    }
}

@Composable
private fun MetricChips(selected: StatMetric, onSelect: (StatMetric) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        StatMetric.entries.forEach { metric ->
            FilterChip(selected = selected == metric, onClick = { onSelect(metric) }, label = { Text(metric.label) })
        }
    }
}

@Composable
private fun Totals(stats: MonthStats) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Total("Dystans", formatDistance(stats.totalDistanceMeters), Modifier.weight(1f))
                Total("Czas", formatDuration(stats.totalDurationSeconds), Modifier.weight(1f))
                Total("Kroki", formatCount(stats.totalSteps), Modifier.weight(1f))
            }
            Text(
                "Aktywności: ${stats.activityCount} · aktywne dni: ${stats.activeDays}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun Total(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Słupki dni miesiąca. Dotknięcie słupka wybiera dzień; wysokość słupków jest względem najlepszego dnia. */
@Composable
private fun DayBars(days: List<DayStats>, metric: StatMetric, selected: LocalDate?, onSelect: (LocalDate) -> Unit) {
    val max = days.maxOfOrNull { it.value(metric) } ?: 0.0
    val full = MaterialTheme.colorScheme.primary
    val dimmed = full.copy(alpha = 0.4f)
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            if (max > 0.0) "Najwięcej w dniu: ${formatMetric(metric, max)}" else "Brak danych",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        Row(modifier = Modifier.fillMaxWidth().height(CHART_HEIGHT), verticalAlignment = Alignment.Bottom) {
            days.forEach { day ->
                val value = day.value(metric)
                val fraction = if (max <= 0.0 || value <= 0.0) 0f else (value / max).toFloat().coerceIn(MIN_BAR_FRACTION, 1f)
                val color = if (selected == null || selected == day.date) full else dimmed
                Box(
                    contentAlignment = Alignment.BottomCenter,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(onClickLabel = "Pokaż ${day.date.dayOfMonth}. dzień miesiąca") { onSelect(day.date) },
                ) {
                    if (fraction > 0f) {
                        Box(
                            Modifier
                                .fillMaxWidth(0.7f)
                                .fillMaxHeight(fraction)
                                .background(color, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)),
                        )
                    }
                }
            }
        }
        HorizontalDivider()
        Row(modifier = Modifier.fillMaxWidth()) {
            days.forEach { day ->
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    val number = day.date.dayOfMonth
                    if (number == 1 || number % 5 == 0) {
                        Text(
                            "$number",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.wrapContentWidth(unbounded = true),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayDetailsHeader(day: DayStats) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            day.date.format(DAY_FORMAT).replaceFirstChar { it.titlecase(PL) },
            style = MaterialTheme.typography.titleSmall,
        )
        if (day.activities.isNotEmpty()) {
            Text(
                "${formatDistance(day.distanceMeters)} · ${formatDuration(day.durationSeconds)} · ${formatCount(day.steps)} kroków",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatMetric(metric: StatMetric, value: Double): String = when (metric) {
    StatMetric.DISTANCE -> formatDistance(value)
    StatMetric.STEPS -> formatCount(value.toInt())
    StatMetric.DURATION -> formatDuration(value.toInt())
}

/** 12345 -> "12 345" (spacja jako separator tysięcy, jak w polskich zapisach). */
internal fun formatCount(value: Int): String = String.format(PL, "%,d", value)
