package com.trailify.feature.map

import com.trailify.core.domain.model.LatLng

/** Zamienia listę linii na tekst GeoJSON (FeatureCollection z LineString), który rozumie MapLibre. */
internal fun toGeoJson(lines: List<List<LatLng>>): String {
    val sb = StringBuilder(64 + lines.size * 64)
    sb.append("{\"type\":\"FeatureCollection\",\"features\":[")
    lines.forEachIndexed { i, line ->
        if (i > 0) sb.append(',')
        sb.append("{\"type\":\"Feature\",\"properties\":{},\"geometry\":{\"type\":\"LineString\",\"coordinates\":[")
        line.forEachIndexed { j, p ->
            if (j > 0) sb.append(',')
            sb.append('[').append(p.longitude).append(',').append(p.latitude).append(']')
        }
        sb.append("]}}")
    }
    sb.append("]}")
    return sb.toString()
}

internal const val EMPTY_GEOJSON = "{\"type\":\"FeatureCollection\",\"features\":[]}"
