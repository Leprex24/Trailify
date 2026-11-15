package com.trailify.core.ui

import android.graphics.RectF
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import com.trailify.core.domain.model.LatLng as DomainLatLng

// Wspólne mapy dla ekranów Odkrywaj, Społeczność i Profil. Ekran START (feature-map) ma własną mapę.

/** Te same podkłady co na ekranie START. */
const val MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

/** Domyślny widok: Lublin (dane testowe, GraphHopper i punkty POI obejmują województwo lubelskie). */
val DEFAULT_MAP_CENTER = DomainLatLng(51.2465, 22.5684)

private const val PRIMARY_COLOR = "#2E7D32"
private const val SELECTED_COLOR = "#E65100"
private const val MUTED_COLOR = "#9E9E9E"
private const val USER_COLOR = "#1E88E5"
private const val TAP_RADIUS_PX = 24f

private const val MARKERS_SOURCE = "trailify-markers"
private const val MARKERS_LAYER = "trailify-markers-layer"
private const val LINE_SOURCE = "trailify-line"

/** Wygląd punktu na mapie. */
enum class MarkerKind { PRIMARY, MUTED, USER }

/** Punkt na mapie; [id] wraca w onSelect po dotknięciu. */
data class MapMarker(val id: Long, val position: DomainLatLng, val kind: MarkerKind = MarkerKind.PRIMARY)

/**
 * Mapa z punktami (np. starty tras, ciekawe punkty). Dotknięcie punktu wywołuje [onSelect] z jego id,
 * dotknięcie pustego miejsca: [onSelect](null) oraz [onMapTap] z pozycją (np. wybór miejsca w formularzu).
 * Kamera obejmuje wszystkie punkty, gdy zmieni się ich lista (nie przy zaznaczaniu).
 */
@Composable
fun MarkersMap(
    markers: List<MapMarker>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    onMapTap: ((DomainLatLng) -> Unit)? = null,
    fitToMarkers: Boolean = true,
) {
    val mapView = rememberMapView()
    var style by remember { mutableStateOf<Style?>(null) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var fittedTo by remember { mutableStateOf<List<Long>?>(null) }
    val select by rememberUpdatedState(onSelect)
    val tap by rememberUpdatedState(onMapTap)

    LaunchedEffect(style, markers, selectedId) {
        style?.getSourceAs<GeoJsonSource>(MARKERS_SOURCE)?.setGeoJson(markersJson(markers, selectedId))
    }

    LaunchedEffect(map, markers) {
        val googleMap = map ?: return@LaunchedEffect
        val ids = markers.map { it.id }
        if (fittedTo == ids || (!fitToMarkers && fittedTo != null)) return@LaunchedEffect
        val points = markers.map { LatLng(it.position.latitude, it.position.longitude) }
        when {
            points.size >= 2 -> googleMap.moveCamera(
                CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(points).build(), 120),
            )
            points.size == 1 -> googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(points[0], 15.0))
            else -> googleMap.moveCamera(
                CameraUpdateFactory.newLatLngZoom(LatLng(DEFAULT_MAP_CENTER.latitude, DEFAULT_MAP_CENTER.longitude), 11.0),
            )
        }
        fittedTo = ids
    }

    AndroidView(
        factory = {
            mapView.also { view ->
                view.getMapAsync { googleMap ->
                    googleMap.setStyle(MAP_STYLE_URL) { loadedStyle ->
                        loadedStyle.addSource(GeoJsonSource(MARKERS_SOURCE))
                        loadedStyle.addLayer(
                            CircleLayer(MARKERS_LAYER, MARKERS_SOURCE).withProperties(
                                PropertyFactory.circleRadius(
                                    Expression.switchCase(Expression.get("selected"), Expression.literal(11f), Expression.literal(8f)),
                                ),
                                PropertyFactory.circleColor(Expression.get("color")),
                                PropertyFactory.circleStrokeColor("#FFFFFF"),
                                PropertyFactory.circleStrokeWidth(2f),
                            ),
                        )
                        style = loadedStyle
                        map = googleMap
                    }
                    googleMap.addOnMapClickListener { tapped ->
                        val pixel = googleMap.projection.toScreenLocation(tapped)
                        val box = RectF(pixel.x - TAP_RADIUS_PX, pixel.y - TAP_RADIUS_PX, pixel.x + TAP_RADIUS_PX, pixel.y + TAP_RADIUS_PX)
                        val id = googleMap.queryRenderedFeatures(box, MARKERS_LAYER)
                            .firstNotNullOfOrNull { f -> f.getNumberProperty("id")?.toLong()?.takeIf { f.getBooleanProperty("selectable") } }
                        select(id)
                        if (id == null) tap?.invoke(DomainLatLng(tapped.latitude, tapped.longitude))
                        true
                    }
                }
            }
        },
        modifier = modifier,
    )
}

