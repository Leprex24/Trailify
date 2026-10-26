package com.trailify.feature.map

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trailify.core.domain.model.ActivitySummary
import com.trailify.core.domain.model.ActivityType
import com.trailify.core.domain.model.RecordingState
import com.trailify.core.domain.model.TrackPoint
import com.trailify.core.domain.recording.averageSpeedMps
import com.trailify.core.domain.recording.paceMinPerKm
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

private const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
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

    Column(modifier = modifier.fillMaxSize()) {
        SummaryMap(points = recording.points, modifier = Modifier.weight(1f).fillMaxWidth())

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

/** Mapa z całą przebytą trasą dopasowaną do ekranu. */
@Composable
private fun SummaryMap(points: List<TrackPoint>, modifier: Modifier = Modifier) {
    val mapView = rememberMapView()
    var style by remember { mutableStateOf<Style?>(null) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }

    LaunchedEffect(style, map, points) {
        val loadedStyle = style ?: return@LaunchedEffect
        val googleMap = map ?: return@LaunchedEffect
        val json = withContext(Dispatchers.Default) { trackJson(points) }
        loadedStyle.getSourceAs<GeoJsonSource>(TRACK_SOURCE)?.setGeoJson(json)
        if (points.size >= 2) {
            val bounds = LatLngBounds.Builder().includes(points.map { LatLng(it.latitude, it.longitude) }).build()
            googleMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 80))
        } else if (points.size == 1) {
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(points[0].latitude, points[0].longitude), 16.0))
        }
    }

    AndroidView(
        factory = {
            mapView.also { view ->
                view.getMapAsync { googleMap ->
                    googleMap.setStyle(STYLE_URL) { loadedStyle ->
                        addRecordingLayers(loadedStyle)
                        style = loadedStyle
                        map = googleMap
                    }
                }
            }
        },
        modifier = modifier,
    )
}
