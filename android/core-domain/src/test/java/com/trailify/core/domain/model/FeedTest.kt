package com.trailify.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class FeedTest {

    private val author = UserSummary("anna", "Anna")

    @Test
    fun zebranyPunktZWpisuToPoiZeZdjeciemWpisu() {
        val item = FeedItem(
            1, FeedEventType.POI_COLLECTED, Instant.EPOCH, author,
            poi = FeedPoi(7, "Brama Krakowska", PoiCategory.HISTORIC, "Gotycka brama.", 51.2477, 22.5675),
            photoUrl = "https://example.com/brama.jpg",
        )
        assertEquals(
            Poi(7, "Brama Krakowska", "Gotycka brama.", 51.2477, 22.5675, "https://example.com/brama.jpg", PoiCategory.HISTORIC),
            item.toPoi(),
        )
    }

    @Test
    fun wpisBezPunktuNieMaPoi() {
        assertNull(FeedItem(1, FeedEventType.ROUTE_PUBLISHED, Instant.EPOCH, author).toPoi())
    }

    @Test
    fun polubienieZmieniaZnacznikILicznikRazem() {
        val item = FeedItem(1, FeedEventType.ACTIVITY, Instant.EPOCH, author, likeCount = 2)
        val liked = item.withLiked(true)
        assertEquals(3, liked.likeCount)
        assertEquals(item, liked.withLiked(false))
        assertEquals(liked, liked.withLiked(true))
    }

    @Test
    fun licznikNieSpadaPonizejZera() {
        val item = FeedItem(1, FeedEventType.ACTIVITY, Instant.EPOCH, author, likeCount = 0, likedByMe = true)
        assertEquals(0, item.withLiked(false).likeCount)
    }
}
