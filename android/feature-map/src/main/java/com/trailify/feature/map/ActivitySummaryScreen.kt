package com.trailify.feature.map

import android.graphics.RectF
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trailify.core.domain.model.ActivitySummary
import com.trailify.core.domain.model.ActivityType
import com.trailify.core.domain.model.LatLng as DomainLatLng
import com.trailify.core.domain.model.RecordingState
import com.trailify.core.domain.model.TrackPoint
import com.trailify.core.domain.recording.CandidateKind
import com.trailify.core.domain.recording.GapInfo
import com.trailify.core.domain.recording.GapRules
import com.trailify.core.domain.recording.averageSpeedMps
import com.trailify.core.domain.recording.paceMinPerKm
import com.trailify.core.domain.recording.reconstructedMeters
import com.trailify.core.domain.recording.trackLengthMeters
import com.trailify.core.ui.formatClock
import com.trailify.core.ui.formatDistanceLive
import com.trailify.core.ui.formatPace
import com.trailify.core.ui.formatSpeed
import com.trailify.core.ui.label
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.viewmodel.koinViewModel
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.GeoJsonSource
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
private val DATE_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", Locale.forLanguageTag("pl-PL")).withZone(ZoneId.systemDefault())

/** Podsumowanie zakończonego nagrania: cała trasa na mapie, statystyki, Zapisz / Odrzuć. */
@Composable
fun ActivitySummaryScreen(
    onDone: () -> Unit,
    onPublish: (ActivitySummary) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ActivitySummaryViewModel = koinViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var confirmDiscard by remember { mutableStateOf(false) }

    LaunchedEffect(ui.discarded) {
        if (ui.discarded) onDone()
    }

    val recording = ui.recording
    if (recording == null) {
        Column(
            modifier = modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Brak nagrania do podsumowania.")
            TextButton(onClick = onDone) { Text("Wróć") }
        }
        return
    }

    // Edytor luki GPS jest tylko przed zapisem i tylko dla luki, która nadal jest w nagraniu.
    val editor = ui.editor
    val editingInfo = if (editor != null && ui.saved == null) recording.gaps.firstOrNull { it.id == editor.gapId } else null
    val previewLine = remember(editor, editingInfo) { previewLineOf(editor, editingInfo) }
    val focus = remember(editor?.gapId, editingInfo != null) {
        editingInfo?.let { listOf(it.gap.from.toLatLng(), it.gap.to.toLatLng()) }.orEmpty()
    }

    Column(modifier = modifier.fillMaxSize()) {
        SummaryMap(
            points = recording.points,
            gaps = recording.gaps,
            previewLine = previewLine,
            previewPoints = if (editor != null && editor.manual) editor.waypoints else emptyList(),
            focus = focus,
            addsPoints = editor != null && editor.manual && editingInfo != null,
            onMapTap = viewModel::addWaypoint,
            onGapTap = viewModel::editGap,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )

        if (editor != null && editingInfo != null) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GapEditorPanel(
                    info = editingInfo,
                    editor = editor,
                    onPreview = viewModel::previewCandidate,
                    onApply = viewModel::applyPreview,
                    onStartManual = viewModel::startManual,
                    onCancelManual = viewModel::cancelManual,
                    onUndo = viewModel::undoWaypoint,
                    onConfirm = viewModel::confirmManual,
                    onClose = viewModel::closeEditor,
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = if (ui.saved != null) "Zapisano: ${recording.type.label()}" else "Podsumowanie: ${recording.type.label()}",
                    style = MaterialTheme.typography.titleLarge,
                )
                recording.startedAt?.let { Text(DATE_FORMAT.format(it), style = MaterialTheme.typography.bodySmall) }
                SummaryStats(recording)
                val reconstructed = reconstructedMeters(recording.points)
                if (reconstructed > 0) {
                    Text(
                        "Linią przerywaną zaznaczono fragmenty odtworzone po utracie sygnału GPS " +
                            "(łącznie około ${formatDistanceLive(reconstructed)}).",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (ui.saved == null && recording.gaps.isNotEmpty()) {
                    GapList(recording.gaps, onEdit = viewModel::editGap)
                }

                val saved = ui.saved
                when {
                    saved != null -> {
                        Text("Aktywność została zapisana na Twoim koncie.", style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = { onPublish(saved.summary) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Opublikuj trasę")
                        }
                        OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Gotowe") }
                    }
                    else -> {
                        if (!ui.canSave) {
                            Text(
                                "Za mało punktów GPS, żeby zapisać trasę (potrzebne są co najmniej dwa). Możesz tylko odrzucić nagranie.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        ui.error?.let {
                            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            Text(
                                "Nagranie pozostaje zapisane na Twoim telefonie. Spróbuj ponownie.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Button(
                            onClick = viewModel::save,
                            enabled = ui.canSave && !ui.saving,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (ui.saving) {
                                CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp).size(16.dp), strokeWidth = 2.dp)
                            }
                            Text(if (ui.error != null) "Spróbuj ponownie" else "Zapisz aktywność")
                        }
                        OutlinedButton(
                            onClick = { confirmDiscard = true },
                            enabled = !ui.saving,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Odrzuć") }
                    }
                }
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Odrzucić nagranie?") },
            text = { Text("Trasa i statystyki zostaną usunięte z telefonu. Tej operacji nie można cofnąć.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    viewModel.discard()
                }) { Text("Odrzuć") }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Anuluj") } },
        )
    }
}

@Composable
private fun SummaryStats(state: RecordingState) {
    val speed = averageSpeedMps(state.distanceMeters, state.elapsedSeconds)
    val cycling = state.type == ActivityType.CYCLING
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        SummaryStat("Czas", formatClock(state.elapsedSeconds))
        SummaryStat("Dystans", formatDistanceLive(state.distanceMeters))
        if (cycling) {
            SummaryStat("Śr. prędkość", formatSpeed(speed))
        } else {
            SummaryStat("Śr. tempo", formatPace(paceMinPerKm(speed)))
            SummaryStat("Kroki", state.steps?.toString() ?: "-")
        }
    }
}

@Composable
private fun SummaryStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

/** Lista fragmentów trasy odtworzonych po utracie GPS, każdy z przyciskiem poprawiania. */
@Composable
private fun GapList(gaps: List<GapInfo>, onEdit: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Fragmenty bez GPS", style = MaterialTheme.typography.titleMedium)
        Text(
            "Możesz poprawić przebieg każdego z nich: dotknij przerywanej linii na mapie albo wybierz „Zmień”.",
            style = MaterialTheme.typography.bodySmall,
        )
        gaps.forEachIndexed { index, info ->
            val length = trackLengthMeters(listOf(info.gap.from) + info.currentPoints + info.gap.to)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${index + 1}. od ${TIME_FORMAT.format(info.gap.from.recordedAt)}, około ${formatMeters(length)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = { onEdit(info.id) }) { Text("Zmień") }
            }
        }
    }
}

