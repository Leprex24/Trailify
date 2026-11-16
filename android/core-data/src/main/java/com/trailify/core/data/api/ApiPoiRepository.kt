package com.trailify.core.data.api

import com.trailify.core.domain.model.BoundingBox
import com.trailify.core.domain.model.LatLng
import com.trailify.core.domain.model.Poi
import com.trailify.core.domain.model.PoiAdmin
import com.trailify.core.domain.model.PoiCollectionEntry
import com.trailify.core.domain.model.PoiDraft
import com.trailify.core.domain.model.PoiStatus
import com.trailify.core.domain.model.UserSummary
import com.trailify.core.domain.repository.PoiRepository
import io.ktor.http.HttpMethod
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.time.Instant

/** Ciekawe punkty z naszego backendu (GET/POST /pois, POST /pois/{id}/collect, GET /me/poi-collection). */
class ApiPoiRepository(private val api: ApiClient) : PoiRepository {

    override suspend fun inBounds(box: BoundingBox): List<Poi> {
        // kolejność w API: minLng,minLat,maxLng,maxLat
        val bbox = "${box.minLng},${box.minLat},${box.maxLng},${box.maxLat}"
        return Json.parseToJsonElement(api.call(HttpMethod.Get, "/pois?bbox=$bbox")).jsonArray.map { parsePoi(it.jsonObject) }
    }

    override suspend fun propose(draft: PoiDraft): PoiAdmin =
        parsePoiAdmin(Json.parseToJsonElement(api.call(HttpMethod.Post, "/pois", poiDraftJson(draft))).jsonObject)

    override suspend fun collect(poiId: Long, position: LatLng): PoiCollectionEntry {
        val body = buildJsonObject {
            put("latitude", position.latitude)
            put("longitude", position.longitude)
        }
        return parseCollectionEntry(Json.parseToJsonElement(api.call(HttpMethod.Post, "/pois/$poiId/collect", body)).jsonObject)
    }

    override suspend fun myCollection(): List<PoiCollectionEntry> =
        Json.parseToJsonElement(api.call(HttpMethod.Get, "/me/poi-collection")).jsonArray.map { parseCollectionEntry(it.jsonObject) }
}

/** Schemat PoiCreate (zgłoszenie użytkownika i dodanie przez administratora). */
internal fun poiDraftJson(draft: PoiDraft): JsonObject = buildJsonObject {
    put("name", draft.name)
    put("description", draft.description)
    put("latitude", draft.latitude)
    put("longitude", draft.longitude)
    draft.photo?.let { photo ->
        putJsonObject("photo") {
            put("storageKey", photo.storageKey)
            put("url", photo.url)
        }
    }
}

internal fun parsePoi(json: JsonObject) = Poi(
    id = json.getValue("id").jsonPrimitive.content.toLong(),
    name = json.getValue("name").jsonPrimitive.content,
    description = json.strOrNull("description"),
    latitude = json.getValue("latitude").jsonPrimitive.content.toDouble(),
    longitude = json.getValue("longitude").jsonPrimitive.content.toDouble(),
    photoUrl = json.strOrNull("photoUrl"),
)

/** Schemat PoiAdmin: pola Poi na tym samym poziomie co status i dane moderacji. */
internal fun parsePoiAdmin(json: JsonObject) = PoiAdmin(
    poi = parsePoi(json),
    status = PoiStatus.valueOf(json.getValue("status").jsonPrimitive.content),
    submittedBy = (json["submittedBy"] as? JsonObject)?.let {
        UserSummary(
            id = it.getValue("id").jsonPrimitive.content,
            displayName = it.getValue("displayName").jsonPrimitive.content,
            avatarUrl = it.strOrNull("avatarUrl"),
        )
    },
    rejectionReason = json.strOrNull("rejectionReason"),
    createdAt = Instant.parse(json.getValue("createdAt").jsonPrimitive.content),
)

internal fun parseCollectionEntry(json: JsonObject) = PoiCollectionEntry(
    poi = parsePoi(json.getValue("poi").jsonObject),
    collectedAt = Instant.parse(json.getValue("collectedAt").jsonPrimitive.content),
)
