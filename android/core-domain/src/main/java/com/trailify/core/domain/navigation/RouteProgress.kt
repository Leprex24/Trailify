package com.trailify.core.domain.navigation

import com.trailify.core.domain.model.LatLng
import com.trailify.core.domain.recording.distanceMeters
import kotlin.math.cos
import kotlin.math.hypot

private const val METERS_PER_DEGREE = 111_320.0

/**
 * Gdzie na zaplanowanej trasie jest użytkownik.
 * [alongMeters] to odległość od początku trasy do punktu trasy najbliższego użytkownikowi,
 * [offRouteMeters] to odległość użytkownika od trasy, [totalMeters] to długość całej trasy.
 */
data class RouteProgress(
    val alongMeters: Double,
    val offRouteMeters: Double,
    val totalMeters: Double,
) {
    val remainingMeters: Double get() = (totalMeters - alongMeters).coerceAtLeast(0.0)
}

/**
 * Rzutuje [position] na linię [route] i zwraca postęp albo null, gdy trasa ma mniej niż 2 punkty.
 *
 * [minAlongMeters] pomija początek trasy: dzięki temu trasa "tam i z powrotem" albo pętla,
 * która przechodzi dwa razy przez to samo miejsce, nie cofa postępu do pierwszego przejścia.
 */
fun routeProgress(route: List<LatLng>, position: LatLng, minAlongMeters: Double = 0.0): RouteProgress? {
    if (route.size < 2) return null
    // Na odcinkach rzędu setek metrów wystarczy płaskie przybliżenie z początkiem w pozycji użytkownika.
    val cosLat = cos(Math.toRadians(position.latitude))
    fun x(p: LatLng) = (p.longitude - position.longitude) * cosLat * METERS_PER_DEGREE
    fun y(p: LatLng) = (p.latitude - position.latitude) * METERS_PER_DEGREE

    var cumulative = 0.0
    var bestDistance = Double.MAX_VALUE
    var bestAlong = 0.0
    for (i in 0 until route.lastIndex) {
        val a = route[i]
        val b = route[i + 1]
        val length = distanceMeters(a, b)
        if (cumulative + length >= minAlongMeters) {
            val ax = x(a)
            val ay = y(a)
            val dx = x(b) - ax
            val dy = y(b) - ay
            val lengthSquared = dx * dx + dy * dy
            val t = if (lengthSquared == 0.0) 0.0 else ((-ax * dx - ay * dy) / lengthSquared).coerceIn(0.0, 1.0)
            val distance = hypot(ax + t * dx, ay + t * dy)
            if (distance < bestDistance) {
                bestDistance = distance
                bestAlong = cumulative + t * length
            }
        }
        cumulative += length
    }
    // minAlongMeters poza końcem trasy: liczymy od początku
    if (bestDistance == Double.MAX_VALUE) return routeProgress(route, position, 0.0)
    return RouteProgress(alongMeters = bestAlong, offRouteMeters = bestDistance, totalMeters = cumulative)
}
