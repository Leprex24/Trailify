package com.trailify.feature.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trailify.core.domain.model.BoundingBox
import com.trailify.core.domain.model.MapLayerType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.viewmodel.koinViewModel
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource

// Bezpłatne kafelki wektorowe oparte na danych OpenStreetMap, bez klucza API.
private const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

// Na start mapa jest wycentrowana na Lublinie.
private val START_POSITION = CameraPosition.Builder()
    .target(LatLng(51.2465, 22.5684))
    .zoom(12.0)
    .build()

private fun sourceId(type: MapLayerType) = "trailify-source-${type.name.lowercase()}"
private fun layerId(type: MapLayerType) = "trailify-layer-${type.name.lowercase()}"

/** Dodaje do stylu mapy puste źródła danych i warstwy linii dla wszystkich warstw tematycznych. */
private fun addThematicLayers(style: Style) {
    // Od końca, żeby ścieżki rowerowe (pierwsze na liście) były rysowane na wierzchu.
    MapLayerType.entries.reversed().forEach { type ->
        val source = GeoJsonSource(sourceId(type))
        source.setGeoJson(EMPTY_GEOJSON)
        style.addSource(source)
        style.addLayer(
            LineLayer(layerId(type), sourceId(type)).withProperties(
                PropertyFactory.lineColor(type.color()),
                PropertyFactory.lineWidth(type.lineWidth()),
                PropertyFactory.lineOpacity(1f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            ),
        )
    }
}

private fun MapLibreMap.visibleBox(): BoundingBox {
    val bounds = projection.visibleRegion.latLngBounds
    return BoundingBox(
        minLat = bounds.latitudeSouth,
        minLng = bounds.longitudeWest,
        maxLat = bounds.latitudeNorth,
        maxLng = bounds.longitudeEast,
    )
}

/**
 * Zakładka START: mapa z warstwami (W1). Planowanie trasy dojdzie w tygodniu 5.
 */
@Composable
fun MapScreen(
    onStartRecording: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MapViewModel = koinViewModel(),
) {
    val mapView = rememberMapView()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var style by remember { mutableStateOf<Style?>(null) }

    // Za każdym razem, gdy zmieni się lista pobranych linii, odświeżamy źródła na mapie.
    val lines = state.lines
    LaunchedEffect(style, lines) {
        val loadedStyle = style ?: return@LaunchedEffect
        MapLayerType.entries.forEach { type ->
            val json = withContext(Dispatchers.Default) { toGeoJson(lines[type].orEmpty()) }
            loadedStyle.getSourceAs<GeoJsonSource>(sourceId(type))?.setGeoJson(json)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = {
                mapView.also { view ->
                    view.getMapAsync { map ->
                        map.cameraPosition = START_POSITION
                        map.addOnCameraIdleListener {
                            viewModel.onCameraIdle(map.visibleBox(), map.cameraPosition.zoom)
                        }
                        map.setStyle(STYLE_URL) { loadedStyle ->
                            addThematicLayers(loadedStyle)
                            style = loadedStyle
                            viewModel.onCameraIdle(map.visibleBox(), map.cameraPosition.zoom)
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            var menuOpen by remember { mutableStateOf(false) }
            Box {
                FilledTonalButton(onClick = { menuOpen = true }) {
                    Text(if (state.enabled.isEmpty()) "Warstwy" else "Warstwy (${state.enabled.size})")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    MapLayerType.entries.forEach { type ->
                        DropdownMenuItem(
                            leadingIcon = { Checkbox(checked = type in state.enabled, onCheckedChange = null) },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Kolorowa kropka: tak samo wygląda ta warstwa na mapie.
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .background(Color(android.graphics.Color.parseColor(type.color())), CircleShape),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(type.label())
                                }
                            },
                            // Menu zostaje otwarte, żeby można było zaznaczyć kilka warstw naraz.
                            onClick = { viewModel.toggleLayer(type) },
                        )
                    }
                }
            }
            if (state.loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            }
            val message = when {
                state.error != null -> state.error
                state.isRetrying -> "Problem z połączeniem z serwerem map, ponawiam…"
                state.hidden.isNotEmpty() ->
                    "Przybliż mapę, aby zobaczyć: " + state.hidden.joinToString(", ") { it.label() }
                else -> null
            }
            if (message != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
                        )
                        if (state.error != null) {
                            TextButton(onClick = viewModel::retry) { Text("Ponów") }
                        }
                    }
                }
            }
        }

        Button(
            onClick = onStartRecording,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        ) {
            Text("Rozpocznij nagrywanie (atrapa)")
        }
    }
}
