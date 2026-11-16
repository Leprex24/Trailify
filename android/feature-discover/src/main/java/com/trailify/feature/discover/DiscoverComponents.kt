package com.trailify.feature.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.trailify.core.domain.model.RouteSummary
import com.trailify.core.ui.formatDistance
import com.trailify.core.ui.formatDuration
import com.trailify.core.ui.label
import java.util.Locale

private val PL = Locale.forLanguageTag("pl-PL")

/** "4,7 (31 ocen)" albo "Brak ocen". */
internal fun ratingText(average: Double?, count: Int): String =
    if (average == null || count == 0) {
        "Brak ocen"
    } else {
        "${String.format(PL, "%.1f", average)} (${reviewsLabel(count)})"
    }

/** Polska odmiana: 1 ocena, 2-4 oceny, 5+ ocen (ale 22-24 oceny). */
internal fun reviewsLabel(count: Int): String {
    val word = when {
        count == 1 -> "ocena"
        count % 10 in 2..4 && count % 100 !in 12..14 -> "oceny"
        else -> "ocen"
    }
    return "$count $word"
}

/** Kafelek trasy w rzędzie na górze Odkrywaj: zdjęcie, nazwa, typ, dystans, ocena i przycisk "Zobacz na mapie". */
@Composable
internal fun RouteTile(
    route: RouteSummary,
    isNew: Boolean,
    onClick: () -> Unit,
    onShowOnMap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(onClick = onClick, modifier = modifier.width(TILE_WIDTH)) {
        Box {
            RouteTileImage(route.thumbnailUrl, modifier = Modifier.fillMaxWidth().height(TILE_IMAGE_HEIGHT))
            if (isNew) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                ) {
                    Text(
                        "Nowość",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }
        Column(modifier = Modifier.padding(12.dp)) {
            // dwie linie zawsze, żeby wszystkie kafelki w rzędzie miały tę samą wysokość
            Text(
                route.name,
                style = MaterialTheme.typography.titleSmall,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${route.activityType.label()} · ${formatDistance(route.totalDistance)}",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            RatingRow(route.averageRating, route.reviewCount, modifier = Modifier.padding(top = 2.dp))
            Button(
                onClick = onShowOnMap,
                enabled = route.startPoint != null,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) { Text("Zobacz na mapie", maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

/** Zwarta karta zaznaczonej trasy na dole mapy (mapa bywa mała, więc karta nie może jej zasłaniać). */
@Composable
internal fun MapRouteCard(route: RouteSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RouteThumbnail(route.thumbnailUrl, size = 64.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(route.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${route.activityType.label()} · ${formatDistance(route.totalDistance)} · ${formatDuration(route.durationSeconds)}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
                RatingRow(route.averageRating, route.reviewCount, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

@Composable
internal fun RatingRow(average: Double?, count: Int, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Icon(
            Icons.Default.Star,
            contentDescription = null,
            tint = if (count > 0) STAR_COLOR else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(16.dp),
        )
        Text(ratingText(average, count), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 4.dp))
    }
}

/** Szerokie zdjęcie kafelka; bez zdjęcia szare pole z ikoną. */
@Composable
internal fun RouteTileImage(url: String?, modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = "Zdjęcie trasy",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(Icons.Default.Place, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}

/** Zdjęcie trasy z internetu albo szare pole z ikoną, gdy trasa nie ma zdjęć. */
@Composable
internal fun RouteThumbnail(url: String?, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = "Zdjęcie trasy",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(Icons.Default.Place, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}

internal val STAR_COLOR = Color(0xFFF9A825)

private val TILE_WIDTH = 210.dp
private val TILE_IMAGE_HEIGHT = 110.dp
