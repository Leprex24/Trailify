package com.trailify.feature.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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

/** Karta trasy na liście Odkrywaj: zdjęcie, nazwa, typ, dystans, czas, ocena i cechy trasy. */
@Composable
internal fun DiscoverRouteCard(route: RouteSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RouteThumbnail(route.thumbnailUrl, size = 88.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    route.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${route.activityType.label()} · ${formatDistance(route.totalDistance)} · ${formatDuration(route.durationSeconds)}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 2.dp),
                )
                RatingRow(route.averageRating, route.reviewCount, modifier = Modifier.padding(top = 4.dp))
                val tags = listOfNotNull("Turystyczna".takeIf { route.isTourist }, "Widokowa".takeIf { route.isScenic })
                if (tags.isNotEmpty()) {
                    Text(
                        tags.joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Text(
                    "Autor: ${route.owner.displayName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
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
