package com.trailify.feature.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Wspólna wysokość przycisków w panelach, żeby sąsiednie przyciski były równe. */
internal val PanelButtonHeight = 52.dp

// Odstęp dolnej krawędzi dymka od dolnego paska. Przycisk START wystaje nad pasek o 28 dp (patrz BottomBar),
// więc zachodzi na dolną część dymka o 28 - 8 = 20 dp.
private val BottomMargin = 8.dp

// Pod treścią dymka zostaje miejsce na ten fragment przycisku START (20 dp) i trochę zapasu.
private val BottomPadding = 28.dp

/**
 * Dymek nad dolnym paskiem: zaokrąglony panel z marginesami po bokach. Przycisk START wystaje z paska i zachodzi
 * na środek dolnej krawędzi dymka (ten sam kolor), więc dymek wychodzi ze START.
 */
@Composable
internal fun PanelSheet(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 8.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = BottomMargin),
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = BottomPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}