/** Podgląd przebiegu (trasa, aktywność) bez przesuwania; przewijanie strony nad mapą działa normalnie. */
@Composable
fun LinePreviewMap(geometry: List<DomainLatLng>, modifier: Modifier = Modifier) {
    val mapView = rememberMapView()
    var style by remember { mutableStateOf<Style?>(null) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }

    LaunchedEffect(style, map, geometry) {
        val loadedStyle = style ?: return@LaunchedEffect
        val googleMap = map ?: return@LaunchedEffect
        loadedStyle.getSourceAs<GeoJsonSource>(LINE_SOURCE)?.setGeoJson(lineJson(geometry))
        val points = geometry.map { LatLng(it.latitude, it.longitude) }
        when {
            points.size >= 2 -> googleMap.moveCamera(
                CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(points).build(), 60),
            )
            points.size == 1 -> googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(points[0], 14.0))
            else -> googleMap.moveCamera(
                CameraUpdateFactory.newLatLngZoom(LatLng(DEFAULT_MAP_CENTER.latitude, DEFAULT_MAP_CENTER.longitude), 11.0),
            )
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = {
                mapView.also { view ->
                    view.getMapAsync { googleMap ->
                        googleMap.uiSettings.setAllGesturesEnabled(false)
                        googleMap.setStyle(MAP_STYLE_URL) { loadedStyle ->
                            loadedStyle.addSource(GeoJsonSource(LINE_SOURCE))
                            loadedStyle.addLayer(
                                LineLayer("$LINE_SOURCE-layer", LINE_SOURCE).withProperties(
                                    PropertyFactory.lineColor(PRIMARY_COLOR),
                                    PropertyFactory.lineWidth(5f),
                                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                                ),
                            )
                            style = loadedStyle
                            map = googleMap
                        }
                    }
                }
            },
            modifier = Modifier.matchParentSize(),
        )
        // Przezroczysta warstwa nad mapą: przesunięcie palcem po podglądzie przewija stronę,
        // zamiast utknąć w MapView (który przechwytuje dotyk nawet z wyłączonymi gestami).
        Box(Modifier.matchParentSize().pointerInput(Unit) {})
    }
}

/**
 * MapView z przekazywaniem zdarzeń cyklu życia ekranu (start, wznowienie, pauza...).
 * Bez tego mapa się nie rysuje albo wycieka pamięć. Ten sam wzorzec co w feature-map.
 */
@Composable
fun rememberMapView(): MapView {
    val context = LocalContext.current
    val mapView = remember {
        MapLibre.getInstance(context.applicationContext)
        MapView(context).apply { onCreate(null) }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    DisposableEffect(lifecycle, mapView) {
        var started = false
        var resumed = false
        var destroyed = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> { mapView.onStart(); started = true }
                Lifecycle.Event.ON_RESUME -> { mapView.onResume(); resumed = true }
                Lifecycle.Event.ON_PAUSE -> { mapView.onPause(); resumed = false }
                Lifecycle.Event.ON_STOP -> { mapView.onStop(); started = false }
                Lifecycle.Event.ON_DESTROY -> { mapView.onDestroy(); destroyed = true }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            // ekran znika (np. zmiana zakładki), a aktywność nadal żyje: sprzątamy ręcznie
            if (!destroyed) {
                if (resumed) mapView.onPause()
                if (started) mapView.onStop()
                mapView.onDestroy()
            }
        }
    }
    return mapView
}

/** Punkty jako GeoJSON (współrzędne w kolejności [długość, szerokość]). */
private fun markersJson(markers: List<MapMarker>, selectedId: Long?): String {
    val features = JSONArray()
    markers.forEach { marker ->
        val selected = marker.kind != MarkerKind.USER && marker.id == selectedId
        val color = when {
            selected -> SELECTED_COLOR
            marker.kind == MarkerKind.MUTED -> MUTED_COLOR
            marker.kind == MarkerKind.USER -> USER_COLOR
            else -> PRIMARY_COLOR
        }
        features.put(
            JSONObject()
                .put("type", "Feature")
                .put(
                    "geometry",
                    JSONObject().put("type", "Point")
                        .put("coordinates", JSONArray().put(marker.position.longitude).put(marker.position.latitude)),
                )
                .put(
                    "properties",
                    JSONObject().put("id", marker.id).put("selected", selected).put("color", color)
                        .put("selectable", marker.kind != MarkerKind.USER),
                ),
        )
    }
    return JSONObject().put("type", "FeatureCollection").put("features", features).toString()
}

private fun lineJson(geometry: List<DomainLatLng>): String {
    val coordinates = JSONArray()
    geometry.forEach { coordinates.put(JSONArray().put(it.longitude).put(it.latitude)) }
    val features = JSONArray()
    if (geometry.size >= 2) {
        features.put(
            JSONObject()
                .put("type", "Feature")
                .put("geometry", JSONObject().put("type", "LineString").put("coordinates", coordinates))
                .put("properties", JSONObject()),
        )
    }
    return JSONObject().put("type", "FeatureCollection").put("features", features).toString()
}
