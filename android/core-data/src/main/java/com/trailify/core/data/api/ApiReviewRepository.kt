package com.trailify.core.data.api

import com.trailify.core.domain.model.Page
import com.trailify.core.domain.model.Photo
import com.trailify.core.domain.model.Review
import com.trailify.core.domain.model.ReviewDraft
import com.trailify.core.domain.model.UserSummary
import com.trailify.core.domain.repository.ReviewRepository
import io.ktor.http.HttpMethod
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.time.Instant

/** Recenzje z naszego backendu (GET/POST /routes/{id}/reviews, DELETE /reviews/{id}). */
class ApiReviewRepository(private val api: ApiClient) : ReviewRepository {

    override suspend fun list(routeId: Long, page: Int, size: Int): Page<Review> {
        val json = Json.parseToJsonElement(api.call(HttpMethod.Get, "/routes/$routeId/reviews?page=$page&size=$size")).jsonObject
        return Page(
            items = json.getValue("items").jsonArray.map { parseReview(it.jsonObject) },
            page = json.intOrNull("page") ?: 0,
            size = json.intOrNull("size") ?: 0,
            totalElements = json.longOrNull("totalElements") ?: 0L,
            totalPages = json.intOrNull("totalPages") ?: 0,
        )
    }

    override suspend fun create(routeId: Long, draft: ReviewDraft): Review {
        val body = buildJsonObject {
            put("rating", draft.rating)
            put("comment", draft.comment)
            putJsonArray("photos") {
                draft.photos.forEach { photo ->
                    add(
                        buildJsonObject {
                            put("storageKey", photo.storageKey)
                            put("url", photo.url)
                        },
                    )
                }
            }
        }
        return parseReview(Json.parseToJsonElement(api.call(HttpMethod.Post, "/routes/$routeId/reviews", body)).jsonObject)
    }

    override suspend fun delete(reviewId: Long) {
        api.call(HttpMethod.Delete, "/reviews/$reviewId")
    }

    private fun parseReview(json: JsonObject): Review {
        val user = json.getValue("user").jsonObject
        return Review(
            id = json.getValue("id").jsonPrimitive.content.toLong(),
            routeId = json.getValue("routeId").jsonPrimitive.content.toLong(),
            user = UserSummary(
                id = user.getValue("id").jsonPrimitive.content,
                displayName = user.getValue("displayName").jsonPrimitive.content,
                avatarUrl = user.strOrNull("avatarUrl"),
            ),
            rating = json.getValue("rating").jsonPrimitive.content.toInt(),
            comment = json.strOrNull("comment"),
            photos = json["photos"]?.jsonArray.orEmpty().map {
                val photo = it.jsonObject
                Photo(id = photo.getValue("id").jsonPrimitive.content.toLong(), url = photo.getValue("url").jsonPrimitive.content)
            },
            createdAt = Instant.parse(json.getValue("createdAt").jsonPrimitive.content),
        )
    }
}
