package com.trailify.backend.social

import com.trailify.backend.common.DEFAULT_PAGE_SIZE
import com.trailify.backend.common.PageRequest
import com.trailify.backend.common.PageResponse
import com.trailify.backend.poi.PoiCollectionEntryResponse
import com.trailify.backend.route.RouteSummaryResponse
import com.trailify.backend.route.UserSummaryResponse
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
import java.util.UUID

/** Cudze profile i obserwowanie: /users/{id}, /users/{id}/follow, .../followers, .../following, .../routes, .../poi-collection. */
@RestController
@RequestMapping("/api/v1/users/{userId}")
class SocialController(private val service: SocialService) {

    @GetMapping
    fun profile(@PathVariable userId: UUID, authentication: Authentication): PublicProfileResponse =
        service.profileOf(authentication.currentProfile(), userId)

    @PutMapping("/follow")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun follow(@PathVariable userId: UUID, authentication: Authentication) {
        service.follow(authentication.currentProfile(), userId)
    }

    @DeleteMapping("/follow")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unfollow(@PathVariable userId: UUID, authentication: Authentication) {
        service.unfollow(authentication.currentProfile(), userId)
    }

    @GetMapping("/followers")
    fun followers(
        @PathVariable userId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "$DEFAULT_PAGE_SIZE") size: Int,
        authentication: Authentication,
    ): PageResponse<UserSummaryResponse> =
        service.followers(authentication.currentProfile(), userId, PageRequest.of(page, size))

    @GetMapping("/following")
    fun following(
        @PathVariable userId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "$DEFAULT_PAGE_SIZE") size: Int,
        authentication: Authentication,
    ): PageResponse<UserSummaryResponse> =
        service.following(authentication.currentProfile(), userId, PageRequest.of(page, size))

    @GetMapping("/routes")
    fun routes(
        @PathVariable userId: UUID,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "$DEFAULT_PAGE_SIZE") size: Int,
        authentication: Authentication,
    ): PageResponse<RouteSummaryResponse> =
        service.routesOf(authentication.currentProfile(), userId, PageRequest.of(page, size))

    @GetMapping("/poi-collection")
    fun collection(@PathVariable userId: UUID, authentication: Authentication): List<PoiCollectionEntryResponse> =
        service.collectionOf(authentication.currentProfile(), userId)
}
