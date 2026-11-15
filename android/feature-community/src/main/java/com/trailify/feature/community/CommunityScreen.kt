package com.trailify.feature.community

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Snackbar
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.trailify.core.domain.model.LatLng
import com.trailify.core.domain.poi.COLLECT_RADIUS_METERS
import com.trailify.core.domain.poi.PoiDistance
import com.trailify.core.domain.poi.nearbyPois
import com.trailify.core.ui.EmptyBox
import com.trailify.core.ui.ErrorBox
import com.trailify.core.ui.LoadingBox
import com.trailify.core.ui.MapMarker
import com.trailify.core.ui.MarkerKind
import com.trailify.core.ui.MarkersMap
import com.trailify.core.ui.UiState
import com.trailify.core.ui.formatDistance
import com.trailify.feature.community.poi.CommunityView
import com.trailify.feature.community.poi.CommunityViewModel
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel

/** Id znacznika "Ty tu jesteś" (punkty POI mają id dodatnie). */
private const val USER_MARKER_ID = -1L
private const val MESSAGE_VISIBLE_MS = 4_000L

/**
 * Zakładka Społeczność (W4, W22-W24): ciekawe punkty w okolicy. Gdy użytkownik podejdzie na 50 m,
 * punkt zbiera się sam i trafia do kolekcji w profilu.
 */
@Composable
fun CommunityScreen(
    onProposePoi: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CommunityViewModel = koinViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        if (granted) {
            // ponowne włączenie, żeby GPS wystartował z nową zgodą (para false/true nie zmienia licznika)
            viewModel.setWatching(false)
            viewModel.setWatching(true)
        }
    }

    // GPS działa tylko, gdy zakładka jest widoczna; po powrocie odświeżamy punkty (np. po zatwierdzeniu przez admina)
    LifecycleStartEffect(Unit) {
        viewModel.setWatching(true)
        viewModel.load()
        onStopOrDispose { viewModel.setWatching(false) }
    }

    LaunchedEffect(ui.message) {
        if (ui.message != null) {
            delay(MESSAGE_VISIBLE_MS)
            viewModel.dismissMessage()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Text(
                "Społeczność",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
            )
            Text(
                "Podejdź do ciekawego punktu na ${COLLECT_RADIUS_METERS.toInt()} m, a trafi do Twojej kolekcji.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            if (!hasPermission) {
                OutlinedCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
                        Text("Włącz lokalizację, żeby zbierać punkty i widzieć odległość.", modifier = Modifier.weight(1f))
                        TextButton(onClick = { askPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION) }) { Text("Włącz") }
                    }
                }
            }
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                CommunityView.entries.forEachIndexed { index, view ->
                    SegmentedButton(
                        selected = ui.view == view,
                        onClick = { viewModel.onViewChange(view) },
                        shape = SegmentedButtonDefaults.itemShape(index, CommunityView.entries.size),
                    ) { Text(if (view == CommunityView.MAP) "Mapa" else "Lista") }
                }
            }

            when (val pois = ui.pois) {
                is UiState.Loading -> LoadingBox()
                is UiState.Error -> ErrorBox(pois.message, onRetry = viewModel::load)
                is UiState.Success -> {
                    val position = ui.position
                    // bez pozycji pokazujemy punkty bez odległości (NaN)
                    val withDistance = position?.let { nearbyPois(it, pois.data) }
                        ?: pois.data.map { PoiDistance(it, Double.NaN) }
                    when {
                        pois.data.isEmpty() -> EmptyBox("W okolicy nie ma jeszcze ciekawych punktów. Zaproponuj pierwszy!")
                        ui.view == CommunityView.LIST -> LazyColumn(
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(withDistance, key = { it.poi.id }) { item ->
                                PoiCard(item, collected = item.poi.id in ui.collectedIds, onCollect = { viewModel.collect(item.poi.id) })
                            }
                        }
                        else -> Box(Modifier.fillMaxSize()) {
                            MarkersMap(
                                markers = pois.data.map {
                                    MapMarker(
                                        it.id,
                                        LatLng(it.latitude, it.longitude),
                                        if (it.id in ui.collectedIds) MarkerKind.MUTED else MarkerKind.PRIMARY,
                                    )
                                } + listOfNotNull(position?.let { MapMarker(USER_MARKER_ID, it, MarkerKind.USER) }),
                                selectedId = ui.selectedPoiId,
                                onSelect = viewModel::onPoiSelected,
                                modifier = Modifier.fillMaxSize(),
                            )
                            withDistance.firstOrNull { it.poi.id == ui.selectedPoiId }?.let { item ->
                                PoiCard(
                                    item,
                                    collected = item.poi.id in ui.collectedIds,
                                    onCollect = { viewModel.collect(item.poi.id) },
                                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).padding(bottom = 72.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = onProposePoi,
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            text = { Text("Zaproponuj punkt") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
        ui.message?.let {
            Snackbar(modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).padding(bottom = 72.dp)) { Text(it) }
        }
    }
}

@Composable
private fun PoiCard(item: PoiDistance, collected: Boolean, onCollect: () -> Unit, modifier: Modifier = Modifier) {
    val poi = item.poi
    Card(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (poi.photoUrl != null) {
                    AsyncImage(poi.photoUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Icon(Icons.Default.Place, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                }
            }
            Column(Modifier.weight(1f)) {
                Text(poi.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                poi.description?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Text(
                        if (item.meters.isNaN()) "" else formatDistance(item.meters),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Spacer(Modifier.weight(1f))
                    when {
                        collected -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text("Zebrany", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                        }
                        item.inRange -> Button(onClick = onCollect) { Text("Zbierz") }
                    }
                }
            }
        }
    }
}
