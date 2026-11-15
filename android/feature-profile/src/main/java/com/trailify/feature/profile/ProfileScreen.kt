package com.trailify.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.trailify.core.domain.model.ActivitySummary
import com.trailify.core.domain.model.PoiCollectionEntry
import com.trailify.core.domain.model.Role
import com.trailify.core.ui.ErrorBox
import com.trailify.core.ui.LoadingBox
import com.trailify.core.ui.UiState
import com.trailify.core.ui.formatDistance
import com.trailify.core.ui.formatDuration
import com.trailify.core.ui.label
import org.koin.compose.viewmodel.koinViewModel
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal val PROFILE_DATE_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", Locale.forLanguageTag("pl-PL")).withZone(ZoneId.systemDefault())

/**
 * Profil (W23-W25): dane, statystyki, zebrane punkty, historia aktywności, wylogowanie.
 * Edycja konta jest na osobnym ekranie (AccountScreen); administrator widzi też przycisk do panelu.
 */
@Composable
fun ProfileScreen(
    onOpenAccount: () -> Unit,
    onOpenActivity: (Long) -> Unit,
    onOpenAdmin: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Za każdym wejściem na ekran, np. po powrocie z ustawień konta ze zmienioną nazwą.
    LaunchedEffect(Unit) { viewModel.load() }

    when (val profile = state.profile) {
        is UiState.Loading -> LoadingBox(modifier)
        is UiState.Error -> ErrorBox(profile.message, onRetry = viewModel::load, modifier = modifier)
        is UiState.Success -> LazyColumn(modifier = modifier, contentPadding = PaddingValues(16.dp)) {
            val me = profile.data
            item {
                Text(me.displayName, style = MaterialTheme.typography.headlineSmall)
                Text(me.email, modifier = Modifier.padding(top = 4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(top = 16.dp)) {
                    Stat("Dystans", formatDistance(me.stats.totalDistance))
                    Stat("Aktywności", "${me.stats.activityCount}")
                    Stat("Trasy", "${me.stats.routeCount}")
                    Stat("Punkty", "${me.stats.collectedPoiCount}")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 16.dp)) {
                    OutlinedButton(onClick = onOpenAccount) { Text("Ustawienia konta") }
                    Button(onClick = viewModel::signOut) { Text("Wyloguj") }
                }
                if (me.role == Role.ADMIN) {
                    Button(onClick = onOpenAdmin, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text("Panel administratora")
                    }
                }
            }

            item { SectionTitle("Zebrane punkty") }
            item { CollectionRow(state.collection, onRetry = viewModel::load) }

            item { SectionTitle("Historia aktywności") }
            when (val history = state.activities) {
                is UiState.Loading -> item { Text("Wczytywanie...", style = MaterialTheme.typography.bodySmall) }
                is UiState.Error -> item { RetryLine(history.message, viewModel::load) }
                is UiState.Success -> if (history.data.isEmpty()) {
                    item { Hint("Nie masz jeszcze zapisanych aktywności. Nagraj pierwszą na zakładce START.") }
                } else {
                    items(history.data, key = { it.id }) { activity ->
                        ActivityRow(activity, onClick = { onOpenActivity(activity.id) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun RetryLine(message: String, onRetry: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
        TextButton(onClick = onRetry) { Text("Ponów") }
    }
}

@Composable
private fun CollectionRow(collection: UiState<List<PoiCollectionEntry>>, onRetry: () -> Unit) {
    when (collection) {
        is UiState.Loading -> Text("Wczytywanie...", style = MaterialTheme.typography.bodySmall)
        is UiState.Error -> RetryLine(collection.message, onRetry)
        is UiState.Success -> if (collection.data.isEmpty()) {
            Hint("Nie masz jeszcze punktów. Zakładka Społeczność pokaże ciekawe miejsca w okolicy.")
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(collection.data, key = { it.poi.id }) { entry ->
                    Card(modifier = Modifier.width(150.dp)) {
                        Box(
                            Modifier.fillMaxWidth().size(width = 150.dp, height = 90.dp)
                                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            val photo = entry.poi.photoUrl
                            if (photo != null) {
                                AsyncImage(photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth())
                            } else {
                                Icon(Icons.Default.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Column(Modifier.padding(8.dp)) {
                            Text(entry.poi.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("pl-PL"))
                                    .withZone(ZoneId.systemDefault()).format(entry.collectedAt),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityRow(activity: ActivitySummary, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(activity.type.label(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(
                PROFILE_DATE_FORMAT.format(activity.startTime),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatDistance(activity.distance), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(formatDuration(activity.durationSeconds), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
