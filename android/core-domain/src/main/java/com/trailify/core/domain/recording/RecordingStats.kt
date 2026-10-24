package com.trailify.core.domain.recording

import com.trailify.core.domain.model.LatLng
import com.trailify.core.domain.model.TrackPoint
import java.time.Duration
import java.time.Instant

// Statystyki liczone z tego, co już zebrano. Czysta logika, więc testowalna.

/** Prędkość uznawana za stanie w miejscu: poniżej tego nie pokazujemy tempa (m/s, ok. 0,7 km/h). */
private const val STANDING_SPEED_MPS = 0.2

/** Jak stary może być ostatni punkt, żeby uznać, że jeszcze się ruszamy (GPS pomija drgania w miejscu). */
private const val STALE_AFTER_SECONDS = 15L

/** Średnia prędkość z całego nagrania (m/s), null gdy jeszcze nie ma z czego liczyć. */
fun averageSpeedMps(distanceMeters: Double, elapsedSeconds: Long): Double? =
    if (elapsedSeconds > 0 && distanceMeters > 0) distanceMeters / elapsedSeconds else null

/**
 * Bieżąca prędkość (m/s) z ostatnich [windowSeconds] sekund. Zwraca 0.0, gdy od ostatniego
 * punktu minęło więcej niż 15 s (stoisz), a null, gdy za mało danych.
 */
fun currentSpeedMps(points: List<TrackPoint>, now: Instant, windowSeconds: Long = 30): Double? {
    val last = points.lastOrNull() ?: return null
    if (Duration.between(last.recordedAt, now).seconds > STALE_AFTER_SECONDS) return 0.0
    if (points.size < 2) return null

    val windowStart = last.recordedAt.minusSeconds(windowSeconds)
    var distance = 0.0
    var first = last
    for (i in points.size - 2 downTo 0) {
        val p = points[i]
        distance += distanceMeters(LatLng(p.latitude, p.longitude), LatLng(first.latitude, first.longitude))
        first = p
        if (p.recordedAt <= windowStart) break
    }
    val seconds = Duration.between(first.recordedAt, last.recordedAt).toMillis() / 1000.0
    return if (seconds >= 3) distance / seconds else null
}

/** Tempo w minutach na kilometr (np. 5.5 = 5:30 /km). null, gdy stoisz albo brak danych. */
fun paceMinPerKm(speedMps: Double?): Double? =
    if (speedMps == null || speedMps < STANDING_SPEED_MPS) null else 1000.0 / speedMps / 60.0
