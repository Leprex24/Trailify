package com.trailify.feature.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trailify.core.domain.model.ActivityType
import com.trailify.core.domain.model.RecordingError
import com.trailify.core.domain.model.RecordingState
import com.trailify.core.domain.model.RecordingStatus
import com.trailify.core.domain.recording.averageSpeedMps
import com.trailify.core.domain.recording.currentSpeedMps
import com.trailify.core.domain.recording.paceMinPerKm
import com.trailify.core.ui.formatClock
import com.trailify.core.ui.formatDistance
import com.trailify.core.ui.formatDistanceLive
import com.trailify.core.ui.formatPace
import com.trailify.core.ui.formatSpeed
import com.trailify.core.ui.label
import java.time.Instant

/** Akcje panelu nagrywania. */
internal class RecordingActions(
    val onTypeSelected: (ActivityType) -> Unit,
    val onStart: () -> Unit,
    val onPlan: () -> Unit,
    val onPause: () -> Unit,
    val onResume: () -> Unit,
    val onStop: () -> Unit,
    val onDiscard: () -> Unit,
    val onOpenSummary: () -> Unit,
    val onOpenSettings: () -> Unit,
)

/**
 * Panel na dole mapy START: przed nagrywaniem wybór rodzaju aktywności i Start,
 * w trakcie czas, dystans, pauza i koniec.
 */
@Composable
internal fun RecordingPanel(
    state: RecordingState,
    selectedType: ActivityType,
    permissionDenied: Boolean,
    actions: RecordingActions,
    modifier: Modifier = Modifier,
    navigation: NavigationUiState? = null,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        shadowElevation = 8.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (state.status) {
                RecordingStatus.IDLE -> IdlePanel(state, selectedType, permissionDenied, actions)
                RecordingStatus.RECORDING, RecordingStatus.PAUSED -> ActivePanel(state, navigation, actions)
                RecordingStatus.FINISHED -> FinishedPanel(state, actions)
            }
        }
    }
}

@Composable
private fun IdlePanel(
    state: RecordingState,
    selectedType: ActivityType,
    permissionDenied: Boolean,
    actions: RecordingActions,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
        ActivityType.entries.forEach { type ->
            FilterChip(
                selected = type == selectedType,
                onClick = { actions.onTypeSelected(type) },
                label = { Text(type.label()) },
            )
        }
    }
    val error = startError(state, permissionDenied)
    if (error != null) {
        Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        if (permissionDenied) {
            OutlinedButton(onClick = actions.onOpenSettings) { Text("Otwórz ustawienia") }
        }
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = actions.onPlan, modifier = Modifier.weight(1f)) { Text("Zaplanuj trasę") }
        Button(onClick = actions.onStart, modifier = Modifier.weight(1f)) { Text("Start") }
    }
}

/** Komunikat o błędzie startu nagrywania (brak zgody, awaria) albo null. Wspólny dla panelu nagrywania i planowania. */
internal fun startError(state: RecordingState, permissionDenied: Boolean): String? = when {
    permissionDenied -> "Do nagrywania trasy potrzebna jest dokładna lokalizacja. Zezwól na nią w ustawieniach aplikacji."
    state.error == RecordingError.START_FAILED -> "Nie udało się uruchomić nagrywania. Spróbuj ponownie."
    state.error == RecordingError.LOCATION_PERMISSION -> "Brak dostępu do lokalizacji. Sprawdź uprawnienia aplikacji."
    else -> null
}

@Composable
private fun ActivePanel(state: RecordingState, navigation: NavigationUiState?, actions: RecordingActions) {
    val paused = state.status == RecordingStatus.PAUSED
    Text(
        text = when {
            state.restored -> "Przywrócono nagranie po zamknięciu aplikacji. Wznów albo zakończ."
            paused -> "Wstrzymano"
            !state.hasFix -> "Szukam sygnału GPS… wyjdź na otwartą przestrzeń"
            else -> state.type.label()
        },
        style = MaterialTheme.typography.labelLarge,
    )
    if (navigation != null) NavigationInfo(navigation)
    MainStats(state)
    SecondaryStats(state, live = !paused)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = if (paused) actions.onResume else actions.onPause,
            modifier = Modifier.weight(1f),
        ) { Text(if (paused) "Wznów" else "Pauza") }
        Button(onClick = actions.onStop, modifier = Modifier.weight(1f)) { Text("Zakończ") }
    }
}

@Composable
private fun FinishedPanel(state: RecordingState, actions: RecordingActions) {
    Text("Zakończono: ${state.type.label()}", style = MaterialTheme.typography.titleMedium)
    MainStats(state)
    SecondaryStats(state, live = false)
    Button(onClick = actions.onOpenSummary, modifier = Modifier.fillMaxWidth()) { Text("Podsumowanie i zapis") }
    OutlinedButton(onClick = actions.onDiscard, modifier = Modifier.fillMaxWidth()) { Text("Odrzuć") }
}

/** Pozostały dystans do końca zaplanowanej trasy albo ostrzeżenie o zejściu z niej. */
@Composable
private fun NavigationInfo(navigation: NavigationUiState) {
    val warning = navigation.offRoute
    Text(
        text = when {
            navigation.arrived -> "Dotarłeś do końca trasy"
            warning -> "Zszedłeś z trasy (${formatDistance(navigation.offRouteMeters)} od niej)"
            else -> "Do końca trasy: ${formatDistance(navigation.remainingMeters)}"
        },
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = if (warning && !navigation.arrived) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
    )
}

/** Czas i dystans: największe liczby. */
@Composable
private fun MainStats(state: RecordingState) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        Stat(label = "Czas", value = formatClock(state.elapsedSeconds), size = 32)
        Stat(label = "Dystans", value = formatDistanceLive(state.distanceMeters), size = 32)
    }
}

/**
 * Tempo (bieg, spacer) albo prędkość (rower) i kroki. [live] = false pokazuje tylko wartości średnie
 * (na pauzie i po zakończeniu bieżące tempo nie ma sensu).
 */
@Composable
private fun SecondaryStats(state: RecordingState, live: Boolean) {
    val average = averageSpeedMps(state.distanceMeters, state.elapsedSeconds)
    val current = if (live) currentSpeedMps(state.points, Instant.now()) else null
    val cycling = state.type == ActivityType.CYCLING
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        if (cycling) {
            if (live) Stat(label = "Prędkość", value = formatSpeed(current))
            Stat(label = "Śr. prędkość", value = formatSpeed(average))
        } else {
            if (live) Stat(label = "Tempo", value = formatPace(paceMinPerKm(current)))
            Stat(label = "Śr. tempo", value = formatPace(paceMinPerKm(average)))
            Stat(label = "Kroki", value = state.steps?.toString() ?: "-")
        }
    }
}

@Composable
private fun Stat(label: String, value: String, size: Int = 20) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = size.sp, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}
