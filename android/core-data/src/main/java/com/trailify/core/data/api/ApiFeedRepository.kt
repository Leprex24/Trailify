package com.trailify.core.data.api

import com.trailify.core.domain.model.ActivityType
import com.trailify.core.domain.model.Comment
import com.trailify.core.domain.model.FeedActivity
import com.trailify.core.domain.model.FeedEventType
import com.trailify.core.domain.model.FeedItem
import com.trailify.core.domain.model.FeedPoi
import com.trailify.core.domain.model.FeedReview
import com.trailify.core.domain.model.FeedRoute
import com.trailify.core.domain.model.LatLng
import com.trailify.core.domain.model.Page
import com.trailify.core.domain.repository.FeedRepository
import io.ktor.http.HttpMethod
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.Instant

/** Oś czasu z naszego backendu: /feed, /feed/{id}/like, /feed/{id}/comments, /feed/comments/{id}. */
class ApiFeedRepository(private val api: ApiClient) : FeedRepository {

    override suspend fun feed(page: Int, size: Int): Page<FeedItem> =
        parsePageOf(api.call(HttpMethod.Get, "/feed?page=$page&size=$size"), ::parseFeedItem)

    override suspend fun like(eventId: Long) {
        api.call(HttpMethod.Put, "/feed/$eventId/like")
    }

    override suspend fun unlike(eventId: Long) {
        api.call(HttpMethod.Delete, "/feed/$eventId/like")
    }

    override suspend fun comments(eventId: Long, page: Int, size: Int): Page<Comment> =
        parsePageOf(api.call(HttpMethod.Get, "/feed/$eventId/comments?page=$page&size=$size"), ::parseComment)

    override suspend fun addComment(eventId: Long, body: String, replyToUserId: String?): Comment {
        val json = buildJsonObject {
            put("body", body)
            replyToUserId?.let { put("replyToUserId", it) }
        }
        return parseComment(Json.parseToJsonElement(api.call(HttpMethod.Post, "/feed/$eventId/comments", json)).jsonObject)
    }

    override suspend fun deleteComment(commentId: Long) {
        api.call(HttpMethod.Delete, "/feed/comments/$commentId")
    }
}

/** Schemat FeedItem: pola pasujące do typu wpisu są wypełnione, pozostałe są null. */
internal fun parseFeedItem(json: JsonObject): FeedItem {
    val route = json["route"] as? JsonObject
    val activity = json["activity"] as? JsonObject
    val review = json["review"] as? JsonObject
    val poi = json["poi"] as? JsonObject
    return FeedItem(
        id = json.getValue("id").jsonPrimitive.content.toLong(),
        type = FeedEventType.valueOf(json.getValue("type").jsonPrimitive.content),
        createdAt = Instant.parse(json.getValue("createdAt").jsonPrimitive.content),
        author = parseUserSummary(json.getValue("author").jsonObject),
        route = route?.let {
            FeedRoute(
                id = it.longOrNull("id") ?: 0L,
                name = it.getValue("name").jsonPrimitive.content,
                activityType = ActivityType.valueOf(it.getValue("activityType").jsonPrimitive.content),
                totalDistance = it.doubleOrNull("totalDistance"),
                durationSeconds = it.intOrNull("durationSeconds"),
            )
        },
        activity = activity?.let {
            FeedActivity(
                id = it.longOrNull("id") ?: 0L,
                type = ActivityType.valueOf(it.getValue("type").jsonPrimitive.content),
                distance = it.doubleOrNull("distance"),
                durationSeconds = it.intOrNull("durationSeconds"),
                avgPace = it.doubleOrNull("avgPace"),
            )
        },
        review = review?.let { FeedReview(rating = it.intOrNull("rating") ?: 0, comment = it.strOrNull("comment")) },
        poi = poi?.let {
            FeedPoi(
                id = it.longOrNull("id") ?: 0L,
                name = it.getValue("name").jsonPrimitive.content,
                category = parsePoiCategory(it.strOrNull("category")),
            )
        },
        // GeoJSON LineString: współrzędne są [długość, szerokość]
        geometry = (json["geometry"] as? JsonObject)?.get("coordinates")?.jsonArray.orEmpty().map {
            val pair = it.jsonArray
            LatLng(latitude = pair[1].jsonPrimitive.content.toDouble(), longitude = pair[0].jsonPrimitive.content.toDouble())
        },
        photoUrl = json.strOrNull("photoUrl"),
        likeCount = json.intOrNull("likeCount") ?: 0,
        commentCount = json.intOrNull("commentCount") ?: 0,
        likedByMe = json.boolOrNull("likedByMe") ?: false,
    )
}

/** Schemat Comment; `replyTo` jest null przy zwykłym komentarzu. */
internal fun parseComment(json: JsonObject) = Comment(
    id = json.getValue("id").jsonPrimitive.content.toLong(),
    author = parseUserSummary(json.getValue("author").jsonObject),
    replyTo = (json["replyTo"] as? JsonObject)?.let(::parseUserSummary),
    body = json.getValue("body").jsonPrimitive.content,
    createdAt = Instant.parse(json.getValue("createdAt").jsonPrimitive.content),
)
