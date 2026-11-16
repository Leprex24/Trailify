package com.trailify.feature.community.feed

import com.trailify.core.domain.model.ActivityType
import com.trailify.core.domain.model.FeedActivity
import com.trailify.core.domain.model.FeedEventType
import com.trailify.core.domain.model.FeedItem
import com.trailify.core.domain.model.FeedPoi
import com.trailify.core.domain.model.FeedReview
import com.trailify.core.domain.model.FeedRoute
import com.trailify.core.domain.model.PoiCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class FeedTextTest {

    private val route = FeedRoute(3, "Pętla nad Zalewem", ActivityType.CYCLING, 12_000.0, 3600)

    private fun item(type: FeedEventType, route: FeedRoute? = null, activity: FeedActivity? = null, review: FeedReview? = null, poi: FeedPoi? = null) =
        FeedItem(1, type, Instant.EPOCH, ANNA, route = route, activity = activity, review = review, poi = poi)

    @Test
    fun opublikowanaTrasaMaNazweIDane() {
        val text = feedText(item(FeedEventType.ROUTE_PUBLISHED, route = route))
        assertEquals("Opublikowana trasa", text.action)
        assertEquals("Pętla nad Zalewem", text.title)
        assertTrue(text.details.orEmpty().contains("12"))
    }

    @Test
    fun aktywnoscBezTrasyJestUdostepniona() {
        val activity = FeedActivity(9, ActivityType.RUNNING, 5000.0, 1500, 5.0)
        val text = feedText(item(FeedEventType.ACTIVITY, activity = activity))
        assertEquals("Udostępniona aktywność", text.action)
    }

    @Test
    fun aktywnoscNaTrasieJestUkonczonaTrasa() {
        val activity = FeedActivity(9, ActivityType.CYCLING, 12_000.0, 3600, 5.0)
        val text = feedText(item(FeedEventType.ACTIVITY, route = route, activity = activity))
        assertEquals("Ukończona trasa", text.action)
        assertEquals("Pętla nad Zalewem", text.title)
    }

    @Test
    fun ocenaPokazujeGwiazdki() {
        val text = feedText(item(FeedEventType.REVIEW, route = route, review = FeedReview(4, null)))
        assertEquals("Ocena trasy", text.action)
        assertEquals("★ 4 / 5", text.details)
    }

    @Test
    fun zebranyPunktMaNazweIKategorie() {
        val text = feedText(item(FeedEventType.POI_COLLECTED, poi = FeedPoi(1, "Wieża widokowa", PoiCategory.VIEWPOINT)))
        assertEquals("Zebrany ciekawy punkt", text.action)
        assertEquals("Wieża widokowa", text.title)
        assertEquals("Punkt widokowy", text.details)
    }
}
