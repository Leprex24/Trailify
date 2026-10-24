package com.trailify.core.data.api

import android.util.Log
import com.trailify.core.domain.model.AppException
import com.trailify.core.domain.model.Profile
import com.trailify.core.domain.model.ProfileStats
import com.trailify.core.domain.model.Role
import com.trailify.core.domain.repository.AuthErrorCodes
import com.trailify.core.domain.repository.ProfileRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.IOException
import java.time.Instant

/**
 * Prawdziwe /me z naszego backendu. Token (JWT) bierzemy z sesji Supabase.
 * Błędy API ({"code": "...", "message": "..."}) zamieniamy na AppException z tym samym kodem.
 */
class ApiProfileRepository(
    private val supabase: SupabaseClient,
    private val baseUrl: String,
) : ProfileRepository {

    private val client = HttpClient(OkHttp) {
        expectSuccess = false
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 30_000
        }
    }

    override suspend fun getMe(): Profile = parseProfile(call(HttpMethod.Get, null))

    override suspend fun updateMe(displayName: String?, avatarUrl: String?): Profile {
        val body = buildJsonObject {
            if (displayName != null) put("displayName", displayName)
            if (avatarUrl != null) put("avatarUrl", avatarUrl)
        }
        return parseProfile(call(HttpMethod.Patch, body))
    }

    override suspend fun deleteMe() {
        call(HttpMethod.Delete, null)
    }

    /** Wysyła żądanie na /me i zwraca treść odpowiedzi (pusta przy 204). */
    private suspend fun call(method: HttpMethod, body: JsonObject?): String {
        val token = supabase.auth.currentAccessTokenOrNull()
            ?: throw AppException(UNAUTHORIZED, "Brak sesji")
        val response: HttpResponse = try {
            client.request("${baseUrl.trimEnd('/')}/me") {
                this.method = method
                header(HttpHeaders.Authorization, "Bearer $token")
                if (body != null) {
                    contentType(ContentType.Application.Json)
                    setBody(body.toString())
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Log.w(TAG, "Brak połączenia z API ($baseUrl)", e)
            throw AppException(AuthErrorCodes.NETWORK, e.message ?: "Brak połączenia")
        }

        val text = response.bodyAsText()
        val status = response.status.value
        if (status in 200..299) return text

        Log.w(TAG, "$method /me -> $status $text")
        val json = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull()
        val code = json?.get("code")?.jsonPrimitive?.contentOrNull
            ?: if (status == 401) UNAUTHORIZED else "HTTP_$status"
        val message = json?.get("message")?.jsonPrimitive?.contentOrNull ?: "Błąd serwera ($status)"
        throw AppException(code, message)
    }

    private fun parseProfile(text: String): Profile {
        val json = Json.parseToJsonElement(text).jsonObject
        val stats = json.getValue("stats").jsonObject
        return Profile(
            id = json.getValue("id").jsonPrimitive.content,
            email = json.getValue("email").jsonPrimitive.content,
            displayName = json.getValue("displayName").jsonPrimitive.content,
            avatarUrl = (json["avatarUrl"] as? JsonPrimitive)?.contentOrNull,
            role = Role.valueOf(json.getValue("role").jsonPrimitive.content),
            createdAt = Instant.parse(json.getValue("createdAt").jsonPrimitive.content),
            stats = ProfileStats(
                totalDistance = stats["totalDistance"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                activityCount = stats["activityCount"]?.jsonPrimitive?.intOrNull ?: 0,
                routeCount = stats["routeCount"]?.jsonPrimitive?.intOrNull ?: 0,
                collectedPoiCount = stats["collectedPoiCount"]?.jsonPrimitive?.intOrNull ?: 0,
            ),
        )
    }

    private companion object {
        const val TAG = "ApiProfile"
        const val UNAUTHORIZED = "UNAUTHORIZED"
    }
}
