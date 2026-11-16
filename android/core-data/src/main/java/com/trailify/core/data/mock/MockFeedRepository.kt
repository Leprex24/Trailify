package com.trailify.core.data.mock

import com.trailify.core.domain.model.ActivityType
import com.trailify.core.domain.model.AppException
import com.trailify.core.domain.model.Comment
import com.trailify.core.domain.model.FeedEventType
import com.trailify.core.domain.model.FeedItem
import com.trailify.core.domain.model.FeedPoi
import com.trailify.core.domain.model.FeedReview
import com.trailify.core.domain.model.FeedRoute
import com.trailify.core.domain.model.Page
import com.trailify.core.domain.model.PoiCategory
import com.trailify.core.domain.model.withLiked
import com.trailify.core.domain.repository.FeedRepository
import kotlinx.coroutines.delay
import java.time.Instant

/** Atrapa osi czasu: kilka wpisów Anny i Marka z danych testowych, polubienia i komentarze żyją do zamknięcia aplikacji. */
class MockFeedRepository : FeedRepository {

    private val now = Instant.now()
    private val items = mutableListOf<FeedItem>()
    private val comments = mutableMapOf<Long, MutableList<Comment>>()
    private var nextCommentId = 100L

    init {
        val lake = MockData.routes.first { it.summary.id == 1L }
        val park = MockData.routes.first { it.summary.id == 2L }
        items += FeedItem(
            id = 1,
            type = FeedEventType.ROUTE_PUBLISHED,
            createdAt = now.minusSeconds(40 * 60),
            author = MockData.anna,
            route = FeedRoute(lake.summary.id, lake.summary.name, lake.summary.activityType, lake.summary.totalDistance, lake.summary.durationSeconds),
            geometry = lake.geometry,
            photoUrl = lake.photos.firstOrNull()?.url,
            likeCount = 3,
            commentCount = 1,
        )
        items += FeedItem(
            id = 2,
            type = FeedEventType.REVIEW,
            createdAt = now.minusSeconds(5 * 3600),
            author = MockData.marek,
            route = FeedRoute(lake.summary.id, lake.summary.name, lake.summary.activityType, lake.summary.totalDistance, lake.summary.durationSeconds),
            review = FeedReview(5, "Świetna pętla, polecam wczesnym rankiem."),
            geometry = lake.geometry,
            likeCount = 1,
        )
        items += FeedItem(
            id = 3,
            type = FeedEventType.POI_COLLECTED,
            createdAt = now.minusSeconds(26 * 3600),
            author = MockData.anna,
            poi = FeedPoi(3, "Wieża Trynitarska", PoiCategory.VIEWPOINT),
            likeCount = 0,
        )
        items += FeedItem(
            id = 4,
            type = FeedEventType.ROUTE_PUBLISHED,
            createdAt = now.minusSeconds(3 * 24 * 3600),
            author = MockData.marek,
            route = FeedRoute(park.summary.id, park.summary.name, ActivityType.WALKING, park.summary.totalDistance, park.summary.durationSeconds),
            geometry = park.geometry,
            likeCount = 2,
            likedByMe = true,
        )
        comments[1] = mutableListOf(
            Comment(1, MockData.marek, null, "Super trasa, byłem w weekend!", now.minusSeconds(20 * 60)),
        )
    }

    override suspend fun feed(page: Int, size: Int): Page<FeedItem> {
        delay(LATENCY_MS)
        val sorted = items.sortedByDescending { it.createdAt }
        val slice = sorted.drop(page * size).take(size)
        return Page(slice, page, size, sorted.size.toLong(), if (sorted.isEmpty()) 0 else (sorted.size + size - 1) / size)
    }

    override suspend fun like(eventId: Long) = change(eventId) { it.withLiked(true) }

    override suspend fun unlike(eventId: Long) = change(eventId) { it.withLiked(false) }

    override suspend fun comments(eventId: Long, page: Int, size: Int): Page<Comment> {
        delay(LATENCY_MS)
        find(eventId)
        val all = comments[eventId].orEmpty()
        return Page(all.drop(page * size).take(size), page, size, all.size.toLong(), if (all.isEmpty()) 0 else (all.size + size - 1) / size)
    }

    override suspend fun addComment(eventId: Long, body: String, replyToUserId: String?): Comment {
        delay(LATENCY_MS)
        val item = find(eventId)
        val list = comments.getOrPut(eventId) { mutableListOf() }
        val replyTo = replyToUserId?.let { id ->
            val known = (list.map { it.author } + item.author).firstOrNull { it.id == id }
            known ?: throw AppException("VALIDATION_ERROR", "Można odpowiedzieć tylko autorowi wpisu albo osobie, która komentowała")
        }
        val comment = Comment(nextCommentId++, MockData.me, replyTo, body.trim(), Instant.now())
        list += comment
        replace(item.copy(commentCount = item.commentCount + 1))
        return comment
    }

    override suspend fun deleteComment(commentId: Long) {
        delay(LATENCY_MS)
        for ((eventId, list) in comments) {
            if (list.removeAll { it.id == commentId }) {
                val item = find(eventId)
                replace(item.copy(commentCount = (item.commentCount - 1).coerceAtLeast(0)))
                return
            }
        }
        throw AppException("NOT_FOUND", "Komentarz nie istnieje")
    }

    private suspend fun change(eventId: Long, transform: (FeedItem) -> FeedItem) {
        delay(LATENCY_MS)
        replace(transform(find(eventId)))
    }

    private fun find(eventId: Long): FeedItem =
        items.firstOrNull { it.id == eventId } ?: throw AppException("NOT_FOUND", "Wpis nie istnieje")

    private fun replace(item: FeedItem) {
        items[items.indexOfFirst { it.id == item.id }] = item
    }

    private companion object {
        const val LATENCY_MS = 300L
    }
}
