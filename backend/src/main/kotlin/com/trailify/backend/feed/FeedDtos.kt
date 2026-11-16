package com.trailify.backend.feed

import com.trailify.backend.common.GeoJsonLineString
import com.trailify.backend.route.UserSummaryResponse
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.util.UUID

const val COMMENT_MAX = 1000

/** Rodzaje wpisów na osi czasu (kolumna feed_event.type). */
object FeedEventType {
    const val ROUTE_PUBLISHED = "ROUTE_PUBLISHED"
    const val ACTIVITY = "ACTIVITY"
    const val REVIEW = "REVIEW"
    const val POI_COLLECTED = "POI_COLLECTED"
}

/** Trasa z wpisu (tylko publiczna). */
data class FeedRouteResponse(
    val id: Long,
    val name: String,
    val activityType: String,
    val totalDistance: Double?,
    val durationSeconds: Int?,
)

/** Udostępniona aktywność z wpisu. */
data class FeedActivityResponse(
    val id: Long,
    val type: String,
    val distance: Double?,
    val durationSeconds: Int?,
    val avgPace: Double?,
)

data class FeedReviewResponse(val rating: Int, val comment: String?)

/** Zebrany punkt: tyle, ile potrzeba do okna punktu i pokazania go na mapie (zdjęcie jest w [FeedItemResponse.photoUrl]). */
data class FeedPoiResponse(
    val id: Long,
    val name: String,
    val category: String,
    val description: String?,
    val latitude: Double,
    val longitude: Double,
)

/**
 * Wpis osi czasu (schemat FeedItem). Wypełnione są tylko pola pasujące do [type]:
 * ROUTE_PUBLISHED: route; ACTIVITY: activity (oraz route, gdy ukończono publiczną trasę);
 * REVIEW: route i review; POI_COLLECTED: poi.
 * [geometry] to uproszczony obrys trasy do podglądu na karcie, [photoUrl] zdjęcie trasy, oceny albo punktu.
 */
data class FeedItemResponse(
    val id: Long,
    val type: String,
    val createdAt: String,
    val author: UserSummaryResponse,
    val route: FeedRouteResponse?,
    val activity: FeedActivityResponse?,
    val review: FeedReviewResponse?,
    val poi: FeedPoiResponse?,
    val geometry: GeoJsonLineString?,
    val photoUrl: String?,
    val likeCount: Int,
    val commentCount: Int,
    val likedByMe: Boolean,
)

/** Schemat CommentCreate: treść i opcjonalnie osoba, której komentarz odpowiada (oznaczenie). */
data class CommentCreateRequest(
    @field:NotBlank @field:Size(max = COMMENT_MAX) val body: String?,
    val replyToUserId: UUID? = null,
)

/** Schemat Comment. [replyTo] to osoba oznaczona w odpowiedzi (null, gdy to zwykły komentarz). */
data class CommentResponse(
    val id: Long,
    val author: UserSummaryResponse,
    val replyTo: UserSummaryResponse?,
    val body: String,
    val createdAt: String,
)
