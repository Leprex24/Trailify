package com.trailify.backend.routing

import com.trailify.backend.common.ActivityType
import com.trailify.backend.common.GeoJsonLineString
import com.trailify.backend.web.ApiException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service

/** Biegacz jest ok. dwa razy szybszy od spacerowicza (ok. 10 km/h wobec 5 km/h). */
const val RUNNING_TIME_FACTOR = 0.5

@Service
class RoutingService(private val engine: RoutingEngine) {

    fun plan(request: RoutePlanRequest): RoutePlanResponse {
        val type = request.profile!!
        val points = request.points!!.map { LatLon(it.latitude!!, it.longitude!!) }
        val route = try {
            engine.route(engineProfile(type), points)
        } catch (e: RouteNotFoundException) {
            throw ApiException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "ROUTE_NOT_FOUND",
                "Nie znaleziono trasy między wskazanymi punktami",
            )
        } catch (e: RoutingUnavailableException) {
            throw ApiException(HttpStatus.SERVICE_UNAVAILABLE, "ROUTING_UNAVAILABLE", "Planowanie tras jest chwilowo niedostępne")
        }
        return RoutePlanResponse(
            distance = route.distanceMeters,
            durationSeconds = durationSeconds(type, route.timeMillis),
            geometry = GeoJsonLineString(coordinates = route.coordinates),
        )
    }
}

/** Profil silnika dla rodzaju aktywności: bieg i spacer idą po tych samych drogach. */
fun engineProfile(type: ActivityType): String = when (type) {
    ActivityType.CYCLING -> "bike"
    ActivityType.RUNNING, ActivityType.WALKING -> "foot"
}

fun durationSeconds(type: ActivityType, timeMillis: Long): Int {
    val seconds = timeMillis / 1000.0
    val adjusted = if (type == ActivityType.RUNNING) seconds * RUNNING_TIME_FACTOR else seconds
    return Math.round(adjusted).toInt()
}
