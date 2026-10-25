package com.trailify.feature.discover

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trailify.core.ui.ErrorBox
import com.trailify.core.ui.LoadingBox
import com.trailify.core.ui.UiState
import com.trailify.core.ui.formatDistance
import org.koin.compose.viewmodel.koinViewModel

/**
 * Szczegóły trasy (W20-W22). Na razie sam opis.
 * Przycisk "Przejdź tę trasę" jest podpięty (mapa START prowadzi po trasie); wygląd do dopracowania.
 * TODO (Mikołaj): zdjęcia, recenzje (ReviewRepository).
 */
@Composable
fun RouteDetailsScreen(
    routeId: Long,
    onBack: () -> Unit,
    onFollowRoute: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RouteDetailsViewModel = koinViewModel(),
) {
    LaunchedEffect(routeId) { viewModel.load(routeId) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    when (val s = state) {
        is UiState.Loading -> LoadingBox(modifier)
        is UiState.Error -> ErrorBox(s.message, onRetry = { viewModel.load(routeId) }, modifier = modifier)
        is UiState.Success -> Column(modifier = modifier.padding(16.dp)) {
            Button(onClick = onBack) { Text("Wstecz") }
            Text(
                text = s.data.summary.name,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                text = formatDistance(s.data.summary.totalDistance),
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = s.data.description ?: "Brak opisu",
                modifier = Modifier.padding(top = 8.dp),
            )
            Button(
                onClick = {
                    viewModel.followRoute(s.data)
                    onFollowRoute()
                },
                modifier = Modifier.padding(top = 16.dp),
            ) { Text("Przejdź tę trasę") }
        }
    }
}
