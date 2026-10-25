package com.trailify.feature.map

import com.trailify.core.domain.model.LatLng
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource

internal const val PLAN_SOURCE = "trailify-plan-source"
internal const val PLAN_POINTS_SOURCE = "trailify-plan-points-source"
private const val EMPTY = """{"type":"FeatureCollection","features":[]}"""

private const val COLOR_START = "#43A047"
private const val COLOR_END = "#E53935"
private const val COLOR_MIDDLE = "#6A1B9A"

/** Dodaje fioletową linię zaplanowanej trasy i kropki punktów. Wywołać przed warstwami nagrywania, żeby czerwony ślad był na wierzchu. */
internal fun addPlanLayers(style: Style) {
    style.addSource(GeoJsonSource(PLAN_SOURCE).also { it.setGeoJson(EMPTY) })
    style.addSource(GeoJsonSource(PLAN_POINTS_SOURCE).also { it.setGeoJson(EMPTY) })
    style.addLayer(
        LineLayer("trailify-plan-layer", PLAN_SOURCE).withProperties(
            PropertyFactory.lineColor(COLOR_MIDDLE),
            PropertyFactory.lineWidth(6f),
            PropertyFactory.lineOpacity(0.85f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        ),
    )
    style.addLayer(
        CircleLayer("trailify-plan-points-layer", PLAN_POINTS_SOURCE).withProperties(
            PropertyFactory.circleRadius(8f),
            PropertyFactory.circleColor(Expression.toColor(Expression.get("color"))),
            PropertyFactory.circleStrokeColor("#FFFFFF"),
            PropertyFactory.circleStrokeWidth(3f),
        ),
    )
}

internal fun planLineJson(geometry: List<LatLng>): String {
    if (geometry.size < 2) return EMPTY
    val coordinates = geometry.joinToString(",") { "[${it.longitude},${it.latitude}]" }
    return """{"type":"Feature","properties":{},"geometry":{"type":"LineString","coordinates":[$coordinates]}}"""
}

/** Punkty trasy: pierwszy zielony, ostatni czerwony, pośrednie fioletowe. */
internal fun planPointsJson(points: List<LatLng>): String {
    if (points.isEmpty()) return EMPTY
    val features = points.mapIndexed { i, p ->
        val color = when (i) {
            0 -> COLOR_START
            points.lastIndex -> COLOR_END
            else -> COLOR_MIDDLE
        }
        """{"type":"Feature","properties":{"color":"$color"},"geometry":{"type":"Point","coordinates":[${p.longitude},${p.latitude}]}}"""
    }
    return """{"type":"FeatureCollection","features":[${features.joinToString(",")}]}"""
}
