package com.trailify.feature.profile

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.trailify.core.domain.model.Profile
import com.trailify.core.ui.ErrorBox
import com.trailify.core.ui.LoadingBox
import com.trailify.core.ui.ProfileHeader
import com.trailify.core.ui.ProfileStat
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

private enum class ProfileTab(val title: String) {
    ACTIVITIES("Aktywności"),
    POINTS("Punkty"),
    STATS("Statystyki"),
}

/**
 * Własny profil: tło, awatar, nazwa i cztery liczby, przyciski "Edytuj profil" i "Ustawienia",
 * a pod nimi zakładki Aktywności, Punkty i Statystyki. Cała strona przewija się razem, a pasek zakładek
 * przykleja się do góry. Cudzy profil jest na osobnym ekranie.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProfileScreen(
    onOpenSettings: () -> Unit,
    onOpenActivity: (Long) -> Unit,
    onProposePoi: () -> Unit,
    onOpenFollowers: (userId: String) -> Unit,
    onOpenFollowing: (userId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    var showEdit by rememberSaveable { mutableStateOf(false) }

    // za każdym wejściem na ekran, np. po powrocie z ustawień albo po nagraniu aktywności
    LaunchedEffect(Unit) { viewModel.load() }

    when (val profile = state.profile) {
        is UiState.Loading -> LoadingBox(modifier)
        is UiState.Error -> ErrorBox(profile.message, onRetry = viewModel::load, modifier = modifier)
        is UiState.Success -> LazyColumn(modifier = modifier, contentPadding = PaddingValues(bottom = 24.dp)) {
            item { OwnProfileHeader(profile.data, onOpenFollowers, onOpenFollowing) }
            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                ) {
                    Button(onClick = { showEdit = true }, modifier = Modifier.fillMaxWidth()) { Text("Edytuj profil") }
                    OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) { Text("Ustawienia") }
                }
            }
            stickyHeader {
                PrimaryTabRow(selectedTabIndex = tabIndex) {
                    ProfileTab.entries.forEachIndexed { index, tab ->
                        Tab(selected = tabIndex == index, onClick = { tabIndex = index }, text = { Text(tab.title) })
                    }
                }
            }
            when (ProfileTab.entries[tabIndex]) {
                ProfileTab.ACTIVITIES -> activitiesTab(state.activities, onOpenActivity, onRetry = viewModel::load)
                ProfileTab.POINTS -> pointsTab(state.collection, onProposePoi, onRetry = viewModel::load)
                ProfileTab.STATS -> statsTab(
                    state = state,
                    onPreviousMonth = viewModel::previousMonth,
                    onNextMonth = viewModel::nextMonth,
                    onMetric = viewModel::selectMetric,
                    onSelectDay = viewModel::selectDay,
                    onOpenActivity = onOpenActivity,
                    onRetry = viewModel::load,
                )
            }
        }
    }

    if (showEdit) {
        EditProfileSheet(
            onDismiss = { showEdit = false },
            onSaved = {
                showEdit = false
                viewModel.load()
            },
        )
    }
}

@Composable
private fun OwnProfileHeader(
    profile: Profile,
    onOpenFollowers: (String) -> Unit,
    onOpenFollowing: (String) -> Unit,
) {
    ProfileHeader(
        userId = profile.id,
        displayName = profile.displayName,
        avatarUrl = profile.avatarUrl,
        backgroundUrl = profile.backgroundUrl,
        stats = listOf(
            ProfileStat("Przebyte", formatDistance(profile.stats.totalDistance)),
            ProfileStat("Trasy", "${profile.stats.routeCount}"),
            ProfileStat("Obserwujący", "${profile.stats.followerCount}", onClick = { onOpenFollowers(profile.id) }),
            ProfileStat("Obserwowani", "${profile.stats.followingCount}", onClick = { onOpenFollowing(profile.id) }),
        ),
    )
}

private fun LazyListScope.activitiesTab(
    history: UiState<List<ActivitySummary>>,
    onOpenActivity: (Long) -> Unit,
    onRetry: () -> Unit,
) {
    when (history) {
        is UiState.Loading -> item { Hint("Wczytywanie...") }
        is UiState.Error -> item { RetryLine(history.message, onRetry) }
        is UiState.Success -> if (history.data.isEmpty()) {
            item { Hint("Nie masz jeszcze zapisanych aktywności. Nagraj pierwszą na zakładce START.") }
        } else {
            items(history.data, key = { it.id }) { activity ->
                ActivityRow(activity, onClick = { onOpenActivity(activity.id) })
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

private fun LazyListScope.pointsTab(
    collection: UiState<List<PoiCollectionEntry>>,
    onProposePoi: () -> Unit,
    onRetry: () -> Unit,
) {
    item {
        OutlinedButton(
            onClick = onProposePoi,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        ) { Text("Zaproponuj nowy punkt") }
    }
    when (collection) {
        is UiState.Loading -> item { Hint("Wczytywanie...") }
        is UiState.Error -> item { RetryLine(collection.message, onRetry) }
        is UiState.Success -> if (collection.data.isEmpty()) {
            item { Hint("Nie masz jeszcze zebranych punktów. Zakładka Społeczność pokaże ciekawe miejsca w okolicy.") }
        } else {
            items(collection.data, key = { it.poi.id }) { entry ->
                PoiRow(entry)
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

@Composable
internal fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
    )
}

@Composable
internal fun RetryLine(message: String, onRetry: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
        TextButton(onClick = onRetry) { Text("Ponów") }
    }
}

@Composable
internal fun PoiRow(entry: PoiCollectionEntry) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            val photo = entry.poi.photoUrl
            if (photo != null) {
                AsyncImage(photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(56.dp))
            } else {
                Icon(Icons.Default.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(entry.poi.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                POINT_DATE_FORMAT.format(entry.collectedAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun ActivityRow(activity: ActivitySummary, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
    ) {
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

private val POINT_DATE_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("pl-PL")).withZone(ZoneId.systemDefault())
