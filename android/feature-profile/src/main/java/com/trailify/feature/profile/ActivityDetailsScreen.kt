package com.trailify.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.trailify.core.domain.model.Activity
import com.trailify.core.domain.model.ActivitySummary
import com.trailify.core.domain.repository.ActivityRepository
import com.trailify.core.ui.ErrorBox
import com.trailify.core.ui.LinePreviewMap
import com.trailify.core.ui.LoadingBox
import com.trailify.core.ui.UiState
import com.trailify.core.ui.formatDistance
import com.trailify.core.ui.formatDuration
import com.trailify.core.ui.label
import com.trailify.core.ui.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import java.util.Locale

/** Jedna aktywność z historii (W25): przebieg na mapie i statystyki. */
class ActivityDetailsViewModel(private val activities: ActivityRepository) : ViewModel() {

    private val _state = MutableStateFlow<UiState<Activity>>(UiState.Loading)
    val state: StateFlow<UiState<Activity>> = _state.asStateFlow()

    fun load(activityId: Long) {
        viewModelScope.launch {
            _state.value = UiState.Loading
            _state.value = try {
                UiState.Success(activities.get(activityId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UiState.Error(e.toUserMessage())
            }
        }
    }
}

/** Szczegóły aktywności. Gdy aktywność nie jest jeszcze trasą, można ją od razu opublikować. */
@Composable
fun ActivityDetailsScreen(
    activityId: Long,
    onPublish: (ActivitySummary) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ActivityDetailsViewModel = koinViewModel(),
) {
    LaunchedEffect(activityId) { viewModel.load(activityId) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    when (val s = state) {
        is UiState.Loading -> LoadingBox(modifier)
        is UiState.Error -> ErrorBox(s.message, onRetry = { viewModel.load(activityId) }, modifier = modifier)
        is UiState.Success -> {
            val activity = s.data.summary
            Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                TextButton(onClick = onBack) { Text("← Wróć") }
                Text(activity.type.label(), style = MaterialTheme.typography.headlineSmall)
                Text(PROFILE_DATE_FORMAT.format(activity.startTime), style = MaterialTheme.typography.bodyMedium)
                if (s.data.geometry.size >= 2) {
                    LinePreviewMap(
                        geometry = s.data.geometry,
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(260.dp).clip(RoundedCornerShape(16.dp)),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.padding(top = 16.dp)) {
                    Stat("Dystans", formatDistance(activity.distance))
                    Stat("Czas", formatDuration(activity.durationSeconds))
                    Stat("Tempo", activity.avgPace?.let { formatPace(it) } ?: "-")
                    activity.stepCount?.let { Stat("Kroki", "$it") }
                }
                if (activity.routeId == null) {
                    Button(onClick = { onPublish(activity) }, modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
                        Text("Opublikuj jako trasę")
                    }
                } else {
                    Text(
                        "Ta aktywność to przejście opublikowanej trasy.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 24.dp),
                    )
                }
            }
        }
    }
}

/** 5.5 min/km -> "5:30 /km". */
internal fun formatPace(minPerKm: Double): String {
    val totalSeconds = (minPerKm * 60).toInt()
    return String.format(Locale.ROOT, "%d:%02d /km", totalSeconds / 60, totalSeconds % 60)
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
