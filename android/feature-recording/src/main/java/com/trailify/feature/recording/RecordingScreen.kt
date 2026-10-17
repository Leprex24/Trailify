package com.trailify.feature.recording

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.trailify.core.ui.PlaceholderScreen

/** Ekran nagrywania aktywności (W10-W15): czas, tempo, kroki, GPS. */
@Composable
fun RecordingScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        PlaceholderScreen(
            title = "Nagrywanie",
            description = "Tu będzie licznik czasu, tempa i kroków oraz zapis punktów GPS.",
            modifier = Modifier.weight(1f),
        )
        Button(onClick = onClose, modifier = Modifier.padding(16.dp)) { Text("Zamknij") }
    }
}
