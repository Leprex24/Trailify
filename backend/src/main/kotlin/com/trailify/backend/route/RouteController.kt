package com.trailify.backend.route

import com.trailify.backend.account.ProfileRecord
import com.trailify.backend.common.ActivityType
import com.trailify.backend.common.DEFAULT_PAGE_SIZE
import com.trailify.backend.common.PageRequest
import com.trailify.backend.common.PageResponse
import com.trailify.backend.security.ProfileAuthentication
import com.trailify.backend.security.currentProfile
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
class RouteController(private val service: RouteService) {

    @PostMapping("/routes")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody body: RouteCreateRequest, authentication: Authentication): RouteResponse =
        service.create(authentication.currentProfile(), body)

    /** Publiczne wyszukiwanie tras (token nie jest potrzebny). */
    @GetMapping("/routes")
    @Suppress("LongParameterList")
    fun search(
        @RequestParam("lat", required = false) lat: Double?,
        @RequestParam("lng", required = false) lng: Double?,
        @RequestParam("radiusKm", required = false) radiusKm: Double?,
        @RequestParam("activityType", required = false) activityType: List<ActivityType>?,
        @RequestParam("minDistance", required = false) minDistance: Double?,
        @RequestParam("maxDistance", required = false) maxDistance: Double?,
        @RequestParam("minRating", required = false) minRating: Double?,
        @RequestParam("isTourist", required = false) isTourist: Boolean?,
        @RequestParam("isScenic", required = false) isScenic: Boolean?,
        @RequestParam("q", required = false) q: String?,
        @RequestParam("sort", required = false) sort: String?,
        @RequestParam("page", defaultValue = "0") page: Int,
        @RequestParam("size", defaultValue = "$DEFAULT_PAGE_SIZE") size: Int,
    ): PageResponse<RouteSummaryResponse> {
        val filter = buildSearchFilter(
            lat, lng, radiusKm, activityType, minDistance, maxDistance, minRating, isTourist, isScenic, q, sort,
        )
        return service.search(filter, PageRequest.of(page, size))
    }

    /** Publiczny odczyt; z tokenem właściciel widzi też swoją trasę prywatną. */
    @GetMapping("/routes/{routeId}")
    fun get(@PathVariable routeId: Long, authentication: Authentication?): RouteResponse =
        service.get(authentication.profileOrNull(), routeId)

    @PatchMapping("/routes/{routeId}")
    fun update(
        @PathVariable routeId: Long,
        @RequestBody body: Map<String, Any?>,
        authentication: Authentication,
    ): RouteResponse = service.update(authentication.currentProfile(), routeId, body)

    @DeleteMapping("/routes/{routeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable routeId: Long, authentication: Authentication) {
        service.delete(authentication.currentProfile(), routeId)
    }

    @GetMapping("/me/routes")
    fun mine(
        @RequestParam("page", defaultValue = "0") page: Int,
        @RequestParam("size", defaultValue = "$DEFAULT_PAGE_SIZE") size: Int,
        authentication: Authentication,
    ): PageResponse<RouteSummaryResponse> =
        service.listMine(authentication.currentProfile().id, PageRequest.of(page, size))
}

/** Zalogowany użytkownik albo null, gdy żądanie przyszło bez tokena (endpointy publiczne). */
private fun Authentication?.profileOrNull(): ProfileRecord? = (this as? ProfileAuthentication)?.profile
