package com.trailify.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import com.trailify.core.domain.model.LatLng
import kotlin.math.cos
import kotlin.math.max

/**
 * Obrys trasy w kwadracie 0..1 z zachowaniem proporcji (długość geograficzna skrócona o cos szerokości),
 * wyśrodkowany, y rośnie w dół. Pusta lista, gdy trasa ma mniej niż 2 punkty albo wszystkie leżą w jednym miejscu.
 */
fun outlineUnitPoints(geometry: List<LatLng>): List<Pair<Float, Float>> {
    if (geometry.size < 2) return emptyList()
    val k = cos(Math.toRadians(geometry.map { it.latitude }.average()))
    val xs = geometry.map { it.longitude * k }
    val ys = geometry.map { it.latitude }
    val minX = xs.min()
    val minY = ys.min()
    val width = xs.max() - minX
    val height = ys.max() - minY
    val size = max(width, height)
    if (size <= 0.0) return emptyList()
    val offsetX = (size - width) / 2
    val offsetY = (size - height) / 2
    return geometry.indices.map { i ->
        (((xs[i] - minX + offsetX) / size).toFloat()) to ((1.0 - (ys[i] - minY + offsetY) / size).toFloat())
    }
}

/**
 * Lekki podgląd trasy: sam obrys bez mapy (znacznie tańszy niż MapView, więc nadaje się do długich list).
 * Początek trasy to zielona kropka, koniec to kropka w kolorze akcentu.
 */
@Composable
fun RouteOutline(geometry: List<LatLng>, modifier: Modifier = Modifier) {
    val points = outlineUnitPoints(geometry)
    val line = MaterialTheme.colorScheme.primary
    val start = Color(0xFF2E7D32)
    val end = MaterialTheme.colorScheme.tertiary
    Canvas(modifier.fillMaxSize()) {
        if (points.size < 2) return@Canvas
        val padding = 16.dp.toPx()
        val side = (minOf(size.width, size.height) - 2 * padding).coerceAtLeast(1f)
        val left = (size.width - side) / 2
        val top = (size.height - side) / 2
        fun at(p: Pair<Float, Float>) = Offset(left + p.first * side, top + p.second * side)

        val path = Path().apply {
            moveTo(at(points.first()).x, at(points.first()).y)
            points.drop(1).forEach { lineTo(at(it).x, at(it).y) }
        }
        drawPath(path, line, style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(start, radius = 6.dp.toPx(), center = at(points.first()))
        drawCircle(end, radius = 6.dp.toPx(), center = at(points.last()))
    }
}

/** Ikona "dodaj osobę" (Material person_add), której nie ma w podstawowym zestawie ikon. */
val PersonAddIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "PersonAdd",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).addPath(
        pathData = PathParser().parsePathString(
            "M15,12c2.21,0 4,-1.79 4,-4s-1.79,-4 -4,-4 -4,1.79 -4,4 1.79,4 4,4z" +
                "M6,10L6,7L4,7v3L1,10v2h3v3h2v-3h3v-2L6,10z" +
                "M15,14c-2.67,0 -8,1.34 -8,4v2h16v-2c0,-2.66 -5.33,-4 -8,-4z",
        ).toNodes(),
        fill = SolidColor(Color.Black),
    ).build()
}
