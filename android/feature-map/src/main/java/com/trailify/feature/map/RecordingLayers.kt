package com.trailify.feature.map

import com.trailify.core.domain.model.LatLng
import com.trailify.core.domain.model.TrackPoint
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource

internal const val TRACK_SOURCE = "trailify-track-source"
internal const val POSITION_SOURCE = "trailify-position-source"
private const val EMPTY = """{"type":"FeatureCollection","features":[]}"""

/** Dodaje na wierzch mapy czerwoną linię nagrywanej trasy i niebieską kropkę z pozycją użytkownika. */
internal fun addRecordingLayers(style: Style) {
    style.addSource(GeoJsonSource(TRACK_SOURCE).also { it.setGeoJson(EMPTY) })
    style.addSource(GeoJsonSource(POSITION_SOURCE).also { it.setGeoJson(EMPTY) })
    style.addLayer(
        LineLayer("trailify-track-layer", TRACK_SOURCE).withProperties(
            PropertyFactory.lineColor("#E53935"),
            PropertyFactory.lineWidth(5f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        ),
    )
    style.addLayer(
        CircleLayer("trailify-position-layer", POSITION_SOURCE).withProperties(
            PropertyFactory.circleRadius(8f),
            PropertyFactory.circleColor("#1E88E5"),
            PropertyFactory.circleStrokeColor("#FFFFFF"),
            PropertyFactory.circleStrokeWidth(3f),
        ),
    )
}

internal fun trackJson(points: List<TrackPoint>): String {
    if (points.size < 2) return EMPTY
    val coordinates = points.joinToString(",") { "[${it.longitude},${it.latitude}]" }
    return """{"type":"Feature","geometry":{"type":"LineString","coordinates":[$coordinates]}}"""
}

internal fun positionJson(position: LatLng?): String {
    if (position == null) return EMPTY
    return """{"type":"Feature","geometry":{"type":"Point","coordinates":[${position.longitude},${position.latitude}]}}"""
}
