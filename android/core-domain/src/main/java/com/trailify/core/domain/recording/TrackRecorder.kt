package com.trailify.core.domain.recording

import com.trailify.core.domain.model.LatLng
import com.trailify.core.domain.model.TrackPoint
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private const val EARTH_RADIUS_M = 6_371_000.0

/** Odległość w metrach między dwoma punktami (wzór haversine). */
fun distanceMeters(a: LatLng, b: LatLng): Double {
    val dLat = Math.toRadians(b.latitude - a.latitude)
    val dLng = Math.toRadians(b.longitude - a.longitude)
    val h = sin(dLat / 2).pow(2) +
        cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(dLng / 2).pow(2)
    return 2 * EARTH_RADIUS_M * asin(sqrt(h))
}

/**
 * Zbiera punkty GPS i liczy przebyty dystans. Czysta logika, bez Androida, dlatego da się ją testować.
 *
 * GPS potrafi "drgać" w miejscu i czasem zwraca błędne skoki, więc punkt jest odrzucany, gdy:
 *  - jego dokładność jest gorsza niż [maxAccuracyMeters],
 *  - leży bliżej niż [minStepMeters] od poprzedniego (stoisz w miejscu, a GPS się waha),
 *  - wynika z niego prędkość większa niż [maxSpeedMps] (skok o kilometr w sekundę).
 */
class TrackRecorder(
    private val maxAccuracyMeters: Float = 30f,
    private val minStepMeters: Double = 3.0,
    private val maxSpeedMps: Double = 40.0,
) {
    private val accepted = ArrayList<TrackPoint>()
    private var last: TrackPoint? = null

    var distanceMeters: Double = 0.0
        private set

    val size: Int get() = accepted.size

    /** Zwraca true, gdy punkt został zapisany. */
    fun add(point: TrackPoint, accuracyMeters: Float? = null): Boolean {
        if (accuracyMeters != null && accuracyMeters > maxAccuracyMeters) return false

        val prev = last
        if (prev == null) {
            accepted.add(point)
            last = point
            return true
        }

        val step = distanceMeters(
            LatLng(prev.latitude, prev.longitude),
            LatLng(point.latitude, point.longitude),
        )
        if (step < minStepMeters) return false

        val seconds = (point.recordedAt.toEpochMilli() - prev.recordedAt.toEpochMilli()) / 1000.0
        if (seconds > 0 && step / seconds > maxSpeedMps) return false

        distanceMeters += step
        accepted.add(point)
        last = point
        return true
    }

    /**
     * Po pauzie następny punkt nie jest doliczany do dystansu od ostatniego sprzed pauzy
     * (przerwa nie jest trasą).
     */
    fun breakSegment() {
        last = null
    }

    /** Przywraca stan zapisany wcześniej (po restarcie aplikacji). Dystans między sesjami się nie liczy. */
    fun restore(points: List<TrackPoint>, distanceMeters: Double) {
        accepted.clear()
        accepted.addAll(points)
        this.distanceMeters = distanceMeters
        last = null
    }

    fun snapshot(): List<TrackPoint> = accepted.toList()
}
