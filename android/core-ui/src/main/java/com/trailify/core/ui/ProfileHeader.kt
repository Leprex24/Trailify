package com.trailify.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/** Jedna liczba w nagłówku profilu; [onClick] robi z niej przycisk (np. lista obserwujących). */
data class ProfileStat(val label: String, val value: String, val onClick: (() -> Unit)? = null)

/**
 * Góra ekranu profilu (własnego i cudzego): tło (zdjęcie albo zielony kolor), awatar nachodzący na tło,
 * nazwa i rząd liczb. [stats] = null chowa liczby (profil prywatny cudzej osoby), a pod nazwą pokazuje [hiddenNote].
 */
@Composable
fun ProfileHeader(
    userId: String,
    displayName: String,
    avatarUrl: String?,
    backgroundUrl: String?,
    stats: List<ProfileStat>?,
    modifier: Modifier = Modifier,
    hiddenNote: String? = null,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(BACKGROUND_HEIGHT)
                .background(MaterialTheme.colorScheme.primary),
        ) {
            if (backgroundUrl != null) {
                AsyncImage(
                    model = backgroundUrl,
                    contentDescription = "Tło profilu",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(top = BACKGROUND_HEIGHT - AVATAR_SIZE / 2),
        ) {
            UserAvatar(displayName, userId, avatarUrl, size = AVATAR_SIZE, ring = true)
            Text(
                displayName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            if (stats != null) {
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                ) {
                    stats.forEach { stat -> StatCell(stat, Modifier.weight(1f)) }
                }
            } else if (hiddenNote != null) {
                Text(
                    hiddenNote,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
        }
    }
}

@Composable
private fun StatCell(stat: ProfileStat, modifier: Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .let { if (stat.onClick != null) it.clickable(onClick = stat.onClick) else it }
            .padding(vertical = 8.dp),
    ) {
        Text(stat.value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            stat.label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val BACKGROUND_HEIGHT = 140.dp
private val AVATAR_SIZE = 96.dp
