package com.trailify.feature.map

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import com.trailify.core.ui.PlaceholderScreen

/**
 * Zakładka START: mapa z warstwami (W1), planowanie trasy (W5-W6), start nagrywania.
 * Na razie atrapa - mapa MapLibre pojawi się w tygodniu 27.10.
 */
@Composable
fun MapScreen(
    onStartRecording: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        PlaceholderScreen(
            title = "Mapa",
            description = "Tu będzie mapa (MapLibre) z warstwami ścieżek rowerowych i szlaków.",
            modifier = Modifier.weight(1f),
        )
        Button(onClick = onStartRecording, modifier = Modifier.padding(16.dp)) {
            Text("Rozpocznij nagrywanie (atrapa)")
        }
    }
}
