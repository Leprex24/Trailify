package com.trailify.feature.community

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.trailify.core.ui.PlaceholderScreen

/** Zakładka Społeczność: ciekawe punkty (POI), zbieranie punktów, propozycje punktów. */
@Composable
fun CommunityScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = "Społeczność",
        description = "Tu pojawią się ciekawe punkty do zbierania.",
        modifier = modifier,
    )
}
