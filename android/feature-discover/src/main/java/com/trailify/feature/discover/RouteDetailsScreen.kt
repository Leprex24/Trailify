package com.trailify.feature.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.trailify.core.domain.model.Route
import com.trailify.core.ui.ErrorBox
import com.trailify.core.ui.LoadingBox
import com.trailify.core.ui.UiState
import com.trailify.core.ui.formatDistance
import com.trailify.core.ui.formatDuration
import com.trailify.core.ui.label
import com.trailify.feature.discover.reviews.ReviewsSection
import com.trailify.feature.discover.reviews.ReviewsViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DATE_FORMAT = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("pl-PL"))
    .withZone(ZoneId.systemDefault())

/**
 * Szczegóły trasy (W2, W20-W22): zdjęcia, statystyki, opis, przebieg na mapie,
 * "Przejdź tę trasę" (mapa START prowadzi po trasie) i opinie innych użytkowników.
 */
@Composable
fun RouteDetailsScreen(
    routeId: Long,
    onBack: () -> Unit,
    onFollowRoute: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RouteDetailsViewModel = koinViewModel(),
) {
    LaunchedEffect(routeId) { viewModel.load(routeId) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    when (val s = state) {
        is UiState.Loading -> LoadingBox(modifier)
        is UiState.Error -> ErrorBox(s.message, onRetry = { viewModel.load(routeId) }, modifier = modifier)
        is UiState.Success -> RouteDetailsContent(
            route = s.data,
            onBack = onBack,
            onFollow = {
                viewModel.followRoute(s.data)
                onFollowRoute()
            },
            onReviewsChanged = { viewModel.refresh(routeId) },
            modifier = modifier,
        )
    }
}

@Composable
private fun RouteDetailsContent(
    route: Route,
    onBack: () -> Unit,
    onFollow: () -> Unit,
    onReviewsChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val summary = route.summary
    // osobny ViewModel na każdą trasę (klucz), właściciel potrzebny do zasad oceniania
    val reviewsViewModel: ReviewsViewModel = koinViewModel(key = "reviews-${summary.id}") {
        parametersOf(summary.id, summary.owner.id)
    }
    val reviews by reviewsViewModel.ui.collectAsStateWithLifecycle()

    // nowa lub usunięta opinia zmienia średnią: odświeżamy nagłówek trasy
    LaunchedEffect(reviews.changeCount) { if (reviews.changeCount > 0) onReviewsChanged() }

    LazyColumn(modifier = modifier, contentPadding = PaddingValues(bottom = 32.dp)) {
        item { TextButton(onClick = onBack, modifier = Modifier.padding(8.dp)) { Text("← Wstecz") } }

        if (route.photos.isNotEmpty()) {
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                ) {
                    items(route.photos, key = { it.id }) { photo ->
                        AsyncImage(
                            model = photo.url,
                            contentDescription = "Zdjęcie trasy",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .width(if (route.photos.size == 1) 360.dp else 280.dp)
                                .height(200.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        )
                    }
                }
            }
        }

        item {
            Column(Modifier.padding(16.dp)) {
                Text(summary.name, style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Autor: ${summary.owner.displayName} · ${DATE_FORMAT.format(summary.createdAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                RatingRow(summary.averageRating, summary.reviewCount, modifier = Modifier.padding(top = 8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.padding(top = 16.dp)) {
                    Stat("Typ", summary.activityType.label())
                    Stat("Dystans", formatDistance(summary.totalDistance))
                    Stat("Czas", formatDuration(summary.durationSeconds))
                }
                val tags = listOfNotNull(
                    "Turystyczna".takeIf { summary.isTourist },
                    "Widokowa".takeIf { summary.isScenic },
                    "Prywatna".takeIf { !summary.isPublic },
                )
                if (tags.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        tags.forEach { AssistChip(onClick = {}, label = { Text(it) }) }
                    }
                }

                Text(
                    route.description ?: "Autor nie dodał opisu.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }

        if (route.geometry.size >= 2) {
            item {
                RouteLineMap(
                    geometry = route.geometry,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(220.dp)
                        .clip(RoundedCornerShape(16.dp)),
                )
            }
        }

        item {
            Button(onClick = onFollow, modifier = Modifier.fillMaxWidth().padding(16.dp)) { Text("Przejdź tę trasę") }
        }

        item {
            ReviewsSection(state = reviews, viewModel = reviewsViewModel, modifier = Modifier.padding(horizontal = 16.dp))
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
