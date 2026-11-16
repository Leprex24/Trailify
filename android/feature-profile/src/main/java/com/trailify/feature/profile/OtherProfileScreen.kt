package com.trailify.feature.profile

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trailify.core.domain.model.ActivitySummary
import com.trailify.core.domain.model.PoiCollectionEntry
import com.trailify.core.domain.model.PublicProfile
import com.trailify.core.domain.model.RouteSummary
import com.trailify.core.ui.BackButton
import com.trailify.core.ui.ErrorBox
import com.trailify.core.ui.LoadingBox
import com.trailify.core.ui.ProfileHeader
import com.trailify.core.ui.ProfileStat
import com.trailify.core.ui.UiState
import com.trailify.core.ui.formatDistance
import com.trailify.core.ui.label
import com.trailify.core.ui.swipeBetweenTabs
import java.util.Locale
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private enum class OtherProfileTab(val title: String) {
    ROUTES("Trasy"),
    ACTIVITIES("Aktywności"),
    POINTS("Punkty"),
}

/**
 * Cudzy profil: nagłówek z liczbami (obserwujących i obserwowanych da się otworzyć), przycisk Obserwuj
 * oraz zakładki Trasy, Aktywności i Punkty. Profil prywatny pokazuje tylko nazwę i zdjęcia.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OtherProfileScreen(
    userId: String,
    onBack: () -> Unit,
    onOpenFollowers: (userId: String) -> Unit,
    onOpenFollowing: (userId: String) -> Unit,
    onOpenRoute: (Long) -> Unit,
    onOpenActivity: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OtherProfileViewModel = koinViewModel(key = "user-$userId") { parametersOf(userId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(userId) { viewModel.load() }

    when (val profile = state.profile) {
        is UiState.Loading -> LoadingBox(modifier)
        is UiState.Error -> Column(modifier) {
            BackButton(onClick = onBack)
            ErrorBox(profile.message, onRetry = viewModel::load)
        }
        is UiState.Success -> LazyColumn(
            modifier = modifier.swipeBetweenTabs(tabIndex, OtherProfileTab.entries.size) { tabIndex = it },
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            val user = profile.data
            item { BackButton(onClick = onBack) }
            item { OtherProfileHeader(user, onOpenFollowers, onOpenFollowing) }
            if (user.canToggleFollow()) {
                item {
                    FollowButton(
                        following = user.isFollowing,
                        busy = state.followBusy,
                        error = state.followError,
                        onClick = viewModel::toggleFollow,
                    )
                }
            }
            if (user.stats != null) {
                stickyHeader {
                    PrimaryTabRow(selectedTabIndex = tabIndex) {
                        OtherProfileTab.entries.forEachIndexed { index, tab ->
                            Tab(selected = tabIndex == index, onClick = { tabIndex = index }, text = { Text(tab.title) })
                        }
                    }
                }
                when (OtherProfileTab.entries[tabIndex]) {
                    OtherProfileTab.ROUTES -> routesTab(state.routes, onOpenRoute, onRetry = viewModel::load)
                    OtherProfileTab.ACTIVITIES -> activitiesTab(state.activities, onOpenActivity, onRetry = viewModel::load)
                    OtherProfileTab.POINTS -> pointsOfUser(state.collection, onRetry = viewModel::load)
                }
            }
        }
    }
}

@Composable
private fun OtherProfileHeader(
    user: PublicProfile,
    onOpenFollowers: (String) -> Unit,
    onOpenFollowing: (String) -> Unit,
) {
    val stats = user.stats
    ProfileHeader(
        userId = user.id,
        displayName = user.displayName,
        avatarUrl = user.avatarUrl,
        backgroundUrl = user.backgroundUrl,
        stats = stats?.let {
            listOf(
                ProfileStat("Przebyte", formatDistance(it.totalDistance)),
                ProfileStat("Trasy", "${it.routeCount}"),
                ProfileStat("Obserwujący", "${it.followerCount}", onClick = { onOpenFollowers(user.id) }),
                ProfileStat("Obserwowani", "${it.followingCount}", onClick = { onOpenFollowing(user.id) }),
            )
        },
        hiddenNote = "To konto jest prywatne.",
    )
}

@Composable
private fun FollowButton(following: Boolean, busy: Boolean, error: String?, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        if (following) {
            OutlinedButton(onClick = onClick, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Obserwujesz") }
        } else {
            Button(onClick = onClick, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Obserwuj") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
}

private fun LazyListScope.routesTab(
    routes: UiState<List<RouteSummary>>,
    onOpenRoute: (Long) -> Unit,
    onRetry: () -> Unit,
) {
    when (routes) {
        is UiState.Loading -> item { Hint("Wczytywanie...") }
        is UiState.Error -> item { RetryLine(routes.message, onRetry) }
        is UiState.Success -> if (routes.data.isEmpty()) {
            item { Hint("Ta osoba nie ma jeszcze publicznych tras.") }
        } else {
            items(routes.data, key = { it.id }) { route ->
                UserRouteRow(route, onClick = { onOpenRoute(route.id) })
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

private fun LazyListScope.activitiesTab(
    activities: UiState<List<ActivitySummary>>,
    onOpenActivity: (Long) -> Unit,
    onRetry: () -> Unit,
) {
    when (activities) {
        is UiState.Loading -> item { Hint("Wczytywanie...") }
        is UiState.Error -> item { RetryLine(activities.message, onRetry) }
        is UiState.Success -> if (activities.data.isEmpty()) {
            item { Hint("Ta osoba nie udostępniła jeszcze żadnej aktywności.") }
        } else {
            items(activities.data, key = { it.id }) { activity ->
                ActivityRow(activity, onClick = { onOpenActivity(activity.id) })
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

private fun LazyListScope.pointsOfUser(collection: UiState<List<PoiCollectionEntry>>, onRetry: () -> Unit) {
    when (collection) {
        is UiState.Loading -> item { Hint("Wczytywanie...") }
        is UiState.Error -> item { RetryLine(collection.message, onRetry) }
        is UiState.Success -> if (collection.data.isEmpty()) {
            item { Hint("Ta osoba nie zebrała jeszcze żadnych punktów.") }
        } else {
            items(collection.data, key = { it.poi.id }) { entry ->
                PoiRow(entry)
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

@Composable
private fun UserRouteRow(route: RouteSummary, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(route.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            val rating = route.averageRating?.let { " · ★ " + String.format(Locale.forLanguageTag("pl-PL"), "%.1f", it) + " (${route.reviewCount})" }.orEmpty()
            Text(
                "${route.activityType.label()} · ${formatDistance(route.totalDistance)}$rating",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