/** Wybór wersji przebiegu trasy w jednej luce albo ręczne zaznaczenie punktów na mapie. */
@Composable
private fun GapEditorPanel(
    info: GapInfo,
    editor: GapEditorState,
    onPreview: (Int) -> Unit,
    onApply: () -> Unit,
    onStartManual: () -> Unit,
    onCancelManual: () -> Unit,
    onUndo: () -> Unit,
    onConfirm: () -> Unit,
    onClose: () -> Unit,
) {
    Text(
        "Fragment bez GPS, ${TIME_FORMAT.format(info.gap.from.recordedAt)}–${TIME_FORMAT.format(info.gap.to.recordedAt)}",
        style = MaterialTheme.typography.titleMedium,
    )
    if (editor.manual) {
        Text(
            "Dotykaj mapy po kolei w miejscach, przez które przechodziłeś. Trasa między punktami zostanie poprowadzona " +
                "po drogach i ścieżkach.",
            style = MaterialTheme.typography.bodySmall,
        )
        Text("Zaznaczone punkty: ${editor.waypoints.size}/${GapRules.MAX_MANUAL_POINTS}", style = MaterialTheme.typography.bodyMedium)
        editor.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = onUndo,
                enabled = editor.waypoints.isNotEmpty() && !editor.busy,
                modifier = Modifier.weight(1f),
            ) { Text("Cofnij punkt") }
            Button(
                onClick = onConfirm,
                enabled = editor.waypoints.isNotEmpty() && !editor.busy,
                modifier = Modifier.weight(1f),
            ) {
                if (editor.busy) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp).size(16.dp), strokeWidth = 2.dp)
                }
                Text("Zatwierdź")
            }
        }
        TextButton(onClick = onCancelManual, enabled = !editor.busy, modifier = Modifier.fillMaxWidth()) { Text("Anuluj") }
    } else {
        Text(
            "Wybierz, którędy najpewniej szedłeś. Podgląd wybranej wersji jest na mapie na niebiesko.",
            style = MaterialTheme.typography.bodySmall,
        )
        info.candidates.forEachIndexed { index, candidate ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onPreview(index) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = editor.previewIndex == index, onClick = { onPreview(index) })
                Column {
                    val inserted = if (info.selected == index) " (wstawiona)" else ""
                    Text(candidateLabel(candidate.kind) + inserted, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${formatMeters(candidate.lengthMeters)}, wiarygodność ${(candidate.probability * 100).roundToInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        if (info.isManual) {
            Text("Obecnie wstawiona jest trasa zaznaczona ręcznie.", style = MaterialTheme.typography.bodySmall)
        }
        Button(
            onClick = onApply,
            enabled = editor.previewIndex != null && editor.previewIndex != info.selected,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Użyj wybranej trasy") }
        OutlinedButton(onClick = onStartManual, modifier = Modifier.fillMaxWidth()) { Text("Narysuj ręcznie") }
        TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Zamknij") }
    }
}

private fun candidateLabel(kind: CandidateKind): String = when (kind) {
    CandidateKind.STRAIGHT -> "Linia prosta"
    CandidateKind.SHORTEST_ROUTE -> "Najkrótsza trasa po ścieżkach"
    CandidateKind.DETOUR -> "Trasa z objazdem"
}

private fun formatMeters(meters: Double): String =
    if (meters < 1000) "${meters.roundToInt()} m" else String.format(Locale.forLanguageTag("pl-PL"), "%.2f km", meters / 1000)

/** Linia pokazywana na mapie na niebiesko: wybrana propozycja albo trasa zaznaczana ręcznie (koniec luki dołączony). */
private fun previewLineOf(editor: GapEditorState?, info: GapInfo?): List<DomainLatLng> {
    if (editor == null || info == null) return emptyList()
    val start = info.gap.from.toLatLng()
    val end = info.gap.to.toLatLng()
    if (editor.manual) {
        return if (editor.waypoints.isEmpty()) emptyList() else listOf(start) + editor.waypoints + end
    }
    val candidate = editor.previewIndex?.let { info.candidates.getOrNull(it) } ?: return emptyList()
    return listOf(start) + candidate.points.map { it.toLatLng() } + end
}

private fun TrackPoint.toLatLng() = DomainLatLng(latitude, longitude)

/**
 * Mapa z całą przebytą trasą dopasowaną do ekranu. Dotknięcie przerywanej linii otwiera poprawianie tej luki,
 * a w trybie ręcznym dotknięcie dodaje punkt trasy.
 */
@Composable
private fun SummaryMap(
    points: List<TrackPoint>,
    gaps: List<GapInfo>,
    previewLine: List<DomainLatLng>,
    previewPoints: List<DomainLatLng>,
    focus: List<DomainLatLng>,
    addsPoints: Boolean,
    onMapTap: (DomainLatLng) -> Unit,
    onGapTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mapView = rememberMapView()
    var style by remember { mutableStateOf<Style?>(null) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    // Całą trasę dopasowujemy do ekranu tylko raz: późniejsze zmiany nie mogą odsuwać kamery od poprawianej luki.
    var fitted by remember { mutableStateOf(false) }
    val adds by rememberUpdatedState(addsPoints)
    val mapTap by rememberUpdatedState(onMapTap)
    val gapTap by rememberUpdatedState(onGapTap)

    LaunchedEffect(style, map, points, gaps) {
        val loadedStyle = style ?: return@LaunchedEffect
        val googleMap = map ?: return@LaunchedEffect
        val json = withContext(Dispatchers.Default) { trackJson(points, gaps) }
        loadedStyle.getSourceAs<GeoJsonSource>(TRACK_SOURCE)?.setGeoJson(json)
        if (!fitted) {
            if (points.size >= 2) {
                val bounds = LatLngBounds.Builder().includes(points.map { LatLng(it.latitude, it.longitude) }).build()
                googleMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 80))
                fitted = true
            } else if (points.size == 1) {
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(points[0].latitude, points[0].longitude), 16.0))
                fitted = true
            }
        }
    }

    LaunchedEffect(style, previewLine, previewPoints) {
        val loadedStyle = style ?: return@LaunchedEffect
        loadedStyle.getSourceAs<GeoJsonSource>(GAP_PREVIEW_SOURCE)?.setGeoJson(gapLineJson(previewLine))
        loadedStyle.getSourceAs<GeoJsonSource>(GAP_POINTS_SOURCE)?.setGeoJson(gapPointsJson(previewPoints))
    }

    LaunchedEffect(map, focus) {
        val googleMap = map ?: return@LaunchedEffect
        if (focus.size < 2) return@LaunchedEffect
        runCatching {
            val bounds = LatLngBounds.Builder().includes(focus.map { LatLng(it.latitude, it.longitude) }).build()
            googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 120))
        }
    }

    AndroidView(
        factory = {
            mapView.also { view ->
                view.getMapAsync { googleMap ->
                    googleMap.setStyle(MapStyleChoice.current.url) { loadedStyle ->
                        addRecordingLayers(loadedStyle)
                        addGapEditLayers(loadedStyle)
                        style = loadedStyle
                        map = googleMap
                    }
                    googleMap.addOnMapClickListener { tapped ->
                        if (adds) {
                            mapTap(DomainLatLng(tapped.latitude, tapped.longitude))
                            true
                        } else {
                            val pixel = googleMap.projection.toScreenLocation(tapped)
                            val box = RectF(pixel.x - TAP_RADIUS_PX, pixel.y - TAP_RADIUS_PX, pixel.x + TAP_RADIUS_PX, pixel.y + TAP_RADIUS_PX)
                            val gapId = googleMap.queryRenderedFeatures(box, RECONSTRUCTED_LAYER)
                                .firstNotNullOfOrNull { it.getNumberProperty("gap")?.toInt() }
                            if (gapId != null) {
                                gapTap(gapId)
                                true
                            } else {
                                false
                            }
                        }
                    }
                }
            }
        },
        modifier = modifier,
    )
}

private const val TAP_RADIUS_PX = 32f
