package com.trailify.feature.discover

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.trailify.core.domain.model.RouteSummary
import com.trailify.core.ui.MapMarker
import com.trailify.core.ui.MarkersMap

/**
 * Mapa wyników Odkrywaj: punkt startowy każdej trasy. Dotknięcie punktu zaznacza trasę ([onSelect]),
 * dotknięcie pustego miejsca zdejmuje zaznaczenie.
 */
@Composable
internal fun RoutesMap(
    routes: List<RouteSummary>,
    selectedRouteId: Long?,
    onSelect: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    MarkersMap(
        markers = routes.mapNotNull { route -> route.startPoint?.let { MapMarker(route.id, it) } },
        selectedId = selectedRouteId,
        onSelect = onSelect,
        modifier = modifier,
    )
}
