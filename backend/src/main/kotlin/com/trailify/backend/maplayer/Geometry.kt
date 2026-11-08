package com.trailify.backend.maplayer

import com.trailify.backend.routing.LatLon
import kotlin.math.cos
import kotlin.math.hypot

private const val METERS_PER_DEGREE = 111_195.0

/**
 * Upraszcza linię algorytmem Douglasa-Peuckera: usuwa punkty, które odchylają trasę o mniej niż [toleranceMeters].
 * Zmniejsza to odpowiedź kilkukrotnie, a na mapie różnicy nie widać. Końce linii zostają zawsze.
 */
fun simplifyLine(points: List<LatLon>, toleranceMeters: Double): List<LatLon> {
    val n = points.size
    if (n <= 2) return points

    // lokalny rzut płaski (metry) względem pierwszego punktu: dla krótkich linii wystarczająco dokładny
    val lat0 = points.first().latitude
    val lng0 = points.first().longitude
    val kx = METERS_PER_DEGREE * cos(Math.toRadians(lat0))
    val xs = DoubleArray(n) { (points[it].longitude - lng0) * kx }
    val ys = DoubleArray(n) { (points[it].latitude - lat0) * METERS_PER_DEGREE }

    val keep = BooleanArray(n)
    keep[0] = true
    keep[n - 1] = true
    val stack = ArrayDeque<IntArray>()
    stack.addLast(intArrayOf(0, n - 1))
    while (stack.isNotEmpty()) {
        val range = stack.removeLast()
        val first = range[0]
        val last = range[1]
        var farthest = -1.0
        var index = -1
        for (i in first + 1 until last) {
            val d = distanceToSegment(xs[i], ys[i], xs[first], ys[first], xs[last], ys[last])
            if (d > farthest) {
                farthest = d
                index = i
            }
        }
        if (index > 0 && farthest > toleranceMeters) {
            keep[index] = true
            stack.addLast(intArrayOf(first, index))
            stack.addLast(intArrayOf(index, last))
        }
    }
    return points.filterIndexed { i, _ -> keep[i] }
}

private fun distanceToSegment(px: Double, py: Double, ax: Double, ay: Double, bx: Double, by: Double): Double {
    val dx = bx - ax
    val dy = by - ay
    val len2 = dx * dx + dy * dy
    if (len2 == 0.0) return hypot(px - ax, py - ay)
    val t = (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0.0, 1.0)
    return hypot(px - (ax + t * dx), py - (ay + t * dy))
}
