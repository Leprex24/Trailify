package com.trailify.feature.discover

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
import com.trailify.core.domain.model.RouteSummary
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

// Te same podkłady mapy co na ekranie START (feature-map).
private const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
private const val ROUTE_COLOR = "#2E7D32"
private const val SELECTED_COLOR = "#E65100"
private const val TAP_RADIUS_PX = 24f

private const val STARTS_SOURCE = "discover-starts"
private const val STARTS_LAYER = "discover-starts-layer"
private const val LINE_SOURCE = "details-line"

/** Domyślny widok: Lublin (dane testowe i GraphHopper obejmują województwo lubelskie). */
private val DEFAULT_CENTER = LatLng(51.2465, 22.5684)

/**
 * Mapa wyników Odkrywaj: punkt startowy każdej trasy. Dotknięcie punktu zaznacza trasę ([onSelect]),
 * dotknięcie pustego miejsca zdejmuje zaznaczenie.
 */
@Composable
internal fun RoutesMap(
    routes: List<RouteSummary>,
    selectedRouteId: Long?,
    onSelect: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mapView = rememberMapView()
    var style by remember { mutableStateOf<Style?>(null) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var fittedTo by remember { mutableStateOf<List<Long>?>(null) }
    val select by rememberUpdatedState(onSelect)

    LaunchedEffect(style, routes, selectedRouteId) {
        style?.getSourceAs<GeoJsonSource>(STARTS_SOURCE)?.setGeoJson(startsJson(routes, selectedRouteId))
    }

    // Kamera obejmuje wszystkie wyniki, ale tylko gdy zmieniła się ich lista (nie przy zaznaczaniu).
    LaunchedEffect(map, routes) {
        val googleMap = map ?: return@LaunchedEffect
        val ids = routes.map { it.id }
        if (fittedTo == ids) return@LaunchedEffect
        val points = routes.mapNotNull { it.startPoint }.map { LatLng(it.latitude, it.longitude) }
        when {
            points.size >= 2 -> googleMap.moveCamera(
                CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(points).build(), 120),
            )
            points.size == 1 -> googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(points[0], 13.0))
            else -> googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(DEFAULT_CENTER, 11.0))
        }
        fittedTo = ids
    }

    AndroidView(
        factory = {
            mapView.also { view ->
                view.getMapAsync { googleMap ->
                    googleMap.setStyle(STYLE_URL) { loadedStyle ->
                        loadedStyle.addSource(GeoJsonSource(STARTS_SOURCE))
                        loadedStyle.addLayer(
                            CircleLayer(STARTS_LAYER, STARTS_SOURCE).withProperties(
                                PropertyFactory.circleRadius(
                                    Expression.switchCase(Expression.get("selected"), Expression.literal(11f), Expression.literal(8f)),
                                ),
                                PropertyFactory.circleColor(
                                    Expression.switchCase(
                                        Expression.get("selected"),
                                        Expression.literal(SELECTED_COLOR),
                                        Expression.literal(ROUTE_COLOR),
                                    ),
                                ),
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
                        val id = googleMap.queryRenderedFeatures(box, STARTS_LAYER)
                            .firstNotNullOfOrNull { it.getNumberProperty("id")?.toLong() }
                        select(id)
                        true
                    }
                }
            }
        },
        modifier = modifier,
    )
}

/** Podgląd przebiegu trasy w jej szczegółach (bez przesuwania, tylko do oglądania). */
@Composable
internal fun RouteLineMap(geometry: List<DomainLatLng>, modifier: Modifier = Modifier) {
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
            else -> googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(DEFAULT_CENTER, 11.0))
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = {
                mapView.also { view ->
                    view.getMapAsync { googleMap ->
                        googleMap.uiSettings.setAllGesturesEnabled(false)
                        googleMap.setStyle(STYLE_URL) { loadedStyle ->
                            loadedStyle.addSource(GeoJsonSource(LINE_SOURCE))
                            loadedStyle.addLayer(
                                LineLayer("$LINE_SOURCE-layer", LINE_SOURCE).withProperties(
                                    PropertyFactory.lineColor(ROUTE_COLOR),
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

/** Punkty startowe tras jako GeoJSON (współrzędne w kolejności [długość, szerokość]). */
internal fun startsJson(routes: List<RouteSummary>, selectedRouteId: Long?): String {
    val features = JSONArray()
    routes.forEach { route ->
        val start = route.startPoint ?: return@forEach
        features.put(
            JSONObject()
                .put("type", "Feature")
                .put("geometry", JSONObject().put("type", "Point").put("coordinates", JSONArray().put(start.longitude).put(start.latitude)))
                .put("properties", JSONObject().put("id", route.id).put("selected", route.id == selectedRouteId)),
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

/**
 * MapView z przekazywaniem zdarzeń cyklu życia ekranu. Ten sam wzorzec co rememberMapView w feature-map
 * (moduły funkcji nie zależą od siebie, więc nie da się go stamtąd zaimportować).
 */
@Composable
private fun rememberMapView(): MapView {
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
