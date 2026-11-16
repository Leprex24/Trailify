package com.trailify.feature.map

import com.trailify.core.domain.model.BoundingBox
import com.trailify.core.domain.model.LatLng
import com.trailify.core.domain.model.Poi
import com.trailify.core.domain.poi.COLLECT_RADIUS_METERS
import com.trailify.core.domain.recording.distanceMeters
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow

// Serwer przyjmuje obszar do 1 stopnia (GET /pois?bbox=...). Szerszy widok to za duże oddalenie: punktów nie pokazujemy.
private const val SERVER_LIMIT_DEGREES = 1.0
internal const val POI_MAX_VIEW_DEGREES = 0.8

/** Zapas wokół widoku, żeby po lekkim przesunięciu mapy nie trzeba było znowu pobierać (ułamek szerokości widoku). */
private const val POI_PREFETCH_FRACTION = 0.25

internal fun BoundingBox.contains(other: BoundingBox) =
    other.minLat >= minLat && other.maxLat <= maxLat && other.minLng >= minLng && other.maxLng <= maxLng

internal fun BoundingBox.contains(latitude: Double, longitude: Double) =
    latitude in minLat..maxLat && longitude in minLng..maxLng

/**
 * Obszar do pobrania dla widocznego fragmentu mapy: widok z zapasem, ale nigdy szerszy niż limit serwera.
 * Zwraca null, gdy widok jest zbyt szeroki (mapa mocno oddalona).
 */
internal fun poiLoadBox(viewport: BoundingBox): BoundingBox? {
    val width = viewport.maxLng - viewport.minLng
    val height = viewport.maxLat - viewport.minLat
    if (width > POI_MAX_VIEW_DEGREES || height > POI_MAX_VIEW_DEGREES) return null
    val limit = SERVER_LIMIT_DEGREES - 0.02
    val padLng = minOf(width * POI_PREFETCH_FRACTION, (limit - width) / 2)
    val padLat = minOf(height * POI_PREFETCH_FRACTION, (limit - height) / 2)
    return BoundingBox(viewport.minLat - padLat, viewport.minLng - padLng, viewport.maxLat + padLat, viewport.maxLng + padLng)
}

/**
 * Łączy znane punkty z nowo pobranymi dla obszaru [box]: wszystko, co leżało w tym obszarze, zastępują świeże dane
 * (więc punkt usunięty na serwerze znika), a punkty spoza obszaru zostają.
 */
internal fun mergePois(known: List<Poi>, loaded: List<Poi>, box: BoundingBox): List<Poi> =
    (known.filterNot { box.contains(it.latitude, it.longitude) } + loaded).distinctBy { it.id }

/** Od tego przybliżenia punkty nie są już grupowane w kółka z liczbą. */
internal const val POI_CLUSTER_MAX_ZOOM = 14.0

/** Rozmiar komórki grupowania na ekranie (w dp): punkty bliżej siebie niż to zlewają się w jedno kółko. */
private const val CLUSTER_CELL_DP = 56.0

/** Największa liczba pokazywana w kółku; więcej to "99+". */
internal const val CLUSTER_LABEL_MAX = 99

private const val SIZE_NORMAL = 1.0
private const val SIZE_BIG = 1.3

internal const val POI_ICON_NEW = "poi-new"
internal const val POI_ICON_COLLECTED = "poi-collected"

internal fun clusterIconName(count: Int) = "poi-cluster-${count.coerceAtMost(CLUSTER_LABEL_MAX + 1)}"

internal fun clusterLabel(count: Int) = if (count > CLUSTER_LABEL_MAX) "$CLUSTER_LABEL_MAX+" else "$count"

/** Punkty niezebrane, które są w zasięgu zbierania (50 m): ich pinezki są większe. */
internal fun poiInRangeIds(position: LatLng?, pois: List<Poi>, collectedIds: Set<Long>): Set<Long> =
    if (position == null) {
        emptySet()
    } else {
        pois.filter {
            it.id !in collectedIds && distanceMeters(position, LatLng(it.latitude, it.longitude)) <= COLLECT_RADIUS_METERS
        }.map { it.id }.toSet()
    }

/** Jedna pinezka (1 punkt) albo kółko z liczbą (więcej punktów) w danym miejscu. */
internal class PoiGroup(val latitude: Double, val longitude: Double, val pois: List<Poi>)

/**
 * Grupuje punkty w siatkę o komórkach ok. [CLUSTER_CELL_DP] dp na ekranie przy przybliżeniu [zoom].
 * Z bliska (od [POI_CLUSTER_MAX_ZOOM]) każdy punkt jest osobno.
 */
internal fun clusterPois(pois: List<Poi>, zoom: Double): List<PoiGroup> {
    if (pois.isEmpty()) return emptyList()
    if (zoom >= POI_CLUSTER_MAX_ZOOM) return pois.map { PoiGroup(it.latitude, it.longitude, listOf(it)) }
    // MapLibre: świat ma 512 dp szerokości przy zoomie 0, a każdy zoom podwaja skalę
    val cellLng = 360.0 / (512.0 * 2.0.pow(zoom)) * CLUSTER_CELL_DP
    val cellLat = cellLng * cos(Math.toRadians(pois.map { it.latitude }.average()))
    return pois.groupBy { floor(it.longitude / cellLng).toLong() to floor(it.latitude / cellLat).toLong() }
        .values
        .map { group -> PoiGroup(group.map { it.latitude }.average(), group.map { it.longitude }.average(), group) }
}

/** [json] dla źródła mapy i [clusterCounts], dla których trzeba mieć ikony kółek. */
internal class PoiGeoJson(val json: String, val clusterCounts: Set<Int>)

/**
 * Punkty jako GeoJSON dla warstwy mapy. Pinezka ma ikonę zależną od zebrania (gwiazdka albo ptaszek) i jest większa,
 * gdy punkt jest wybrany albo w zasięgu. Wybrany punkt nigdy nie wchodzi do kółka z liczbą.
 */
internal fun poisGeoJson(
    pois: List<Poi>,
    collectedIds: Set<Long>,
    selectedId: Long?,
    inRangeIds: Set<Long>,
    zoom: Double,
): PoiGeoJson {
    val groups = clusterPois(pois.filter { it.id != selectedId }, zoom) +
        pois.filter { it.id == selectedId }.map { PoiGroup(it.latitude, it.longitude, listOf(it)) }
    val clusterCounts = mutableSetOf<Int>()
    val features = groups.joinToString(",") { group ->
        val point = """"geometry":{"type":"Point","coordinates":[${group.longitude},${group.latitude}]}"""
        if (group.pois.size == 1) {
            val poi = group.pois.single()
            val icon = if (poi.id in collectedIds) POI_ICON_COLLECTED else POI_ICON_NEW
            val size = if (poi.id == selectedId || poi.id in inRangeIds) SIZE_BIG else SIZE_NORMAL
            """{"type":"Feature","properties":{"kind":"poi","id":${poi.id},"icon":"$icon","size":$size},$point}"""
        } else {
            val count = group.pois.size
            clusterCounts += count.coerceAtMost(CLUSTER_LABEL_MAX + 1)
            """{"type":"Feature","properties":{"kind":"cluster","icon":"${clusterIconName(count)}",""" +
                """"lat":${group.latitude},"lng":${group.longitude}},$point}"""
        }
    }
    return PoiGeoJson("""{"type":"FeatureCollection","features":[$features]}""", clusterCounts)
}
