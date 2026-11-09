package com.trailify.feature.map

import com.trailify.core.domain.model.LatLng
import com.trailify.core.domain.model.TrackPoint
import com.trailify.core.domain.recording.GapInfo
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource

internal const val TRACK_SOURCE = "trailify-track-source"
internal const val POSITION_SOURCE = "trailify-position-source"
internal const val GAP_PREVIEW_SOURCE = "trailify-gap-preview-source"
internal const val GAP_POINTS_SOURCE = "trailify-gap-points-source"
internal const val RECONSTRUCTED_LAYER = "trailify-track-reconstructed-layer"
private const val EMPTY = """{"type":"FeatureCollection","features":[]}"""

/**
 * Dodaje na wierzch mapy pomarańczową linię nagrywanej trasy z białą obwódką (fragmenty odtworzone po utracie GPS są
 * bursztynową linią przerywaną) i niebieską kropkę z pozycją użytkownika.
 */
internal fun addRecordingLayers(style: Style) {
    style.addSource(GeoJsonSource(TRACK_SOURCE).also { it.setGeoJson(EMPTY) })
    style.addSource(GeoJsonSource(POSITION_SOURCE).also { it.setGeoJson(EMPTY) })
    // biała obwódka pod całym śladem (zwykłym i odtworzonym), żeby był czytelny na każdym tle
    style.addLayer(
        LineLayer("trailify-track-casing-layer", TRACK_SOURCE).withProperties(
            PropertyFactory.lineColor("#FFFFFF"),
            PropertyFactory.lineWidth(9.5f),
            PropertyFactory.lineOpacity(0.9f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        ),
    )
    style.addLayer(
        LineLayer("trailify-track-layer", TRACK_SOURCE)
            .withFilter(Expression.eq(Expression.get("reconstructed"), Expression.literal(false)))
            .withProperties(
                PropertyFactory.lineColor("#FC4C02"),
                PropertyFactory.lineWidth(5.5f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            ),
    )
    style.addLayer(
        LineLayer(RECONSTRUCTED_LAYER, TRACK_SOURCE)
            .withFilter(Expression.eq(Expression.get("reconstructed"), Expression.literal(true)))
            .withProperties(
                PropertyFactory.lineColor("#FFB300"),
                PropertyFactory.lineWidth(5.5f),
                PropertyFactory.lineDasharray(arrayOf(1.5f, 1.5f)),
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

/** Warstwy poprawiania luki w podsumowaniu: niebieski podgląd wybranej propozycji i punkty zaznaczane ręcznie. */
internal fun addGapEditLayers(style: Style) {
    style.addSource(GeoJsonSource(GAP_PREVIEW_SOURCE).also { it.setGeoJson(EMPTY) })
    style.addSource(GeoJsonSource(GAP_POINTS_SOURCE).also { it.setGeoJson(EMPTY) })
    style.addLayer(
        LineLayer("trailify-gap-preview-layer", GAP_PREVIEW_SOURCE).withProperties(
            PropertyFactory.lineColor("#1E88E5"),
            PropertyFactory.lineWidth(6f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        ),
    )
    style.addLayer(
        CircleLayer("trailify-gap-points-layer", GAP_POINTS_SOURCE).withProperties(
            PropertyFactory.circleRadius(8f),
            PropertyFactory.circleColor("#1E88E5"),
            PropertyFactory.circleStrokeColor("#FFFFFF"),
            PropertyFactory.circleStrokeWidth(3f),
        ),
    )
}

internal fun gapLineJson(points: List<LatLng>): String {
    if (points.size < 2) return EMPTY
    val coordinates = points.joinToString(",") { "[${it.longitude},${it.latitude}]" }
    return """{"type":"Feature","geometry":{"type":"LineString","coordinates":[$coordinates]}}"""
}

internal fun gapPointsJson(points: List<LatLng>): String {
    if (points.isEmpty()) return EMPTY
    val features = points.joinToString(",") {
        """{"type":"Feature","geometry":{"type":"Point","coordinates":[${it.longitude},${it.latitude}]}}"""
    }
    return """{"type":"FeatureCollection","features":[$features]}"""
}

/**
 * Trasa jako zbiór linii: kolejne odcinki tego samego rodzaju (zmierzone albo odtworzone po utracie GPS)
 * tworzą jedną linię z właściwością "reconstructed", według której mapa wybiera styl.
 * Odcinek jest odtworzony, gdy którykolwiek z jego końców jest punktem odtworzonym.
 * Linie odtworzone dostają też właściwość "gap" z numerem luki (z [gaps]), żeby dało się je dotknąć na mapie.
 */
internal fun trackJson(points: List<TrackPoint>, gaps: List<GapInfo> = emptyList()): String {
    if (points.size < 2) return EMPTY
    val features = ArrayList<String>()
    var run = arrayListOf(points[0])
    var runReconstructed = points[0].isReconstructed || points[1].isReconstructed
    for (i in 1 until points.size) {
        val reconstructed = points[i - 1].isReconstructed || points[i].isReconstructed
        if (reconstructed != runReconstructed) {
            features.add(lineFeature(run, runReconstructed, gaps))
            run = arrayListOf(points[i - 1])
            runReconstructed = reconstructed
        }
        run.add(points[i])
    }
    features.add(lineFeature(run, runReconstructed, gaps))
    return """{"type":"FeatureCollection","features":[${features.joinToString(",")}]}"""
}

private fun lineFeature(points: List<TrackPoint>, reconstructed: Boolean, gaps: List<GapInfo>): String {
    val coordinates = points.joinToString(",") { "[${it.longitude},${it.latitude}]" }
    val gapId = if (reconstructed) {
        gaps.firstOrNull { info ->
            points.any {
                it.isReconstructed &&
                    it.recordedAt.isAfter(info.gap.from.recordedAt) &&
                    it.recordedAt.isBefore(info.gap.to.recordedAt)
            }
        }?.id
    } else {
        null
    }
    val properties = if (gapId != null) """{"reconstructed":true,"gap":$gapId}""" else """{"reconstructed":$reconstructed}"""
    return """{"type":"Feature","properties":$properties,"geometry":{"type":"LineString","coordinates":[$coordinates]}}"""
}

internal fun positionJson(position: LatLng?): String {
    if (position == null) return EMPTY
    return """{"type":"Feature","geometry":{"type":"Point","coordinates":[${position.longitude},${position.latitude}]}}"""
}
