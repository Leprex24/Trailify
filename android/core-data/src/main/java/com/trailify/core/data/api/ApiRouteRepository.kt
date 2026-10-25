package com.trailify.core.data.api

import com.trailify.core.domain.model.ActivityType
import com.trailify.core.domain.model.LatLng
import com.trailify.core.domain.model.Page
import com.trailify.core.domain.model.Photo
import com.trailify.core.domain.model.Route
import com.trailify.core.domain.model.RouteDraft
import com.trailify.core.domain.model.RouteQuery
import com.trailify.core.domain.model.RouteSummary
import com.trailify.core.domain.model.RouteUpdate
import com.trailify.core.domain.model.UserSummary
import com.trailify.core.domain.repository.RouteRepository
import io.ktor.http.HttpMethod
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.net.URLEncoder
import java.time.Instant

/** Trasy z naszego backendu (GET/POST /routes, PATCH/DELETE /routes/{id}, GET /me/routes). */
class ApiRouteRepository(private val api: ApiClient) : RouteRepository {

    override suspend fun search(query: RouteQuery): Page<RouteSummary> =
        parsePage(api.call(HttpMethod.Get, "/routes?" + queryString(query)))

    override suspend fun get(id: Long): Route = parseRoute(api.call(HttpMethod.Get, "/routes/$id"))

    override suspend fun create(draft: RouteDraft): Route {
        val body = buildJsonObject {
            put("name", draft.name)
            put("description", draft.description)
            put("activityType", draft.activityType.name)
            put("isTourist", draft.isTourist)
            put("isScenic", draft.isScenic)
            put("isPublic", draft.isPublic)
            // backend wymaga dokładnie jednego z pól: activityId albo geometry
            draft.activityId?.let { put("activityId", it) }
            draft.geometry?.let { points ->
                putJsonObject("geometry") {
                    put("type", "LineString")
                    putJsonArray("coordinates") {
                        points.forEach { p ->
                            add(buildJsonArray { add(JsonPrimitive(p.longitude)); add(JsonPrimitive(p.latitude)) })
                        }
                    }
                }
            }
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
        return parseRoute(api.call(HttpMethod.Post, "/routes", body))
    }

    override suspend fun update(id: Long, update: RouteUpdate): Route {
        // wysyłamy tylko pola do zmiany, bo brak pola oznacza "bez zmian"
        val body = buildJsonObject {
            update.name?.let { put("name", it) }
            update.description?.let { put("description", it) }
            update.activityType?.let { put("activityType", it.name) }
            update.isTourist?.let { put("isTourist", it) }
            update.isScenic?.let { put("isScenic", it) }
            update.isPublic?.let { put("isPublic", it) }
        }
        return parseRoute(api.call(HttpMethod.Patch, "/routes/$id", body))
    }

    override suspend fun delete(id: Long) {
        api.call(HttpMethod.Delete, "/routes/$id")
    }

    override suspend fun getMine(page: Int, size: Int): Page<RouteSummary> =
        parsePage(api.call(HttpMethod.Get, "/me/routes?page=$page&size=$size"))

    private fun queryString(query: RouteQuery): String {
        val params = mutableListOf<Pair<String, String>>()
        query.center?.let {
            params += "lat" to it.latitude.toString()
            params += "lng" to it.longitude.toString()
            query.radiusKm?.let { radius -> params += "radiusKm" to radius.toString() }
        }
        if (query.activityTypes.isNotEmpty()) params += "activityType" to query.activityTypes.joinToString(",") { it.name }
        query.minDistance?.let { params += "minDistance" to it.toString() }
        query.maxDistance?.let { params += "maxDistance" to it.toString() }
        query.minRating?.let { params += "minRating" to it.toString() }
        query.isTourist?.let { params += "isTourist" to it.toString() }
        query.isScenic?.let { params += "isScenic" to it.toString() }
        query.text?.takeIf { it.isNotBlank() }?.let { params += "q" to it.trim() }
        params += "sort" to query.sort.name.lowercase()
        params += "page" to query.page.toString()
        params += "size" to query.size.toString()
        return params.joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, "UTF-8")}" }
    }

    private fun parsePage(text: String): Page<RouteSummary> {
        val json = Json.parseToJsonElement(text).jsonObject
        return Page(
            items = json.getValue("items").jsonArray.map { parseSummary(it.jsonObject) },
            page = json.intOrNull("page") ?: 0,
            size = json.intOrNull("size") ?: 0,
            totalElements = json.longOrNull("totalElements") ?: 0L,
            totalPages = json.intOrNull("totalPages") ?: 0,
        )
    }

    private fun parseRoute(text: String): Route {
        val json = Json.parseToJsonElement(text).jsonObject
        // geometria to GeoJSON LineString: współrzędne są [długość, szerokość]
        val coordinates = json["geometry"]?.jsonObject?.get("coordinates")?.jsonArray.orEmpty()
        return Route(
            summary = parseSummary(json),
            description = json.strOrNull("description"),
            geometry = coordinates.map {
                val pair = it.jsonArray
                LatLng(latitude = pair[1].jsonPrimitive.content.toDouble(), longitude = pair[0].jsonPrimitive.content.toDouble())
            },
            photos = json["photos"]?.jsonArray.orEmpty().map {
                val photo = it.jsonObject
                Photo(id = photo.getValue("id").jsonPrimitive.content.toLong(), url = photo.getValue("url").jsonPrimitive.content)
            },
        )
    }

    private fun parseSummary(json: JsonObject): RouteSummary {
        val owner = json.getValue("owner").jsonObject
        return RouteSummary(
            id = json.getValue("id").jsonPrimitive.content.toLong(),
            name = json.getValue("name").jsonPrimitive.content,
            activityType = ActivityType.valueOf(json.getValue("activityType").jsonPrimitive.content),
            isTourist = json.boolOrFalse("isTourist"),
            isScenic = json.boolOrFalse("isScenic"),
            isPublic = json.boolOrFalse("isPublic"),
            totalDistance = json.doubleOrNull("totalDistance"),
            durationSeconds = json.intOrNull("durationSeconds"),
            averageRating = json.doubleOrNull("averageRating"),
            reviewCount = json.intOrNull("reviewCount") ?: 0,
            recommendationScore = json.doubleOrNull("recommendationScore"),
            startPoint = json["startPoint"]?.let { it as? JsonObject }?.let {
                val lat = it.doubleOrNull("latitude")
                val lng = it.doubleOrNull("longitude")
                if (lat != null && lng != null) LatLng(lat, lng) else null
            },
            thumbnailUrl = json.strOrNull("thumbnailUrl"),
            distanceFromQuery = json.doubleOrNull("distanceFromQuery"),
            owner = UserSummary(
                id = owner.getValue("id").jsonPrimitive.content,
                displayName = owner.getValue("displayName").jsonPrimitive.content,
                avatarUrl = owner.strOrNull("avatarUrl"),
            ),
            createdAt = Instant.parse(json.getValue("createdAt").jsonPrimitive.content),
        )
    }
}

private fun JsonObject.boolOrFalse(name: String): Boolean = strOrNull(name)?.toBoolean() ?: false
