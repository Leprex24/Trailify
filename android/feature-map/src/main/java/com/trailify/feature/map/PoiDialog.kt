package com.trailify.feature.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.trailify.core.domain.model.Poi
import com.trailify.core.domain.poi.COLLECT_RADIUS_METERS
import com.trailify.core.ui.formatDistance
import com.trailify.core.ui.label

private val AMBER = Color(0xFFF9A825)

/**
 * Okno ciekawego punktu na środku ekranu (jak przystanek w Pokémon GO): zdjęcie, nazwa, opis i zbieranie.
 * Zamyka się dotknięciem poza oknem albo przyciskiem pod nim. [meters] to odległość od użytkownika (null bez GPS).
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
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                // dotknięcie poza oknem zamyka je (bez efektu fali)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
                .padding(24.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                // okno przechwytuje dotknięcia, żeby nie zamykać się po dotknięciu w treść
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
            ) {
                Column {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxWidth().height(200.dp).background(AMBER),
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = Color.White, modifier = Modifier.size(64.dp))
                        poi.photoUrl?.let {
                            AsyncImage(
                                model = it,
                                contentDescription = poi.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(20.dp),
                    ) {
                        Text(poi.name, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                        Text(
                            poi.category.label(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                        )
                        poi.description?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                        }
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
            }
            // przycisk wyjścia pod oknem
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 6.dp,
                modifier = Modifier.padding(top = 20.dp).clip(CircleShape),
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Zamknij")
                }
            }
        }
    }
}
