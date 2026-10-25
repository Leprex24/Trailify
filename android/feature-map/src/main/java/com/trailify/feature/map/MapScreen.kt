package com.trailify.feature.map

import androidx.compose.foundation.background
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trailify.core.domain.model.ActivityType
import com.trailify.core.domain.model.BoundingBox
import com.trailify.core.domain.model.LatLng as DomainLatLng
import com.trailify.core.domain.model.MapLayerType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.viewmodel.koinViewModel
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
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

private val LOCATION_PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

private fun hasFineLocation(context: android.content.Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

private const val FOLLOW_ZOOM = 15.0

/**
 * Zakładka START: mapa z warstwami (W1), niebieską kropką z pozycją użytkownika
 * i nagrywaniem aktywności (W10-W15) w panelu na dole. Planowanie trasy dojdzie w tygodniu 5.
 */
@Composable
fun MapScreen(
    onOpenSummary: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MapViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val mapView = rememberMapView()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val recording by viewModel.recordingState.collectAsStateWithLifecycle()
    val userLocation by viewModel.userLocation.collectAsStateWithLifecycle()
    var style by remember { mutableStateOf<Style?>(null) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }

    var selectedType by rememberSaveable { mutableStateOf(ActivityType.WALKING) }
    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    var hasLocationPermission by remember { mutableStateOf(hasFineLocation(context)) }
    var pendingStart by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasLocationPermission = hasFineLocation(context)
        if (pendingStart) {
            pendingStart = false
            if (hasLocationPermission) {
                permissionDenied = false
                viewModel.startRecording(selectedType)
            } else {
                permissionDenied = true
            }
        }
    }

    // Pozycja jest śledzona tylko, gdy mapa jest na ekranie (oszczędza baterię).
    // Zgodę sprawdzamy przy każdym powrocie do aplikacji (mogła zostać przyznana w ustawieniach).
    LifecycleStartEffect(Unit) {
        hasLocationPermission = hasFineLocation(context)
        onStopOrDispose { }
    }
    LifecycleStartEffect(hasLocationPermission) {
        if (hasLocationPermission) viewModel.setWatching(true)
        onStopOrDispose { viewModel.setWatching(false) }
    }
    // Przy pierwszym wejściu na mapę pytamy o lokalizację (raz, bez nagabywania).
    LaunchedEffect(Unit) {
        if (!hasLocationPermission && !viewModel.askedForLocation) {
            viewModel.askedForLocation = true
            permissionLauncher.launch(LOCATION_PERMISSIONS)
        }
    }

    // Za każdym razem, gdy zmieni się lista pobranych linii, odświeżamy źródła na mapie.
    val lines = state.lines
    LaunchedEffect(style, lines) {
        val loadedStyle = style ?: return@LaunchedEffect
        MapLayerType.entries.forEach { type ->
            val json = withContext(Dispatchers.Default) { toGeoJson(lines[type].orEmpty()) }
            loadedStyle.getSourceAs<GeoJsonSource>(sourceId(type))?.setGeoJson(json)
        }
    }

    // Czerwona linia z nagrywanej trasy.
    val points = recording.points
    LaunchedEffect(style, points.size) {
        val loadedStyle = style ?: return@LaunchedEffect
        val json = withContext(Dispatchers.Default) { trackJson(points) }
        loadedStyle.getSourceAs<GeoJsonSource>(TRACK_SOURCE)?.setGeoJson(json)
    }

    // Niebieska kropka + kamera: raz wycentruj na użytkowniku, w trakcie nagrywania podążaj za nim.
    val recordingActive = recording.isActive
    LaunchedEffect(style, map, userLocation, recordingActive) {
        val loadedStyle = style ?: return@LaunchedEffect
        val googleMap = map ?: return@LaunchedEffect
        loadedStyle.getSourceAs<GeoJsonSource>(POSITION_SOURCE)?.setGeoJson(positionJson(userLocation))
        val location = userLocation ?: return@LaunchedEffect
        val target = LatLng(location.latitude, location.longitude)
        if (!viewModel.centeredOnUser) {
            viewModel.centeredOnUser = true
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(target, FOLLOW_ZOOM))
        } else if (viewModel.following && recordingActive) {
            googleMap.animateCamera(CameraUpdateFactory.newLatLng(target))
        }
    }

    fun onStartClicked() {
        val missing = buildList {
            if (!hasLocationPermission) addAll(LOCATION_PERMISSIONS)
            // Android 13+: bez tego nie zobaczysz powiadomienia z licznikiem (samo nagrywanie działa)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !viewModel.askedForNotifications &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            // Android 10+: krokomierz wymaga zgody "aktywność fizyczna" (rower nie liczy kroków)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && selectedType != ActivityType.CYCLING &&
                !viewModel.askedForActivityRecognition &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.ACTIVITY_RECOGNITION)
            }
        }
        if (missing.isEmpty()) {
            permissionDenied = false
            viewModel.startRecording(selectedType)
        } else {
            if (Manifest.permission.POST_NOTIFICATIONS in missing) viewModel.askedForNotifications = true
            if (Manifest.permission.ACTIVITY_RECOGNITION in missing) viewModel.askedForActivityRecognition = true
            pendingStart = true
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    val actions = RecordingActions(
        onTypeSelected = { selectedType = it },
        onStart = ::onStartClicked,
        onPause = viewModel::pauseRecording,
        onResume = viewModel::resumeRecording,
        onStop = {
            viewModel.stopRecording()
            onOpenSummary()
        },
        onDiscard = viewModel::discardRecording,
        onOpenSummary = onOpenSummary,
        onOpenSettings = {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
            )
        },
    )

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = {
                mapView.also { view ->
                    view.getMapAsync { googleMap ->
                        // po powrocie z innej zakładki wracamy w to samo miejsce, nie do Lublina
                        val savedTarget = viewModel.lastCameraTarget
                        googleMap.cameraPosition = if (savedTarget != null) {
                            CameraPosition.Builder()
                                .target(LatLng(savedTarget.latitude, savedTarget.longitude))
                                .zoom(viewModel.lastCameraZoom)
                                .build()
                        } else {
                            START_POSITION
                        }
                        googleMap.addOnCameraIdleListener {
                            googleMap.cameraPosition.target?.let {
                                viewModel.lastCameraTarget = DomainLatLng(it.latitude, it.longitude)
                            }
                            viewModel.lastCameraZoom = googleMap.cameraPosition.zoom
                            viewModel.onCameraIdle(googleMap.visibleBox(), googleMap.cameraPosition.zoom)
                        }
                        // przesunięcie mapy palcem wyłącza podążanie kamery za pozycją
                        googleMap.addOnCameraMoveStartedListener { reason ->
                            if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                                viewModel.following = false
                            }
                        }
                        googleMap.setStyle(STYLE_URL) { loadedStyle ->
                            addThematicLayers(loadedStyle)
                            addRecordingLayers(loadedStyle)
                            style = loadedStyle
                            map = googleMap
                            viewModel.onCameraIdle(googleMap.visibleBox(), googleMap.cameraPosition.zoom)
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

        FilledTonalButton(
            onClick = {
                if (!hasLocationPermission) {
                    permissionLauncher.launch(LOCATION_PERMISSIONS)
                } else {
                    viewModel.following = true
                    val location = userLocation
                    val googleMap = map
                    if (location != null && googleMap != null) {
                        val zoom = maxOf(googleMap.cameraPosition.zoom, FOLLOW_ZOOM)
                        googleMap.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(LatLng(location.latitude, location.longitude), zoom),
                        )
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text("Moja pozycja")
        }

        RecordingPanel(
            state = recording,
            selectedType = selectedType,
            permissionDenied = permissionDenied,
            actions = actions,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(12.dp),
        )
    }
}
