package com.trailify.feature.map

import androidx.compose.foundation.background
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SmallFloatingActionButton
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
import com.trailify.core.domain.model.RecordingStatus
import com.trailify.core.domain.recording.distanceMeters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.koin.compose.viewmodel.koinViewModel
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource

// Podkłady mapy (domyślna mapa OpenStreetMap albo satelita z Geoportalu): patrz MapStyles.kt

// Na start mapa jest wycentrowana na Lublinie.
private val START_POSITION = CameraPosition.Builder()
    .target(LatLng(51.2465, 22.5684))
    .zoom(12.0)
    .build()

private fun sourceId(type: MapLayerType) = "trailify-source-${type.name.lowercase()}"
private fun layerId(type: MapLayerType) = "trailify-layer-${type.name.lowercase()}"

private fun haloId(type: MapLayerType) = "trailify-halo-${type.name.lowercase()}"

/**
 * Dodaje do stylu mapy puste źródła danych i warstwy linii dla wszystkich warstw tematycznych.
 * Na satelicie pod liniami leży jasna obwódka (pod wszystkimi liniami naraz, żeby nie zasłaniała sąsiednich).
 */
private fun addThematicLayers(style: Style, satellite: Boolean) {
    // Od końca, żeby ścieżki rowerowe (pierwsze na liście) były rysowane na wierzchu.
    val types = MapLayerType.entries.reversed()
    types.forEach { type ->
        val source = GeoJsonSource(sourceId(type))
        source.setGeoJson(EMPTY_GEOJSON)
        style.addSource(source)
    }
    if (satellite) {
        types.forEach { type ->
            style.addLayer(
                LineLayer(haloId(type), sourceId(type)).withProperties(
                    PropertyFactory.lineColor("#FFFFFF"),
                    PropertyFactory.lineWidth(type.lineWidth(satellite = true) + 3f),
                    PropertyFactory.lineOpacity(0.75f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                ),
            )
        }
    }
    types.forEach { type ->
        val dash = type.dash()
        val layer = LineLayer(layerId(type), sourceId(type)).withProperties(
            PropertyFactory.lineColor(type.color(satellite)),
            PropertyFactory.lineWidth(type.lineWidth(satellite)),
            PropertyFactory.lineOpacity(if (satellite) 1f else 0.9f),
            // przy kreskowaniu końce okrągłe wydłużałyby kreski i zlewały je w linię ciągłą
            PropertyFactory.lineCap(if (dash == null) Property.LINE_CAP_ROUND else Property.LINE_CAP_BUTT),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        )
        if (dash != null) layer.setProperties(PropertyFactory.lineDasharray(dash))
        style.addLayer(layer)
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

private const val POI_MESSAGE_MS = 4_000L

// Kolor ciekawych punktów w menu Warstwy: taki sam jak niezebrane punkty na mapie.
private const val POI_MENU_COLOR = "#F9A825"

// Tyle dp wokół dotknięcia szukamy ciekawego punktu (palec jest większy niż kropka).
private const val POI_TOUCH_DP = 24

// Po wybraniu punktu kamera przybliża się co najmniej do tego poziomu.
private const val POI_FOCUS_ZOOM = 16.0
private const val POI_FOCUS_MS = 600

/** Co dotknięto na warstwie ciekawych punktów. */
private sealed interface PoiHit {
    data class Single(val id: Long) : PoiHit

    /** Kółko z liczbą punktów w danym miejscu. */
    data class Cluster(val latitude: Double, val longitude: Double) : PoiHit
}

/** Pinezka albo kółko pod dotknięciem (null, gdy nic tam nie ma). */
private fun MapLibreMap.poiAt(point: LatLng, density: Float): PoiHit? {
    val screen = projection.toScreenLocation(point)
    val radius = POI_TOUCH_DP * density
    val area = RectF(screen.x - radius, screen.y - radius, screen.x + radius, screen.y + radius)
    val feature = queryRenderedFeatures(area, POI_LAYER, POI_CLUSTER_LAYER).firstOrNull() ?: return null
    return runCatching {
        if (feature.getStringProperty("kind") == "cluster") {
            PoiHit.Cluster(feature.getNumberProperty("lat").toDouble(), feature.getNumberProperty("lng").toDouble())
        } else {
            PoiHit.Single(feature.getNumberProperty("id").toLong())
        }
    }.getOrNull()
}

// Kamera jest "na użytkowniku", gdy jej środek jest bliżej niż tyle metrów od jego pozycji.
private const val CENTERED_METERS = 30.0


/**
 * Zakładka START: mapa z warstwami (W1), niebieską kropką z pozycją użytkownika,
 * planowaniem trasy po punktach (W7-W8) i nagrywaniem aktywności (W10-W15) w panelu na dole.
 */
@Composable
fun MapScreen(
    onOpenSummary: () -> Unit,
    onProposePoi: (latitude: Double, longitude: Double) -> Unit,
    panelVisible: Boolean = true,
    modifier: Modifier = Modifier,
    viewModel: MapViewModel = koinViewModel(),
    poiViewModel: MapPoiViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val mapView = rememberMapView()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val recording by viewModel.recordingState.collectAsStateWithLifecycle()
    val userLocation by viewModel.userLocation.collectAsStateWithLifecycle()
    val plan by viewModel.plan.collectAsStateWithLifecycle()
    val navigation by viewModel.navigation.collectAsStateWithLifecycle()
    val poiState by poiViewModel.ui.collectAsStateWithLifecycle()
    // miejsce przytrzymane palcem: pytamy, czy zaproponować tam ciekawy punkt
    var proposeAt by remember { mutableStateOf<DomainLatLng?>(null) }
    // przybliżenie mapy (do grupowania punktów w kółka)
    var mapZoom by remember { mutableStateOf(START_POSITION.zoom) }
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
        poiViewModel.refresh()
        onStopOrDispose { }
    }
    LifecycleStartEffect(hasLocationPermission) {
        if (hasLocationPermission) viewModel.setWatching(true)
        // w parze z setWatching(true) powyżej (źródło pozycji liczy ekrany, które go używają)
        onStopOrDispose { if (hasLocationPermission) viewModel.setWatching(false) }
    }
    // Przy pierwszym wejściu na mapę pytamy o lokalizację (raz, bez nagabywania).
    LaunchedEffect(Unit) {
        if (!hasLocationPermission && !viewModel.askedForLocation) {
            viewModel.askedForLocation = true
            permissionLauncher.launch(LOCATION_PERMISSIONS)
        }
    }

    // Ładowanie stylu mapy: na starcie i po zmianie stylu w menu. Warstwy dodajemy do każdego nowego stylu,
    // a efekty poniżej (zależne od "style") same wypełniają je danymi.
    val styleOption = MapStyleChoice.current
    LaunchedEffect(map, styleOption) {
        val googleMap = map ?: return@LaunchedEffect
        style = null
        styleOption.load(googleMap) { loadedStyle ->
            addThematicLayers(loadedStyle, satellite = styleOption == MapStyleOption.SATELLITE)
            addPoiLayers(loadedStyle, context.resources.displayMetrics.density)
            addPlanLayers(loadedStyle)
            addRecordingLayers(loadedStyle)
            style = loadedStyle
            viewModel.onCameraIdle(googleMap.visibleBox(), googleMap.cameraPosition.zoom)
            poiViewModel.onCameraIdle(googleMap.visibleBox())
            mapZoom = googleMap.cameraPosition.zoom
        }
    }

    // Ciekawe punkty na mapie (kolor zależy od zebrania, wybrany jest większy).
    // Pinezki z ikoną zebrania, większe w zasięgu; przy oddaleniu grupują się w kółka z liczbą.
    val shownPois = if (poiState.enabled) poiState.pois else emptyList()
    val poiInRange = remember(userLocation, shownPois, poiState.collectedIds) {
        poiInRangeIds(userLocation, shownPois, poiState.collectedIds)
    }
    LaunchedEffect(style, shownPois, poiState.collectedIds, poiState.selectedId, poiInRange, mapZoom) {
        val loadedStyle = style ?: return@LaunchedEffect
        val density = context.resources.displayMetrics.density
        val result = withContext(Dispatchers.Default) {
            poisGeoJson(shownPois, poiState.collectedIds, poiState.selectedId, poiInRange, mapZoom)
        }
        // ikony kółek muszą być w stylu, zanim dane się do nich odwołają
        result.clusterCounts.forEach { ensureClusterImage(loadedStyle, it, density) }
        loadedStyle.getSourceAs<GeoJsonSource>(POI_SOURCE)?.setGeoJson(result.json)
    }
    // Wybrany punkt: kamera przenosi się do niego (okno z informacjami jest na środku ekranu).
    LaunchedEffect(map, poiState.selectedId) {
        val googleMap = map ?: return@LaunchedEffect
        val poi = poiState.selected ?: return@LaunchedEffect
        viewModel.following = false
        val zoom = maxOf(googleMap.cameraPosition.zoom, POI_FOCUS_ZOOM)
        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(poi.latitude, poi.longitude), zoom), POI_FOCUS_MS)
    }
    LaunchedEffect(poiState.message) {
        if (poiState.message != null) {
            delay(POI_MESSAGE_MS)
            poiViewModel.dismissMessage()
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

    // Pomarańczowa linia z nagrywanej trasy.
    val points = recording.points
    // klucz to sama lista: punkty odtworzone po utracie GPS potrafią zmienić linię bez zmiany liczby punktów
    LaunchedEffect(style, points) {
        val loadedStyle = style ?: return@LaunchedEffect
        val json = withContext(Dispatchers.Default) { trackJson(points) }
        loadedStyle.getSourceAs<GeoJsonSource>(TRACK_SOURCE)?.setGeoJson(json)
    }

    // Fioletowa linia zaplanowanej trasy; kropki punktów tylko w trybie planowania.
    val planRoute = plan.route
    val planWaypoints = plan.waypoints
    val planEditing = plan.editing
    LaunchedEffect(style, planRoute) {
        val loadedStyle = style ?: return@LaunchedEffect
        val json = withContext(Dispatchers.Default) { planLineJson(planRoute?.geometry.orEmpty()) }
        loadedStyle.getSourceAs<GeoJsonSource>(PLAN_SOURCE)?.setGeoJson(json)
    }
    LaunchedEffect(style, planWaypoints, planEditing) {
        val loadedStyle = style ?: return@LaunchedEffect
        val json = planPointsJson(if (planEditing) planWaypoints else emptyList())
        loadedStyle.getSourceAs<GeoJsonSource>(PLAN_POINTS_SOURCE)?.setGeoJson(json)
    }
    // Wybrana trasa społeczności: rodzaj aktywności z trasy i kamera na jej całość (raz na trasę).
    val followedRouteId = plan.routeId
    val followedType = plan.routeType
    LaunchedEffect(followedRouteId) {
        if (followedType != null) selectedType = followedType
    }
    LaunchedEffect(map, followedRouteId, planRoute) {
        val googleMap = map ?: return@LaunchedEffect
        val geometry = planRoute?.geometry ?: return@LaunchedEffect
        if (followedRouteId == null || viewModel.fittedRouteId == followedRouteId || geometry.size < 2) return@LaunchedEffect
        viewModel.fittedRouteId = followedRouteId
        viewModel.following = false
        runCatching {
            val bounds = LatLngBounds.Builder().includes(geometry.map { LatLng(it.latitude, it.longitude) }).build()
            googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 120))
        }
    }
    // Nagrywanie ruszyło (dopiero wtedy, bo start może się nie udać): koniec edycji planu.
    LaunchedEffect(recording.isActive) {
        if (recording.isActive) viewModel.finishEditing()
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
        onTypeSelected = {
            selectedType = it
            viewModel.onPlanTypeChanged(it)
        },
        onStart = ::onStartClicked,
        onPlan = { viewModel.startPlanning(selectedType) },
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

    val planActions = PlanActions(
        onTypeSelected = actions.onTypeSelected,
        onUndo = viewModel::undoWaypoint,
        onClear = viewModel::clearWaypoints,
        onClose = viewModel::clearPlan,
        onStart = ::onStartClicked,
        onOpenSettings = actions.onOpenSettings,
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
                            poiViewModel.onCameraIdle(googleMap.visibleBox())
                            mapZoom = googleMap.cameraPosition.zoom
                        }
                        // przesunięcie mapy palcem wyłącza podążanie kamery za pozycją
                        googleMap.addOnCameraMoveStartedListener { reason ->
                            if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                                viewModel.following = false
                            }
                        }
                        // w trybie planowania dotknięcie mapy dodaje punkt trasy, poza nim wybiera ciekawy punkt
                        googleMap.addOnMapClickListener { point ->
                            if (viewModel.plan.value.editing) {
                                viewModel.addWaypoint(DomainLatLng(point.latitude, point.longitude))
                                true
                            } else {
                                when (val hit = googleMap.poiAt(point, context.resources.displayMetrics.density)) {
                                    is PoiHit.Single -> {
                                        poiViewModel.select(hit.id)
                                        true
                                    }
                                    // kółko z liczbą: przybliżamy, aż punkty się rozdzielą
                                    is PoiHit.Cluster -> {
                                        val zoom = maxOf(googleMap.cameraPosition.zoom + 2.0, POI_CLUSTER_MAX_ZOOM)
                                        googleMap.animateCamera(
                                            CameraUpdateFactory.newLatLngZoom(LatLng(hit.latitude, hit.longitude), zoom),
                                        )
                                        true
                                    }
                                    null -> false
                                }
                            }
                        }
                        // przytrzymanie palca: propozycja nowego ciekawego punktu w tym miejscu
                        googleMap.addOnMapLongClickListener { point ->
                            if (viewModel.plan.value.editing) {
                                false
                            } else {
                                proposeAt = DomainLatLng(point.latitude, point.longitude)
                                true
                            }
                        }
                        // kompas (widać go tylko po obróceniu mapy) z lewej, bo z prawej są przyciski sterujące
                        val margin = (12 * context.resources.displayMetrics.density).toInt()
                        googleMap.uiSettings.compassGravity = Gravity.TOP or Gravity.START
                        googleMap.uiSettings.setCompassMargins(margin, margin, margin, margin)
                        // styl ładuje efekt wyżej, gdy tylko mapa jest gotowa
                        map = googleMap
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Przyciski sterujące mapą razem, w jednej kolumnie przy prawej krawędzi.
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            var menuOpen by remember { mutableStateOf(false) }
            Box {
                // Wypełniony na kolor motywu, gdy jakaś warstwa jest włączona.
                SmallFloatingActionButton(
                    onClick = { menuOpen = true },
                    containerColor = if (state.enabled.isEmpty()) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primary,
                    contentColor = if (state.enabled.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(Icons.Filled.Layers, contentDescription = "Warstwy i styl mapy")
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
                                            .background(Color(android.graphics.Color.parseColor(type.color(styleOption == MapStyleOption.SATELLITE))), CircleShape),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(type.label())
                                }
                            },
                            // Menu zostaje otwarte, żeby można było zaznaczyć kilka warstw naraz.
                            onClick = { viewModel.toggleLayer(type) },
                        )
                    }
                    DropdownMenuItem(
                        leadingIcon = { Checkbox(checked = poiState.enabled, onCheckedChange = null) },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(12.dp).background(Color(android.graphics.Color.parseColor(POI_MENU_COLOR)), CircleShape))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Ciekawe punkty")
                            }
                        },
                        onClick = poiViewModel::toggleEnabled,
                    )
                    HorizontalDivider()
                    Text(
                        "Podkład",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                    MapStyleOption.entries.forEach { option ->
                        DropdownMenuItem(
                            leadingIcon = { RadioButton(selected = option == styleOption, onClick = null) },
                            text = { Text(option.label) },
                            onClick = { MapStyleChoice.current = option },
                        )
                    }
                }
            }
            // Pierwsze dotknięcie centruje na użytkowniku, drugie (gdy już jest wycentrowane) ustawia północ u góry.
            SmallFloatingActionButton(
                onClick = {
                    if (!hasLocationPermission) {
                        permissionLauncher.launch(LOCATION_PERMISSIONS)
                    } else {
                        viewModel.following = true
                        val location = userLocation
                        val googleMap = map
                        if (location != null && googleMap != null) {
                            val user = LatLng(location.latitude, location.longitude)
                            val camera = googleMap.cameraPosition
                            val centered = camera.target?.let { it.distanceTo(user) < CENTERED_METERS } == true
                            val update = if (centered) {
                                CameraUpdateFactory.newCameraPosition(
                                    CameraPosition.Builder().target(user).zoom(camera.zoom).bearing(0.0).tilt(0.0).build(),
                                )
                            } else {
                                CameraUpdateFactory.newLatLngZoom(user, maxOf(camera.zoom, FOLLOW_ZOOM))
                            }
                            googleMap.animateCamera(update)
                        }
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(Icons.Filled.MyLocation, contentDescription = "Moja pozycja")
            }
        }

        // Pasek postępu i komunikaty o warstwach u góry, z miejscem na przyciski z prawej i kompas z lewej.
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(start = 64.dp, end = 68.dp, top = 12.dp),
        ) {
            if (state.loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
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

        // Panel przylega do dolnego paska i wychodzi z przycisku START (ten sam kolor, bez odstępu).
        // Przycisk START w pasku go chowa i pokazuje (wtedy widać napis o źródle mapy w lewym dolnym rogu),
        // ale w trakcie nagrywania i podczas przechodzenia trasy panel zostaje, bo są w nim sterowanie i statystyki.
        val showPanel = panelVisible || recording.status != RecordingStatus.IDLE || plan.routeId != null
        // Płynne wsuwanie i wysuwanie: dymek zjeżdża w dół za pasek i blednie (po schowaniu wypada z kompozycji).
        val panelProgress by animateFloatAsState(
            targetValue = if (showPanel) 1f else 0f,
            animationSpec = tween(durationMillis = 250),
            label = "panel",
        )
        if (panelProgress > 0f) Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = panelProgress
                    translationY = (1f - panelProgress) * size.height
                },
        ) {
            val panelModifier = Modifier
            if (plan.editing && recording.status == RecordingStatus.IDLE) {
                PlanPanel(
                    plan = plan,
                    selectedType = selectedType,
                    startError = startError(recording, permissionDenied),
                    permissionDenied = permissionDenied,
                    actions = planActions,
                    modifier = panelModifier,
                )
            } else if (plan.routeId != null && recording.status == RecordingStatus.IDLE) {
                FollowPanel(
                    plan = plan,
                    startError = startError(recording, permissionDenied),
                    permissionDenied = permissionDenied,
                    actions = planActions,
                    modifier = panelModifier,
                )
            } else {
                RecordingPanel(
                    state = recording,
                    selectedType = selectedType,
                    permissionDenied = permissionDenied,
                    actions = actions,
                    navigation = navigation,
                    modifier = panelModifier,
                )
            }
        }

        // Komunikat o zebranym punkcie.
        poiState.message?.let { text ->
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 64.dp, start = 64.dp, end = 68.dp),
            ) {
                Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
            }
        }
    }

    poiState.selected?.let { poi ->
        val meters = userLocation?.let { distanceMeters(it, DomainLatLng(poi.latitude, poi.longitude)) }
        PoiDialog(
            poi = poi,
            meters = meters,
            collected = poi.id in poiState.collectedIds,
            onCollect = { poiViewModel.collect(poi.id) },
            onDismiss = { poiViewModel.select(null) },
        )
    }

    proposeAt?.let { place ->
        AlertDialog(
            onDismissRequest = { proposeAt = null },
            title = { Text("Zaproponować punkt w tym miejscu?") },
            text = { Text("Otworzymy formularz z zaznaczonym miejscem. Punkt pojawi się na mapie po zatwierdzeniu przez administratora.") },
            confirmButton = {
                TextButton(onClick = {
                    proposeAt = null
                    onProposePoi(place.latitude, place.longitude)
                }) { Text("Zaproponuj") }
            },
            dismissButton = { TextButton(onClick = { proposeAt = null }) { Text("Anuluj") } },
        )
    }
}
