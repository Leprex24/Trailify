package com.trailify.feature.map

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.trailify.core.domain.model.Poi
import com.trailify.core.domain.poi.COLLECT_RADIUS_METERS
import com.trailify.core.ui.PoiCardDialog
import com.trailify.core.ui.formatDistance

/**
 * Okno ciekawego punktu na mapie. [meters] to odległość od użytkownika (null bez GPS).
 * Przycisk "Zbierz punkt" działa w promieniu 50 m: to ręczne zebranie, gdyby automatyczne nie zadziałało.
 */
@Composable
internal fun PoiDialog(
    poi: Poi,
    meters: Double?,
    collected: Boolean,
    onCollect: () -> Unit,
    onDismiss: () -> Unit,
) {
    PoiCardDialog(poi = poi, onDismiss = onDismiss) {
        when {
            collected -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    "Zebrany punkt",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            else -> {
                val inRange = meters != null && meters <= COLLECT_RADIUS_METERS
                Button(onClick = onCollect, enabled = inRange, modifier = Modifier.fillMaxWidth()) {
                    Text("Zbierz punkt")
                }
                if (!inRange) {
                    Text(
                        if (meters != null) {
                            "Jesteś ${formatDistance(meters)} stąd. Podejdź na ${COLLECT_RADIUS_METERS.toInt()} m, a punkt zbierze się sam."
                        } else {
                            "Włącz lokalizację, żeby zbierać punkty."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
