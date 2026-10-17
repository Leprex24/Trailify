package com.trailify.feature.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
 * Profil (W23-W25): dane, statystyki, zebrane punkty, wylogowanie.
 * TODO (Mikołaj): edycja profilu, zebrane punkty (PoiRepository), panel admina dla roli ADMIN.
 */
@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    when (val s = state) {
        is UiState.Loading -> LoadingBox(modifier)
        is UiState.Error -> ErrorBox(s.message, onRetry = viewModel::load, modifier = modifier)
        is UiState.Success -> Column(modifier = modifier.padding(16.dp)) {
            Text(s.data.displayName, style = MaterialTheme.typography.headlineSmall)
            Text(s.data.email, modifier = Modifier.padding(top = 4.dp))
            Text(
                text = "Przebyty dystans: ${formatDistance(s.data.stats.totalDistance)}",
                modifier = Modifier.padding(top = 16.dp),
            )
            Text("Aktywności: ${s.data.stats.activityCount}")
            Text("Trasy: ${s.data.stats.routeCount}")
            Text("Zebrane punkty: ${s.data.stats.collectedPoiCount}")
            Button(onClick = viewModel::signOut, modifier = Modifier.padding(top = 24.dp)) {
                Text("Wyloguj")
            }
        }
    }
}
