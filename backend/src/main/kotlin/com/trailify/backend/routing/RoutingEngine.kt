package com.trailify.backend.routing

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.net.http.HttpClient
import java.time.Duration

/** Punkt (szerokość, długość). */
data class LatLon(val latitude: Double, val longitude: Double)

/** Trasa zwrócona przez silnik: metry, milisekundy i linia jako pary [długość, szerokość]. */
data class EngineRoute(val distanceMeters: Double, val timeMillis: Long, val coordinates: List<List<Double>>)

/** Silnik wyznaczania tras. Interfejs, żeby logikę dało się testować bez prawdziwego GraphHoppera. */
fun interface RoutingEngine {
    /** @param profile nazwa profilu w silniku (`bike`, `foot`) */
    fun route(profile: String, points: List<LatLon>): EngineRoute
}

/** Rzucane, gdy silnik nie potrafi poprowadzić trasy między punktami (np. punkt poza obszarem danych). */
class RouteNotFoundException(message: String) : RuntimeException(message)

/** Rzucane, gdy silnik jest niedostępny albo zwrócił błąd serwera. */
class RoutingUnavailableException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

@Component
class GraphHopperEngine(
    @Value("\${routing.graphhopper-url:http://localhost:8989}") baseUrl: String,
) : RoutingEngine {
    private val log = LoggerFactory.getLogger(javaClass)

    private val client: RestClient = RestClient.builder()
        .requestFactory(
            JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build())
                .apply { setReadTimeout(Duration.ofSeconds(15)) },
        )
        .baseUrl(baseUrl.trimEnd('/'))
        .build()

    override fun route(profile: String, points: List<LatLon>): EngineRoute {
        val body = try {
            client.get()
                .uri { b ->
                    b.path("/route")
                    // każdy punkt osobno (po jednym String), bo wersja z listą jest niejednoznaczna dla kompilatora
                    points.forEach { b.queryParam("point", "${it.latitude},${it.longitude}") }
                    b.queryParam("profile", profile)
                    b.queryParam("points_encoded", "false")
                    b.queryParam("instructions", "false")
                    b.build()
                }
                .retrieve()
                .onStatus({ it.is4xxClientError }) { _, response ->
                    // GraphHopper odpowiada 400, gdy nie znajdzie punktu lub połączenia
                    throw RouteNotFoundException("GraphHopper: ${response.statusCode}")
                }
                .body(object : ParameterizedTypeReference<Map<String, Any?>>() {})
        } catch (e: RouteNotFoundException) {
            throw e
        } catch (e: Exception) {
            log.error("Nie udało się połączyć z GraphHopper", e)
            throw RoutingUnavailableException("Silnik tras jest niedostępny", e)
        }
        return parseGraphHopperResponse(body ?: throw RoutingUnavailableException("Pusta odpowiedź silnika tras"))
    }
}

/** Wyciąga pierwszą trasę z odpowiedzi GraphHopper (`paths[0]`). */
fun parseGraphHopperResponse(body: Map<String, Any?>): EngineRoute {
    val path = (body["paths"] as? List<*>)?.firstOrNull() as? Map<*, *>
        ?: throw RouteNotFoundException("Brak trasy w odpowiedzi")
    val distance = (path["distance"] as? Number)?.toDouble()
        ?: throw RoutingUnavailableException("Odpowiedź bez dystansu")
    val time = (path["time"] as? Number)?.toLong()
        ?: throw RoutingUnavailableException("Odpowiedź bez czasu")
    val coordinates = ((path["points"] as? Map<*, *>)?.get("coordinates") as? List<*>)
        ?.map { pair -> (pair as List<*>).map { (it as Number).toDouble() } }
        ?: throw RoutingUnavailableException("Odpowiedź bez geometrii")
    return EngineRoute(distance, time, coordinates)
}
