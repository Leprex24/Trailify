package com.trailify.feature.community.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.trailify.core.domain.model.FeedEventType
import com.trailify.core.domain.model.FeedItem
import com.trailify.core.ui.RouteOutline
import com.trailify.core.ui.UserAvatar
import com.trailify.core.ui.formatTimeAgo
import java.time.Instant

private val AMBER = Color(0xFFF9A825)
private val VISUAL_HEIGHT = 150.dp

/**
 * Jeden wpis osi czasu: po lewej awatar na pionowej linii, po prawej karta z opisem, podglądem (zdjęcie albo obrys trasy)
 * oraz przyciskami Lubię i Skomentuj. [isLast] kończy linię na awatarze ostatniego wpisu.
 */
@Composable
internal fun FeedCard(
    item: FeedItem,
    now: Instant,
    isLast: Boolean,
    onOpenItem: () -> Unit,
    onOpenAuthor: () -> Unit,
    onLike: () -> Unit,
    onComment: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = feedText(item)
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(horizontal = 16.dp)) {
        // oś czasu: awatar i linia do następnego wpisu
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(44.dp).fillMaxHeight()) {
            UserAvatar(item.author.displayName, item.author.id, item.author.avatarUrl, size = 40.dp, modifier = Modifier.clickable(onClick = onOpenAuthor))
            if (!isLast) {
                Box(Modifier.padding(top = 4.dp).width(2.dp).weight(1f).background(MaterialTheme.colorScheme.outlineVariant))
            }
        }
        Card(modifier = Modifier.weight(1f).padding(start = 8.dp, bottom = 12.dp)) {
            Column(Modifier.clickable(onClick = onOpenItem).padding(top = 12.dp, start = 12.dp, end = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.author.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false).clickable(onClick = onOpenAuthor),
                    )
                    Text(
                        "  ·  " + formatTimeAgo(now, item.createdAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Text(text.action, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                text.title?.let { Text(it, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 2.dp)) }
                text.details?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                item.review?.comment?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                }
                FeedVisual(item, Modifier.padding(top = 8.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onLike) {
                    Icon(
                        if (item.likedByMe) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = null,
                        tint = if (item.likedByMe) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        if (item.likeCount > 0) "Lubię (${item.likeCount})" else "Lubię",
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
                TextButton(onClick = onComment) {
                    Text(if (item.commentCount > 0) "Skomentuj (${item.commentCount})" else "Skomentuj")
                }
            }
        }
    }
}

/** Zdjęcie, a bez zdjęcia obrys trasy albo bursztynowa gwiazdka dla punktu. Brak podglądu to brak miejsca na karcie. */
@Composable
private fun FeedVisual(item: FeedItem, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    when {
        item.photoUrl != null -> AsyncImage(
            model = item.photoUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.fillMaxWidth().height(VISUAL_HEIGHT).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant),
        )
        item.geometry.size >= 2 -> Box(modifier.fillMaxWidth().height(VISUAL_HEIGHT).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant)) {
            RouteOutline(item.geometry, Modifier.fillMaxSize())
        }
        item.type == FeedEventType.POI_COLLECTED -> Box(
            contentAlignment = Alignment.Center,
            modifier = modifier.fillMaxWidth().height(80.dp).clip(shape).background(AMBER),
        ) {
            Icon(Icons.Default.Star, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
        }
    }
}
