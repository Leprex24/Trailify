package com.trailify.core.domain.model

import java.time.Instant

/** Rodzaj wpisu na osi czasu. */
enum class FeedEventType { ROUTE_PUBLISHED, ACTIVITY, REVIEW, POI_COLLECTED }

data class FeedRoute(
    val id: Long,
    val name: String,
    val activityType: ActivityType,
    val totalDistance: Double?, // metry
    val durationSeconds: Int?,
)

data class FeedActivity(
    val id: Long,
    val type: ActivityType,
    val distance: Double?, // metry
    val durationSeconds: Int?,
    val avgPace: Double?, // min/km
)

data class FeedReview(val rating: Int, val comment: String?)

data class FeedPoi(val id: Long, val name: String, val category: PoiCategory)

/**
 * Wpis osi czasu osoby, którą obserwujemy. Wypełnione są tylko pola pasujące do [type]:
 * ROUTE_PUBLISHED: [route]; ACTIVITY: [activity] (i [route], gdy ukończono publiczną trasę);
 * REVIEW: [route] i [review]; POI_COLLECTED: [poi].
 * [geometry] to uproszczony obrys trasy albo aktywności do podglądu na karcie (pusty, gdy go nie ma).
 */
data class FeedItem(
    val id: Long,
    val type: FeedEventType,
    val createdAt: Instant,
    val author: UserSummary,
    val route: FeedRoute? = null,
    val activity: FeedActivity? = null,
    val review: FeedReview? = null,
    val poi: FeedPoi? = null,
    val geometry: List<LatLng> = emptyList(),
    val photoUrl: String? = null,
    val likeCount: Int = 0,
    val commentCount: Int = 0,
    val likedByMe: Boolean = false,
)

/** Wpis po polubieniu albo cofnięciu polubienia: znacznik i licznik zmieniają się razem. */
fun FeedItem.withLiked(liked: Boolean): FeedItem {
    if (liked == likedByMe) return this
    return copy(likedByMe = liked, likeCount = (likeCount + if (liked) 1 else -1).coerceAtLeast(0))
}

/** Komentarz pod wpisem. [replyTo] to osoba oznaczona w odpowiedzi (null przy zwykłym komentarzu). */
data class Comment(
    val id: Long,
    val author: UserSummary,
    val replyTo: UserSummary?,
    val body: String,
    val createdAt: Instant,
)
