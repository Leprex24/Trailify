package com.trailify.core.data.api

import android.util.Log
import com.trailify.core.domain.model.AppException
import com.trailify.core.domain.repository.AuthErrorCodes
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException

/**
 * Wspólny klient naszego backendu. Dokleja token (JWT) z sesji Supabase i zamienia błędy API
 * ({"code": "...", "message": "..."}) na AppException z tym samym kodem.
 * Repozytoria (profil, aktywności, w przyszłości trasy) używają tylko tej klasy.
 */
class ApiClient(
    private val supabase: SupabaseClient,
    private val baseUrl: String,
) {
    private val client = HttpClient(OkHttp) {
        expectSuccess = false
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            // aktywność z dużą liczbą punktów to kilka MB, na słabym łączu potrzeba czasu
            requestTimeoutMillis = 60_000
        }
    }

    /**
     * Wysyła żądanie i zwraca treść odpowiedzi (pusta przy 204).
     * Rzuca AppException: NETWORK (brak połączenia), UNAUTHORIZED (brak sesji) albo kod z API.
     */
    suspend fun call(method: HttpMethod, path: String, body: JsonObject? = null): String {
        val token = supabase.auth.currentAccessTokenOrNull()
            ?: throw AppException(UNAUTHORIZED, "Brak sesji")
        val response = try {
            client.request("${baseUrl.trimEnd('/')}$path") {
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

        Log.w(TAG, "$method $path -> $status ${text.take(500)}")
        val json = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull()
        val code = json?.get("code")?.jsonPrimitive?.contentOrNull
            ?: if (status == 401) UNAUTHORIZED else "HTTP_$status"
        val message = json?.get("message")?.jsonPrimitive?.contentOrNull ?: "Błąd serwera ($status)"
        throw AppException(code, message)
    }

    private companion object {
        const val TAG = "ApiClient"
        const val UNAUTHORIZED = "UNAUTHORIZED"
    }
}
