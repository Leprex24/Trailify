package com.trailify.backend.routing

import com.trailify.backend.common.ActivityType
import com.trailify.backend.web.ApiException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

class RoutingServiceTest {

    private val route = EngineRoute(
        distanceMeters = 3000.0,
        timeMillis = 2_160_000, // 36 minut
        coordinates = listOf(listOf(22.5, 51.2), listOf(22.6, 51.3)),
    )

    private fun request(type: ActivityType = ActivityType.WALKING) = RoutePlanRequest(
        profile = type,
        points = listOf(PlanPointRequest(51.2, 22.5), PlanPointRequest(51.3, 22.6)),
    )

    @Test
    fun `profile silnika dla rodzajow aktywnosci`() {
        assertThat(engineProfile(ActivityType.CYCLING)).isEqualTo("bike")
        assertThat(engineProfile(ActivityType.WALKING)).isEqualTo("foot")
        assertThat(engineProfile(ActivityType.RUNNING)).isEqualTo("foot")
    }

    @Test
    fun `bieg jest dwa razy szybszy od spaceru`() {
        assertThat(durationSeconds(ActivityType.WALKING, 2_160_000)).isEqualTo(2160)
        assertThat(durationSeconds(ActivityType.RUNNING, 2_160_000)).isEqualTo(1080)
    }

    @Test
    fun `plan przekazuje punkty i profil do silnika`() {
        var usedProfile = ""
        var usedPoints = emptyList<LatLon>()
        val service = RoutingService { profile, points ->
            usedProfile = profile
            usedPoints = points
            route
        }
        val result = service.plan(request(ActivityType.CYCLING))
        assertThat(usedProfile).isEqualTo("bike")
        assertThat(usedPoints).containsExactly(LatLon(51.2, 22.5), LatLon(51.3, 22.6))
        assertThat(result.distance).isEqualTo(3000.0)
        assertThat(result.durationSeconds).isEqualTo(2160)
        assertThat(result.geometry.coordinates).hasSize(2)
    }

    @Test
    fun `brak trasy to 422`() {
        val service = RoutingService { _, _ -> throw RouteNotFoundException("brak") }
        assertThatThrownBy { service.plan(request()) }
            .isInstanceOfSatisfying(ApiException::class.java) {
                assertThat(it.status).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
                assertThat(it.code).isEqualTo("ROUTE_NOT_FOUND")
            }
    }

    @Test
    fun `niedostepny silnik to 503`() {
        val service = RoutingService { _, _ -> throw RoutingUnavailableException("padl") }
        assertThatThrownBy { service.plan(request()) }
            .isInstanceOfSatisfying(ApiException::class.java) {
                assertThat(it.status).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                assertThat(it.code).isEqualTo("ROUTING_UNAVAILABLE")
            }
    }

    @Test
    fun `parsowanie odpowiedzi GraphHopper`() {
        val body = mapOf(
            "paths" to listOf(
                mapOf(
                    "distance" to 3393.117,
                    "time" to 728518,
                    "points" to mapOf("type" to "LineString", "coordinates" to listOf(listOf(22.5, 51.2), listOf(22.6, 51.3))),
                ),
            ),
        )
        val parsed = parseGraphHopperResponse(body)
        assertThat(parsed.distanceMeters).isEqualTo(3393.117)
        assertThat(parsed.timeMillis).isEqualTo(728518L)
        assertThat(parsed.coordinates).hasSize(2)
    }

    @Test
    fun `odpowiedz bez trasy`() {
        assertThatThrownBy { parseGraphHopperResponse(mapOf("paths" to emptyList<Any>())) }
            .isInstanceOf(RouteNotFoundException::class.java)
    }
}
