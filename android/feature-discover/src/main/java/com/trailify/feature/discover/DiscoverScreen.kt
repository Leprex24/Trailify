package com.trailify.feature.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trailify.core.ui.EmptyBox
import com.trailify.core.ui.ErrorBox
import com.trailify.core.ui.LoadingBox
import com.trailify.core.ui.RouteCard
import com.trailify.core.ui.UiState
import org.koin.compose.viewmodel.koinViewModel

/** Zakładka Odkrywaj: rekomendowane trasy, wyszukiwanie (W2-W4). */
@Composable
fun DiscoverScreen(
    onRouteClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DiscoverViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(modifier = modifier) {
        Text(
            text = "Odkrywaj",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
        )
        when (val s = state) {
            is UiState.Loading -> LoadingBox()
            is UiState.Error -> ErrorBox(message = s.message, onRetry = viewModel::load)
            is UiState.Success -> if (s.data.isEmpty()) {
                EmptyBox("Brak tras do wyświetlenia")
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(s.data, key = { it.id }) { route ->
                        RouteCard(route = route, onClick = { onRouteClick(route.id) })
                    }
                }
            }
        }
    }
}
