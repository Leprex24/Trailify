package com.trailify.core.data.api

import com.trailify.core.domain.model.AdminUser
import com.trailify.core.domain.model.Page
import com.trailify.core.domain.model.PoiAdmin
import com.trailify.core.domain.model.PoiDraft
import com.trailify.core.domain.model.PoiStatus
import com.trailify.core.domain.model.UserStatus
import com.trailify.core.domain.repository.AdminRepository
import io.ktor.http.HttpMethod
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.URLEncoder

/** Panel administratora (/admin/pois..., /admin/users...). Serwer wpuszcza tylko rolę ADMIN. */
class ApiAdminRepository(private val api: ApiClient) : AdminRepository {

    override suspend fun pois(status: PoiStatus?, page: Int, size: Int): Page<PoiAdmin> {
        val filter = status?.let { "status=${it.name}&" }.orEmpty()
        return parsePage(api.call(HttpMethod.Get, "/admin/pois?${filter}page=$page&size=$size"), ::parsePoiAdmin)
    }

    override suspend fun createPoi(draft: PoiDraft): PoiAdmin =
        parsePoiAdmin(Json.parseToJsonElement(api.call(HttpMethod.Post, "/admin/pois", poiDraftJson(draft))).jsonObject)

    override suspend fun updatePoi(id: Long, name: String?, description: String?): PoiAdmin {
        // brak pola = bez zmian
        val body = buildJsonObject {
            name?.let { put("name", it) }
            description?.let { put("description", it) }
        }
        return parsePoiAdmin(Json.parseToJsonElement(api.call(HttpMethod.Patch, "/admin/pois/$id", body)).jsonObject)
    }

    override suspend fun deletePoi(id: Long) {
        api.call(HttpMethod.Delete, "/admin/pois/$id")
    }

    override suspend fun approvePoi(id: Long) {
        api.call(HttpMethod.Post, "/admin/pois/$id/approve")
    }

    override suspend fun rejectPoi(id: Long, reason: String?) {
        api.call(HttpMethod.Post, "/admin/pois/$id/reject", buildJsonObject { reason?.let { put("reason", it) } })
    }

    override suspend fun users(query: String?, page: Int, size: Int): Page<AdminUser> {
        val q = query?.trim()?.takeIf { it.isNotEmpty() }?.let { "q=${URLEncoder.encode(it, "UTF-8")}&" }.orEmpty()
        return parsePage(api.call(HttpMethod.Get, "/admin/users?${q}page=$page&size=$size")) { json ->
            AdminUser(profile = parseProfileJson(json), status = UserStatus.valueOf(json.getValue("status").jsonPrimitive.content))
        }
    }

    override suspend fun blockUser(id: String, deleteContent: Boolean) {
        api.call(HttpMethod.Post, "/admin/users/$id/block", buildJsonObject { put("deleteContent", deleteContent) })
    }

    override suspend fun unblockUser(id: String) {
        api.call(HttpMethod.Post, "/admin/users/$id/unblock")
    }

    override suspend fun deleteUser(id: String) {
        api.call(HttpMethod.Delete, "/admin/users/$id")
    }

    private fun <T> parsePage(text: String, item: (JsonObject) -> T): Page<T> {
        val json = Json.parseToJsonElement(text).jsonObject
        return Page(
            items = json.getValue("items").jsonArray.map { item(it.jsonObject) },
            page = json.intOrNull("page") ?: 0,
            size = json.intOrNull("size") ?: 0,
            totalElements = json.longOrNull("totalElements") ?: 0L,
            totalPages = json.intOrNull("totalPages") ?: 0,
        )
    }
}
