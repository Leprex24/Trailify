package com.trailify.backend.review

import com.trailify.backend.account.ProfileRecord
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
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** Recenzje tras: GET/POST /routes/{routeId}/reviews, DELETE /reviews/{reviewId}. */
@RestController
@RequestMapping("/api/v1")
class ReviewController(private val service: ReviewService) {

    /** Publiczna lista (token nie jest potrzebny); z tokenem właściciel widzi też recenzje swojej trasy prywatnej. */
    @GetMapping("/routes/{routeId}/reviews")
    fun list(
        @PathVariable routeId: Long,
        @RequestParam("page", defaultValue = "0") page: Int,
        @RequestParam("size", defaultValue = "$DEFAULT_PAGE_SIZE") size: Int,
        authentication: Authentication?,
    ): PageResponse<ReviewResponse> = service.list(authentication.profileOrNull(), routeId, PageRequest.of(page, size))

    @PostMapping("/routes/{routeId}/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @PathVariable routeId: Long,
        @Valid @RequestBody body: ReviewCreateRequest,
        authentication: Authentication,
    ): ReviewResponse = service.create(authentication.currentProfile(), routeId, body)

    @DeleteMapping("/reviews/{reviewId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable reviewId: Long, authentication: Authentication) {
        service.delete(authentication.currentProfile(), reviewId)
    }
}

private fun Authentication?.profileOrNull(): ProfileRecord? = (this as? ProfileAuthentication)?.profile
