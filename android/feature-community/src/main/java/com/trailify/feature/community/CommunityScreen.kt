package com.trailify.feature.community

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trailify.core.domain.model.FeedEventType
import com.trailify.core.domain.model.FeedItem
import com.trailify.core.domain.model.Poi
import com.trailify.core.domain.model.toPoi
import com.trailify.core.ui.ErrorBox
import com.trailify.core.ui.LoadingBox
import com.trailify.core.ui.PersonAddIcon
import com.trailify.core.ui.PoiCardDialog
import com.trailify.core.ui.ScreenTitle
import com.trailify.feature.community.feed.CommentsSheet
import com.trailify.feature.community.feed.FeedCard
import com.trailify.feature.community.feed.FeedViewModel
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel
import java.time.Instant

private const val MESSAGE_VISIBLE_MS = 4_000L

/**
 * Zakładka Społeczność: oś czasu osób, które obserwujesz (nowe trasy, aktywności, oceny i zebrane punkty).
 * Ikona w prawym górnym rogu otwiera wyszukiwarkę osób. Dotknięcie karty otwiera trasę, aktywność
 * albo profil autora, zależnie od rodzaju wpisu.
 */
@Composable
fun CommunityScreen(
    onSearchUsers: () -> Unit,
    onOpenUser: (userId: String) -> Unit,
    onOpenRoute: (routeId: Long) -> Unit,
    onOpenActivity: (activityId: Long) -> Unit,
    onShowPoiOnMap: (Poi) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FeedViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var commentsFor by remember { mutableStateOf<FeedItem?>(null) }
    var openedPoi by remember { mutableStateOf<Poi?>(null) }
    val now = remember(state.items) { Instant.now() }

    // po powrocie na zakładkę (np. po zaobserwowaniu kogoś) oś czasu odświeża się od nowa
    LifecycleStartEffect(Unit) {
        viewModel.load()
        onStopOrDispose { }
    }
    LaunchedEffect(state.message) {
        if (state.message != null) {
            delay(MESSAGE_VISIBLE_MS)
            viewModel.dismissMessage()
        }
    }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenTitle("Społeczność") {
                IconButton(onClick = onSearchUsers) { Icon(PersonAddIcon, contentDescription = "Znajdź osoby") }
            }
            when {
                state.isLoading -> LoadingBox()
                state.error != null && state.items.isEmpty() -> ErrorBox(state.error.orEmpty(), onRetry = viewModel::load)
                state.items.isEmpty() -> EmptyFeed(onSearchUsers)
                else -> LazyColumn(
                    contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    itemsIndexed(state.items, key = { _, item -> item.id }) { index, item ->
                        FeedCard(
                            item = item,
                            now = now,
                            isLast = index == state.items.lastIndex && !state.hasMore,
                            onOpenItem = {
                                when (item.type) {
                                    FeedEventType.ROUTE_PUBLISHED, FeedEventType.REVIEW ->
                                        item.route?.let { onOpenRoute(it.id) } ?: onOpenUser(item.author.id)
                                    FeedEventType.ACTIVITY ->
                                        item.activity?.let { onOpenActivity(it.id) } ?: onOpenUser(item.author.id)
                                    FeedEventType.POI_COLLECTED -> item.toPoi()?.let { openedPoi = it } ?: onOpenUser(item.author.id)
                                }
                            },
                            onOpenAuthor = { onOpenUser(item.author.id) },
                            // ukończona trasa: nazwa otwiera trasę, ślad i reszta karty aktywność
                            onOpenTitle = item.route?.takeIf { item.type == FeedEventType.ACTIVITY }?.let { route -> { onOpenRoute(route.id) } },
                            onLike = { viewModel.toggleLike(item.id) },
                            onComment = { commentsFor = item },
                        )
                    }
                    if (state.hasMore || state.isLoadingMore) {
                        item {
                            TextButton(
                                onClick = viewModel::loadMore,
                                enabled = !state.isLoadingMore,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(if (state.isLoadingMore) "Wczytywanie..." else "Pokaż więcej") }
                        }
                    }
                }
            }
        }
        state.message?.let {
            Snackbar(modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)) { Text(it) }
        }
    }

    openedPoi?.let { poi ->
        PoiCardDialog(poi = poi, onDismiss = { openedPoi = null }) {
            Button(onClick = { openedPoi = null; onShowPoiOnMap(poi) }, modifier = Modifier.fillMaxWidth()) {
                Text("Zobacz na mapie")
            }
        }
    }

    commentsFor?.let { item ->
        CommentsSheet(
            item = item,
            onCount = { viewModel.onCommentCount(item.id, it) },
            onOpenUser = onOpenUser,
            onDismiss = { commentsFor = null },
        )
    }
}

@Composable
private fun EmptyFeed(onSearchUsers: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Obserwuj kogoś, żeby zobaczyć jego trasy, aktywności i oceny.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onSearchUsers, modifier = Modifier.padding(top = 16.dp)) { Text("Znajdź osoby") }
    }
}
