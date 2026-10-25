package com.trailify.core.data.api

import com.trailify.core.domain.model.Profile
import com.trailify.core.domain.model.ProfileStats
import com.trailify.core.domain.model.Role
import com.trailify.core.domain.repository.ProfileRepository
import io.ktor.http.HttpMethod
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.Instant

/** Prawdziwe /me z naszego backendu. */
class ApiProfileRepository(private val api: ApiClient) : ProfileRepository {

    override suspend fun getMe(): Profile = parseProfile(api.call(HttpMethod.Get, "/me"))

    override suspend fun updateMe(displayName: String?, avatarUrl: String?): Profile {
        val body = buildJsonObject {
            if (displayName != null) put("displayName", displayName)
            if (avatarUrl != null) put("avatarUrl", avatarUrl)
        }
        return parseProfile(api.call(HttpMethod.Patch, "/me", body))
    }

    override suspend fun deleteMe() {
        api.call(HttpMethod.Delete, "/me")
    }

    private fun parseProfile(text: String): Profile {
        val json = Json.parseToJsonElement(text).jsonObject
        val stats = json.getValue("stats").jsonObject
        return Profile(
            id = json.getValue("id").jsonPrimitive.content,
            email = json.getValue("email").jsonPrimitive.content,
            displayName = json.getValue("displayName").jsonPrimitive.content,
            avatarUrl = json.strOrNull("avatarUrl"),
            role = Role.valueOf(json.getValue("role").jsonPrimitive.content),
            createdAt = Instant.parse(json.getValue("createdAt").jsonPrimitive.content),
            stats = ProfileStats(
                totalDistance = stats.doubleOrNull("totalDistance") ?: 0.0,
                activityCount = stats.intOrNull("activityCount") ?: 0,
                routeCount = stats.intOrNull("routeCount") ?: 0,
                collectedPoiCount = stats.intOrNull("collectedPoiCount") ?: 0,
            ),
        )
    }
}
