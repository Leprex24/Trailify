package com.trailify.backend.maplayer

import com.trailify.backend.routing.LatLon
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import java.net.http.HttpClient
import java.time.Duration

/** Rzucane, gdy serwer Overpass jest niedostępny, zwrócił błąd albo przerwał zbyt ciężkie zapytanie. */
class OverpassUnavailableException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** Silnik zapytań o linie z OpenStreetMap. Interfejs, żeby serwis dało się testować bez prawdziwego Overpass. */
fun interface OverpassEngine {
    /** Wykonuje zapytanie Overpass QL i zwraca linie (każda to lista punktów). */
    fun lines(query: String): List<List<LatLon>>
}

@Component
class OverpassHttpEngine(
    @Value("\${maplayers.overpass-url:http://localhost:8081}") baseUrl: String,
) : OverpassEngine {
    private val log = LoggerFactory.getLogger(javaClass)

    private val client: RestClient = RestClient.builder()
        .requestFactory(
            JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build())
                .apply { setReadTimeout(Duration.ofSeconds(60)) },
        )
        .baseUrl(baseUrl.trimEnd('/'))
        .build()

    override fun lines(query: String): List<List<LatLon>> {
        val body = try {
            val form = LinkedMultiValueMap<String, String>().apply { add("data", query) }
            client.post()
                .uri("/api/interpreter")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(object : ParameterizedTypeReference<Map<String, Any?>>() {})
        } catch (e: Exception) {
            log.error("Nie udało się pobrać danych z serwera Overpass", e)
            throw OverpassUnavailableException("Serwer Overpass jest niedostępny", e)
        }
        return parseOverpassLines(body ?: throw OverpassUnavailableException("Pusta odpowiedź serwera Overpass"))
    }
}

/**
 * Zamienia odpowiedź Overpass (JSON z `out geom`) na linie. Overpass odpowiada kodem 200 także wtedy, gdy zapytanie
 * przekroczyło limit czasu lub pamięci: dane są wtedy niepełne, a informacja o błędzie siedzi w polu "remark".
 */
fun parseOverpassLines(body: Map<String, Any?>): List<List<LatLon>> {
    val remark = body["remark"] as? String
    if (remark != null && remark.contains("runtime error")) {
        throw OverpassUnavailableException("Serwer Overpass przerwał zbyt ciężkie zapytanie")
    }
    val elements = body["elements"] as? List<*> ?: return emptyList()
    val result = ArrayList<List<LatLon>>(elements.size)
    for (element in elements) {
        val geometry = (element as? Map<*, *>)?.get("geometry") as? List<*> ?: continue
        val line = ArrayList<LatLon>(geometry.size)
        for (point in geometry) {
            val p = point as? Map<*, *> ?: continue
            val lat = (p["lat"] as? Number)?.toDouble() ?: continue
            val lon = (p["lon"] as? Number)?.toDouble() ?: continue
            line.add(LatLon(lat, lon))
        }
        if (line.size >= 2) result.add(line)
    }
    return result
}
