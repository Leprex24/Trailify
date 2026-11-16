package com.trailify.core.data.api

import com.trailify.core.domain.model.ActivitySummary
import com.trailify.core.domain.model.Page
import com.trailify.core.domain.model.PoiCollectionEntry
import com.trailify.core.domain.model.PublicProfile
import com.trailify.core.domain.model.PublicStats
import com.trailify.core.domain.model.RouteSummary
import com.trailify.core.domain.model.UserSummary
import com.trailify.core.domain.repository.SocialRepository
import io.ktor.http.HttpMethod
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder

/** Cudze profile i obserwowanie: /users/{id}, /follow, /followers, /following, /routes, /poi-collection. */
class ApiSocialRepository(private val api: ApiClient) : SocialRepository {

    override suspend fun getProfile(userId: String): PublicProfile =
        parsePublicProfile(Json.parseToJsonElement(api.call(HttpMethod.Get, "/users/$userId")).jsonObject)

    override suspend fun follow(userId: String) {
        api.call(HttpMethod.Put, "/users/$userId/follow")
    }

    override suspend fun unfollow(userId: String) {
        api.call(HttpMethod.Delete, "/users/$userId/follow")
    }

    override suspend fun followers(userId: String, page: Int, size: Int): Page<UserSummary> =
        parsePageOf(api.call(HttpMethod.Get, "/users/$userId/followers?page=$page&size=$size"), ::parseUserSummary)

    override suspend fun following(userId: String, page: Int, size: Int): Page<UserSummary> =
        parsePageOf(api.call(HttpMethod.Get, "/users/$userId/following?page=$page&size=$size"), ::parseUserSummary)

    override suspend fun routes(userId: String, page: Int, size: Int): Page<RouteSummary> =
        parsePageOf(api.call(HttpMethod.Get, "/users/$userId/routes?page=$page&size=$size"), ::parseRouteSummary)

    override suspend fun poiCollection(userId: String): List<PoiCollectionEntry> =
        Json.parseToJsonElement(api.call(HttpMethod.Get, "/users/$userId/poi-collection")).jsonArray
            .map { parseCollectionEntry(it.jsonObject) }

    override suspend fun activities(userId: String, page: Int, size: Int): Page<ActivitySummary> =
        parsePageOf(api.call(HttpMethod.Get, "/users/$userId/activities?page=$page&size=$size"), ::parseActivitySummary)

    override suspend fun search(query: String, page: Int, size: Int): Page<UserSummary> {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        return parsePageOf(api.call(HttpMethod.Get, "/users?query=$q&page=$page&size=$size"), ::parseUserSummary)
    }

    override suspend fun removeFollower(userId: String) {
        api.call(HttpMethod.Delete, "/me/followers/$userId")
    }
}

/** Schemat PublicProfile; `stats` jest null dla cudzego profilu prywatnego. */
internal fun parsePublicProfile(json: JsonObject): PublicProfile {
    val stats = json["stats"] as? JsonObject
    return PublicProfile(
        id = json.getValue("id").jsonPrimitive.content,
        displayName = json.getValue("displayName").jsonPrimitive.content,
        avatarUrl = json.strOrNull("avatarUrl"),
        backgroundUrl = json.strOrNull("backgroundUrl"),
        isPrivate = json.boolOrNull("isPrivate") ?: false,
        isMe = json.boolOrNull("isMe") ?: false,
        isFollowing = json.boolOrNull("isFollowing") ?: false,
        stats = stats?.let {
            PublicStats(
                totalDistance = it.doubleOrNull("totalDistance") ?: 0.0,
                routeCount = it.intOrNull("routeCount") ?: 0,
                followerCount = it.intOrNull("followerCount") ?: 0,
                followingCount = it.intOrNull("followingCount") ?: 0,
            )
        },
    )
}

internal fun parseUserSummary(json: JsonObject) = UserSummary(
    id = json.getValue("id").jsonPrimitive.content,
    displayName = json.getValue("displayName").jsonPrimitive.content,
    avatarUrl = json.strOrNull("avatarUrl"),
)

/** Strona wyników (PageResponse z backendu): `items`, `page`, `size`, `totalElements`, `totalPages`. */
internal fun <T> parsePageOf(text: String, parseItem: (JsonObject) -> T): Page<T> {
    val json = Json.parseToJsonElement(text).jsonObject
    return Page(
        items = json.getValue("items").jsonArray.map { parseItem(it.jsonObject) },
        page = json.intOrNull("page") ?: 0,
        size = json.intOrNull("size") ?: 0,
        totalElements = json.longOrNull("totalElements") ?: 0L,
        totalPages = json.intOrNull("totalPages") ?: 0,
    )
}
