package com.trailify.core.data.api

import com.trailify.core.domain.model.Activity
import com.trailify.core.domain.model.ActivityDraft
import com.trailify.core.domain.model.ActivitySummary
import com.trailify.core.domain.model.ActivityType
import com.trailify.core.domain.model.LatLng
import com.trailify.core.domain.model.Page
import com.trailify.core.domain.repository.ActivityRepository
import io.ktor.http.HttpMethod
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.Instant

/**
 * Aktywności z naszego backendu. Zapis jest bezpieczny do ponawiania: ten sam `clientId`
 * zwraca istniejącą aktywność zamiast duplikatu (serwer odpowiada wtedy 200 zamiast 201).
 */
class ApiActivityRepository(private val api: ApiClient) : ActivityRepository {

    override suspend fun save(draft: ActivityDraft): Activity {
        val body = buildJsonObject {
            put("clientId", draft.clientId)
            put("type", draft.type.name)
            put("routeId", draft.routeId)
            put("startTime", draft.startTime.toString())
            put("endTime", draft.endTime.toString())
            put("distance", draft.distance)
            put("durationSeconds", draft.durationSeconds)
            put("avgPace", draft.avgPace)
            put("stepCount", draft.stepCount)
            put(
                "points",
                buildJsonArray {
                    draft.points.forEach { p ->
                        add(
                            buildJsonObject {
                                put("latitude", p.latitude)
                                put("longitude", p.longitude)
                                put("altitude", p.altitude)
                                put("speed", p.speed)
                                put("recordedAt", p.recordedAt.toString())
                                put("isReconstructed", p.isReconstructed)
                            },
                        )
                    }
                },
            )
        }
        return parseActivity(api.call(HttpMethod.Post, "/activities", body))
    }

    override suspend fun get(id: Long): Activity = parseActivity(api.call(HttpMethod.Get, "/activities/$id"))

    override suspend fun delete(id: Long) {
        api.call(HttpMethod.Delete, "/activities/$id")
    }

    override suspend fun getMine(page: Int, size: Int): Page<ActivitySummary> {
        val json = Json.parseToJsonElement(api.call(HttpMethod.Get, "/me/activities?page=$page&size=$size")).jsonObject
        return Page(
            items = json.getValue("items").jsonArray.map { parseSummary(it.jsonObject) },
            page = json.intOrNull("page") ?: page,
            size = json.intOrNull("size") ?: size,
            totalElements = json.longOrNull("totalElements") ?: 0L,
            totalPages = json.intOrNull("totalPages") ?: 0,
        )
    }

    private fun parseActivity(text: String): Activity {
        val json = Json.parseToJsonElement(text).jsonObject
        // geometria to GeoJSON LineString: współrzędne są [długość, szerokość]
        val coordinates = json["geometry"]?.jsonObject?.get("coordinates")?.jsonArray.orEmpty()
        val geometry = coordinates.map {
            val pair = it.jsonArray
            LatLng(latitude = pair[1].jsonPrimitive.content.toDouble(), longitude = pair[0].jsonPrimitive.content.toDouble())
        }
        return Activity(summary = parseSummary(json), geometry = geometry)
    }

    private fun parseSummary(json: JsonObject) = ActivitySummary(
        id = json.longOrNull("id") ?: 0L,
        type = ActivityType.valueOf(json.getValue("type").jsonPrimitive.content),
        routeId = json.longOrNull("routeId"),
        startTime = Instant.parse(json.getValue("startTime").jsonPrimitive.content),
        endTime = json.strOrNull("endTime")?.let(Instant::parse),
        distance = json.doubleOrNull("distance"),
        durationSeconds = json.intOrNull("durationSeconds"),
        avgPace = json.doubleOrNull("avgPace"),
        stepCount = json.intOrNull("stepCount"),
    )
}
