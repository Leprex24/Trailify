package com.trailify.backend.account

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import java.net.http.HttpClient
import java.time.Duration
import java.util.UUID

/**
 * Operacje administracyjne w Supabase, które wymagają klucza secret (`sb_secret_...`):
 * usunięcie konta z Supabase Auth i plików użytkownika ze Storage.
 *
 * Klucz idzie w nagłówku `apikey` (nie w Authorization) i nigdy nie opuszcza backendu.
 */
@Component
class SupabaseAdminClient(
    @Value("\${supabase.url}") supabaseUrl: String,
    @Value("\${supabase.secret-key:}") private val secretKey: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val client: RestClient = RestClient.builder()
        .requestFactory(
            JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build())
                .apply { setReadTimeout(Duration.ofSeconds(15)) },
        )
        .baseUrl(supabaseUrl.trimEnd('/'))
        .defaultHeader("apikey", secretKey)
        .build()

    val isConfigured: Boolean get() = secretKey.isNotBlank()

    /** Usuwa wszystkie pliki użytkownika (ścieżki `<bucket>/<userId>/...`) ze wszystkich bucketów. */
    fun deleteUserFiles(userId: UUID) {
        for (bucket in BUCKETS) {
            var round = 0
            while (round++ < MAX_ROUNDS) {
                val names = listFiles(bucket, userId)
                if (names.isEmpty()) break
                removeFiles(bucket, names.map { "$userId/$it" })
            }
        }
    }

    /** Usuwa konto z Supabase Auth. Przez klucze obce znika też profil i wszystkie dane użytkownika. */
    fun deleteUser(userId: UUID) {
        try {
            client.delete()
                .uri("/auth/v1/admin/users/{id}", userId.toString())
                .retrieve()
                .toBodilessEntity()
        } catch (e: HttpClientErrorException) {
            // konto już usunięte (np. ponowienie po błędzie): to też sukces
            if (e.statusCode.value() != 404) throw e
        }
    }

    private fun listFiles(bucket: String, userId: UUID): List<String> =
        try {
            client.post()
                .uri("/storage/v1/object/list/{bucket}", bucket)
                .contentType(MediaType.APPLICATION_JSON)
                .body(mapOf("prefix" to userId.toString(), "limit" to PAGE_SIZE, "offset" to 0))
                .retrieve()
                .body(object : ParameterizedTypeReference<List<Map<String, Any?>>>() {})
                .orEmpty()
                .mapNotNull { it["name"] as? String }
        } catch (e: HttpClientErrorException) {
            val status = e.statusCode.value()
            // bucket jeszcze nie istnieje = nie ma czego usuwać
            if (status == 404 || status == 400) {
                log.info("Storage: bucket {} niedostępny ({}), pomijam", bucket, status)
                emptyList()
            } else {
                throw e
            }
        }

    private fun removeFiles(bucket: String, paths: List<String>) {
        client.method(HttpMethod.DELETE)
            .uri("/storage/v1/object/{bucket}", bucket)
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("prefixes" to paths))
            .retrieve()
            .toBodilessEntity()
    }

    private companion object {
        /** Buckety z openapi.yaml. */
        val BUCKETS = listOf("avatars", "route-photos", "review-photos", "poi-photos")
        const val PAGE_SIZE = 1000
        const val MAX_ROUNDS = 20
    }
}
