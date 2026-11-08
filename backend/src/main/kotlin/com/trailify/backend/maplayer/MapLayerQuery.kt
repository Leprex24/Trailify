package com.trailify.backend.maplayer

/** Warstwy tematyczne mapy. Nazwy muszą być takie same jak w aplikacji (MapLayerType) i w openapi.yaml. */
enum class MapLayerType {
    /** Ścieżki rowerowe i trasy rowerowe. */
    CYCLE_PATHS,

    /** Szlaki i trasy piesze: znakowane, miejskie, tematyczne. */
    HIKING_TRAILS,

    /** Ścieżki i drogi gruntowe w lasach i parkach (bez zwykłych chodników). */
    FOREST_PATHS,
}

/** Prostokąt na mapie (stopnie). */
data class GeoBox(val minLat: Double, val minLng: Double, val maxLat: Double, val maxLng: Double)

/** Największy dozwolony prostokąt: duże kafelki aplikacji mają 0,15 x 0,24 stopnia. */
const val MAX_BOX_LAT_SPAN = 0.2
const val MAX_BOX_LNG_SPAN = 0.3

/** Sprawdza prostokąt z żądania. Zwraca opis błędu albo null, gdy jest poprawny. */
fun boxError(box: GeoBox): String? {
    val values = listOf(box.minLat, box.minLng, box.maxLat, box.maxLng)
    if (values.any { !it.isFinite() }) return "Współrzędne muszą być liczbami"
    if (box.minLat < -90 || box.maxLat > 90 || box.minLng < -180 || box.maxLng > 180) return "Współrzędne poza zakresem"
    if (box.minLat >= box.maxLat || box.minLng >= box.maxLng) return "Prostokąt jest pusty (min musi być mniejsze od max)"
    if (box.maxLat - box.minLat > MAX_BOX_LAT_SPAN || box.maxLng - box.minLng > MAX_BOX_LNG_SPAN) {
        return "Prostokąt jest zbyt duży (najwyżej $MAX_BOX_LAT_SPAN x $MAX_BOX_LNG_SPAN stopnia)"
    }
    return null
}

/**
 * Składa zapytanie Overpass QL dla warstwy i prostokąta. Te same zapytania wykonywała wcześniej aplikacja
 * na publicznych serwerach Overpass, teraz wykonuje je backend na własnym serwerze.
 * Kolejność w prostokącie Overpass: południe, zachód, północ, wschód.
 */
fun buildOverpassQuery(type: MapLayerType, box: GeoBox): String {
    val bbox = "${box.minLat},${box.minLng},${box.maxLat},${box.maxLng}"
    val body = when (type) {
        MapLayerType.CYCLE_PATHS -> """
            way["highway"="cycleway"]($bbox)->.paths;
            rel["route"="bicycle"]($bbox);
            way(r)($bbox)->.routes;
            (.paths; .routes;);
        """.trimIndent()

        MapLayerType.HIKING_TRAILS -> """
            rel["route"~"^(hiking|foot|walking)${'$'}"]($bbox);
            way(r)($bbox);
        """.trimIndent()

        // Chodniki (footway) tylko te z gruntową nawierzchnią lub oznaczone jako szlak (sac_scale),
        // więc zwykłe chodniki miejskie odpadają.
        MapLayerType.FOREST_PATHS -> """
            way["highway"~"^(path|track|bridleway)${'$'}"]["footway"!~"^(sidewalk|crossing|link|traffic_island)${'$'}"]["access"!~"^(private|no)${'$'}"]($bbox)->.p;
            way["highway"="footway"]["footway"!~"^(sidewalk|crossing|link|traffic_island)${'$'}"]["access"!~"^(private|no)${'$'}"]["surface"~"^(ground|dirt|earth|gravel|fine_gravel|compacted|grass|sand|mud|pebblestone|unpaved|wood)${'$'}"]($bbox)->.f;
            way["highway"="footway"]["footway"!~"^(sidewalk|crossing|link|traffic_island)${'$'}"]["access"!~"^(private|no)${'$'}"]["sac_scale"]($bbox)->.h;
            (.p; .f; .h;);
        """.trimIndent()
    }
    return "[out:json][timeout:60];\n$body\nout skel geom qt 15000;"
}
