package com.trailify.feature.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.trailify.core.domain.model.ActivityType
import com.trailify.core.ui.formatDistance
import com.trailify.core.ui.formatDuration
import com.trailify.core.ui.label

internal class PlanActions(
    val onTypeSelected: (ActivityType) -> Unit,
    val onUndo: () -> Unit,
    val onClear: () -> Unit,
    val onClose: () -> Unit,
    val onStart: () -> Unit,
    val onOpenSettings: () -> Unit,
)

/** Panel na dole mapy w trybie planowania: rodzaj aktywności, dystans i czas, cofnij / wyczyść / zamknij, Start. */
@Composable
internal fun PlanPanel(
    plan: PlanUiState,
    selectedType: ActivityType,
    startError: String?,
    permissionDenied: Boolean,
    actions: PlanActions,
    modifier: Modifier = Modifier,
) {
    PanelSheet(modifier) {
            ActivityTypeSelector(selected = selectedType, onSelected = actions.onTypeSelected)

            val route = plan.route
            when {
                plan.waypoints.isEmpty() -> Hint("Dotknij mapy, aby dodać punkt startowy.")
                plan.error != null -> Text(
                    plan.error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                route != null -> Text(
                    "${formatDistance(route.distance)} · ${formatDuration(route.durationSeconds)}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                plan.waypoints.size < 2 -> Hint("Dotknij mapy, aby dodać kolejny punkt.")
                else -> Hint("Wyznaczam trasę…")
            }
            if (plan.loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            if (startError != null) {
                Text(startError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                if (permissionDenied) {
                    OutlinedButton(onClick = actions.onOpenSettings) { Text("Otwórz ustawienia") }
                }
            }

            // Tylko ikony (opis dla czytników ekranu w contentDescription): napisy łamały się w wąskich przyciskach.
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = actions.onUndo,
                    enabled = plan.waypoints.isNotEmpty(),
                    modifier = Modifier.weight(1f).height(PanelButtonHeight),
                ) { Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Cofnij punkt") }
                OutlinedButton(
                    onClick = actions.onClear,
                    enabled = plan.waypoints.isNotEmpty(),
                    modifier = Modifier.weight(1f).height(PanelButtonHeight),
                ) { Icon(Icons.Filled.Delete, contentDescription = "Wyczyść trasę") }
                OutlinedButton(onClick = actions.onClose, modifier = Modifier.weight(1f).height(PanelButtonHeight)) {
                    Icon(Icons.Filled.Close, contentDescription = "Zamknij planowanie")
                }
            }
            Button(
                onClick = actions.onStart,
                enabled = route != null,
                modifier = Modifier.fillMaxWidth().height(PanelButtonHeight),
            ) { StartLabel("Start trasy") }
    }
}

/** Panel na dole mapy po wybraniu opublikowanej trasy: nazwa, dystans i Start (albo rezygnacja). */
@Composable
internal fun FollowPanel(
    plan: PlanUiState,
    startError: String?,
    permissionDenied: Boolean,
    actions: PlanActions,
    modifier: Modifier = Modifier,
) {
    PanelSheet(modifier) {
            Text(plan.routeName.orEmpty(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            val route = plan.route
            if (route != null) {
                val duration = route.durationSeconds.takeIf { it > 0 }?.let { " · ${formatDuration(it)}" }.orEmpty()
                Text("${plan.routeType?.label().orEmpty()} · ${formatDistance(route.distance)}$duration")
            }
            if (startError != null) {
                Text(startError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                if (permissionDenied) {
                    OutlinedButton(onClick = actions.onOpenSettings) { Text("Otwórz ustawienia") }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = actions.onClose, modifier = Modifier.weight(1f).height(PanelButtonHeight)) { Text("Anuluj") }
                Button(onClick = actions.onStart, modifier = Modifier.weight(1f).height(PanelButtonHeight)) { StartLabel("Start") }
            }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium)
}
