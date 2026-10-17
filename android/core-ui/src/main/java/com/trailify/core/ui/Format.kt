package com.trailify.core.ui

import com.trailify.core.domain.model.ActivityType
import java.util.Locale

private val PL = Locale.forLanguageTag("pl-PL")

/** 26400.0 -> "26,4 km", 850.0 -> "850 m" */
fun formatDistance(meters: Double?): String {
    if (meters == null) return "-"
    return if (meters >= 1000) String.format(PL, "%.1f km", meters / 1000) else String.format(PL, "%.0f m", meters)
}

/** 5400 -> "1:30 h", 1650 -> "27 min" */
fun formatDuration(seconds: Int?): String {
    if (seconds == null) return "-"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return if (h > 0) String.format(PL, "%d:%02d h", h, m) else String.format(PL, "%d min", m)
}

fun ActivityType.label(): String = when (this) {
    ActivityType.CYCLING -> "Rower"
    ActivityType.RUNNING -> "Bieganie"
    ActivityType.WALKING -> "Spacer"
}
