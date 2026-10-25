package com.trailify.core.data.api

import com.trailify.core.domain.model.ActivityType
import com.trailify.core.domain.model.LatLng
import com.trailify.core.domain.model.PlannedRoute
import com.trailify.core.domain.repository.RoutePlanner
import io.ktor.http.HttpMethod
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Planowanie trasy przez nasz backend (POST /routing/plan, silnik: GraphHopper). */
class ApiRoutePlanner(private val api: ApiClient) : RoutePlanner {

    override suspend fun plan(type: ActivityType, waypoints: List<LatLng>): PlannedRoute {
        val body = buildJsonObject {
            put("profile", type.name)
            put(
                "points",
                buildJsonArray {
                    waypoints.forEach { p ->
                        add(
                            buildJsonObject {
                                put("latitude", p.latitude)
                                put("longitude", p.longitude)
                            },
                        )
                    }
                },
            )
        }
        val json = Json.parseToJsonElement(api.call(HttpMethod.Post, "/routing/plan", body)).jsonObject
        // geometria to GeoJSON LineString: współrzędne są [długość, szerokość]
        val coordinates = json.getValue("geometry").jsonObject.getValue("coordinates").jsonArray
        return PlannedRoute(
            distance = json.doubleOrNull("distance") ?: 0.0,
            durationSeconds = json.intOrNull("durationSeconds") ?: 0,
            geometry = coordinates.map {
                val pair = it.jsonArray
                LatLng(
                    latitude = pair[1].jsonPrimitive.content.toDouble(),
                    longitude = pair[0].jsonPrimitive.content.toDouble(),
                )
            },
        )
    }
}
