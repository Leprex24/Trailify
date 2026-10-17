package com.trailify.feature.routes

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.trailify.core.ui.PlaceholderScreen

/** Zakładka Trasy: moje zaplanowane i zapisane trasy, historia aktywności (W25). */
@Composable
fun RoutesScreen(
    onRouteClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    PlaceholderScreen(
        title = "Moje trasy",
        description = "Tu pojawią się zaplanowane trasy i historia aktywności.",
        modifier = modifier,
    )
}
