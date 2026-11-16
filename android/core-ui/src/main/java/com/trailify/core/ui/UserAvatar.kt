package com.trailify.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

private val AVATAR_COLORS = listOf(
    Color(0xFF1E88E5),
    Color(0xFF8E24AA),
    Color(0xFFD81B60),
    Color(0xFFE64A19),
    Color(0xFF5D4037),
    Color(0xFF00897B),
    Color(0xFF3949AB),
    Color(0xFF558B2F),
)

/**
 * Kolor awatara bez zdjęcia. Zależy od id użytkownika (a nie od losowania przy rysowaniu),
 * więc ta sama osoba ma ten sam kolor na każdym ekranie i po każdym uruchomieniu.
 */
fun avatarColor(userId: String): Color = AVATAR_COLORS[(userId.hashCode() and Int.MAX_VALUE) % AVATAR_COLORS.size]

/** Pierwsza litera lub cyfra nazwy wielką literą; "?" gdy nazwa jej nie ma. */
fun avatarInitial(displayName: String): String =
    displayName.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?"

/**
 * Zdjęcie profilowe, a bez zdjęcia kółko z pierwszą literą nazwy. Litera jest pod zdjęciem,
 * więc gdy zdjęcie się nie wczyta, nadal widać awatar.
 */
@Composable
fun UserAvatar(
    displayName: String,
    userId: String,
    avatarUrl: String?,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    ring: Boolean = false,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(avatarColor(userId))
            .let { if (ring) it.border(4.dp, MaterialTheme.colorScheme.background, CircleShape) else it },
    ) {
        Text(
            text = avatarInitial(displayName),
            color = Color.White,
            fontSize = (size.value * 0.42f).sp,
        )
        if (avatarUrl != null) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = "Zdjęcie profilowe",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
