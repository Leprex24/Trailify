package com.trailify.backend.social

import com.trailify.backend.common.DEFAULT_PAGE_SIZE
import com.trailify.backend.common.PageRequest
import com.trailify.backend.common.PageResponse
import com.trailify.backend.route.UserSummaryResponse
import com.trailify.backend.security.currentProfile
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** Wyszukiwanie osób (GET /users?query=) i usuwanie własnych obserwujących (DELETE /me/followers/{id}). */
@RestController
@RequestMapping("/api/v1")
class PeopleController(private val service: SocialService) {

    @GetMapping("/users")
    fun search(
        @RequestParam(required = false) query: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "$DEFAULT_PAGE_SIZE") size: Int,
        authentication: Authentication,
    ): PageResponse<UserSummaryResponse> =
        service.search(authentication.currentProfile(), query, PageRequest.of(page, size))

    /** Obserwujący przestaje obserwować zalogowanego użytkownika (nie jest to odobserwowanie przez niego). */
    @DeleteMapping("/me/followers/{followerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removeFollower(@PathVariable followerId: UUID, authentication: Authentication) {
        service.removeFollower(authentication.currentProfile(), followerId)
    }
}
