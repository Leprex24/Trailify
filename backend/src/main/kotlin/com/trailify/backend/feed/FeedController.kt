package com.trailify.backend.feed

import com.trailify.backend.common.DEFAULT_PAGE_SIZE
import com.trailify.backend.common.PageRequest
import com.trailify.backend.common.PageResponse
import com.trailify.backend.security.currentProfile
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** Oś czasu: GET /feed, PUT/DELETE /feed/{id}/like, GET/POST /feed/{id}/comments, DELETE /feed/comments/{id}. */
@RestController
@RequestMapping("/api/v1/feed")
class FeedController(private val service: FeedService) {

    @GetMapping
    fun feed(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "$DEFAULT_PAGE_SIZE") size: Int,
        authentication: Authentication,
    ): PageResponse<FeedItemResponse> = service.feed(authentication.currentProfile(), PageRequest.of(page, size))

    @PutMapping("/{eventId}/like")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun like(@PathVariable eventId: Long, authentication: Authentication) {
        service.like(authentication.currentProfile(), eventId)
    }

    @DeleteMapping("/{eventId}/like")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unlike(@PathVariable eventId: Long, authentication: Authentication) {
        service.unlike(authentication.currentProfile(), eventId)
    }

    @GetMapping("/{eventId}/comments")
    fun comments(
        @PathVariable eventId: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "$DEFAULT_PAGE_SIZE") size: Int,
    ): PageResponse<CommentResponse> = service.comments(eventId, PageRequest.of(page, size))

    @PostMapping("/{eventId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    fun addComment(
        @PathVariable eventId: Long,
        @Valid @RequestBody body: CommentCreateRequest,
        authentication: Authentication,
    ): CommentResponse = service.addComment(authentication.currentProfile(), eventId, body)

    @DeleteMapping("/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteComment(@PathVariable commentId: Long, authentication: Authentication) {
        service.deleteComment(authentication.currentProfile(), commentId)
    }
}
