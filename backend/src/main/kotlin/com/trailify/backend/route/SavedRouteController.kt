package com.trailify.backend.route

import com.trailify.backend.common.DEFAULT_PAGE_SIZE
import com.trailify.backend.common.PageRequest
import com.trailify.backend.common.PageResponse
import com.trailify.backend.security.currentProfile
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** Odpowiedź GET /routes/{id}/saved. */
data class SavedStateResponse(val saved: Boolean)

/** Zapisane trasy (gwiazdka): PUT/DELETE/GET /routes/{routeId}/saved i GET /me/saved-routes. Wszystko wymaga logowania. */
@RestController
@RequestMapping("/api/v1")
class SavedRouteController(private val service: RouteService) {

    @PutMapping("/routes/{routeId}/saved")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun save(@PathVariable routeId: Long, authentication: Authentication) {
        service.setSaved(authentication.currentProfile(), routeId, saved = true)
    }

    @DeleteMapping("/routes/{routeId}/saved")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unsave(@PathVariable routeId: Long, authentication: Authentication) {
        service.setSaved(authentication.currentProfile(), routeId, saved = false)
    }

    @GetMapping("/routes/{routeId}/saved")
    fun isSaved(@PathVariable routeId: Long, authentication: Authentication): SavedStateResponse =
        SavedStateResponse(service.isSaved(authentication.currentProfile().id, routeId))

    @GetMapping("/me/saved-routes")
    fun mine(
        @RequestParam("page", defaultValue = "0") page: Int,
        @RequestParam("size", defaultValue = "$DEFAULT_PAGE_SIZE") size: Int,
        authentication: Authentication,
    ): PageResponse<RouteSummaryResponse> =
        service.listSaved(authentication.currentProfile().id, PageRequest.of(page, size))
}
