package com.trailify.backend.common

/** GeoJSON LineString: współrzędne w kolejności [longitude, latitude] (GeoJsonLineString z openapi.yaml). */
data class GeoJsonLineString(
    val type: String = "LineString",
    val coordinates: List<List<Double>>,
)

/** Składa tekst WKT (`LINESTRING(lng lat, lng lat, ...)`) z par (longitude, latitude), do zapisu w PostGIS. */
fun lineStringWkt(points: List<Pair<Double, Double>>): String =
    points.joinToString(prefix = "LINESTRING(", separator = ",", postfix = ")") { (lng, lat) -> "$lng $lat" }

/** Odczytuje WKT zwrócony przez `ST_AsText` (`LINESTRING(22.5 51.2,22.6 51.3)`) do postaci GeoJSON. */
fun parseLineStringWkt(wkt: String): GeoJsonLineString {
    val open = wkt.indexOf('(')
    val close = wkt.lastIndexOf(')')
    require(wkt.startsWith("LINESTRING") && open in 0 until close) { "To nie jest LINESTRING: $wkt" }
    val coordinates = wkt.substring(open + 1, close)
        .split(',')
        .map { pair -> pair.trim().split(' ').filter { it.isNotEmpty() }.map { it.toDouble() } }
    return GeoJsonLineString(coordinates = coordinates)
}
