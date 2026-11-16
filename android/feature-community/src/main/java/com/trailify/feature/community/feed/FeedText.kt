package com.trailify.feature.community.feed

import com.trailify.core.domain.model.FeedEventType
import com.trailify.core.domain.model.FeedItem
import com.trailify.core.ui.formatDistance
import com.trailify.core.ui.formatDuration
import com.trailify.core.ui.formatPace
import com.trailify.core.ui.label

/**
 * Teksty karty wpisu. [action] opisuje, co się stało (bez rodzaju gramatycznego, bo nie znamy płci osoby),
 * [title] to nazwa trasy albo punktu, a [details] to jedna linia z liczbami.
 */
internal data class FeedText(val action: String, val title: String?, val details: String?)

internal fun feedText(item: FeedItem): FeedText = when (item.type) {
    FeedEventType.ROUTE_PUBLISHED -> FeedText(
        action = "Opublikowana trasa",
        title = item.route?.name,
        details = item.route?.let { listOf(it.activityType.label(), formatDistance(it.totalDistance), formatDuration(it.durationSeconds)).joinToString(" · ") },
    )
    FeedEventType.ACTIVITY -> {
        val activity = item.activity
        FeedText(
            action = if (item.route != null) "Ukończona trasa" else "Udostępniona aktywność",
            title = item.route?.name ?: activity?.type?.label(),
            details = activity?.let {
                listOf(formatDistance(it.distance), formatDuration(it.durationSeconds), formatPace(it.avgPace)).joinToString(" · ")
            },
        )
    }
    FeedEventType.REVIEW -> FeedText(
        action = "Ocena trasy",
        title = item.route?.name,
        details = item.review?.let { "★ ${it.rating} / 5" },
    )
    FeedEventType.POI_COLLECTED -> FeedText(
        action = "Zebrany ciekawy punkt",
        title = item.poi?.name,
        details = item.poi?.category?.label(),
    )
}
