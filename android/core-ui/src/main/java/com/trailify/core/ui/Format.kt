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

/** Zegar nagrywania: 75 -> "01:15", 3725 -> "1:02:05" */
fun formatClock(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) String.format(PL, "%d:%02d:%02d", h, m, s) else String.format(PL, "%02d:%02d", m, s)
}

/** Dystans z dwoma miejscami po przecinku, do licznika na żywo: 1250.0 -> "1,25 km" */
fun formatDistanceLive(meters: Double): String = String.format(PL, "%.2f km", meters / 1000)

/** 5.5 -> "5:30 /km", null -> "-" */
fun formatPace(minPerKm: Double?): String {
    if (minPerKm == null) return "-"
    val total = Math.round(minPerKm * 60).toInt()
    return String.format(PL, "%d:%02d /km", total / 60, total % 60)
}

/** 5.0 m/s -> "18,0 km/h", null -> "-" */
fun formatSpeed(mps: Double?): String =
    if (mps == null) "-" else String.format(PL, "%.1f km/h", mps * 3.6)
