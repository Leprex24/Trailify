package com.trailify.backend.activity

import com.trailify.backend.common.DEFAULT_PAGE_SIZE
import com.trailify.backend.common.PageRequest
import com.trailify.backend.common.PageResponse
import com.trailify.backend.security.currentProfile
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
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

@RestController
@RequestMapping("/api/v1")
class ActivityController(private val service: ActivityService) {

    /** 201 przy nowej aktywności, 200 gdy aktywność z tym `clientId` już istnieje. */
    @PostMapping("/activities")
    fun create(
        @Valid @RequestBody body: ActivityCreateRequest,
        authentication: Authentication,
    ): ResponseEntity<ActivityResponse> {
        val result = service.create(authentication.currentProfile().id, body)
        val status = if (result.created) HttpStatus.CREATED else HttpStatus.OK
        return ResponseEntity.status(status).body(result.activity)
    }

    @GetMapping("/activities/{activityId}")
    fun get(@PathVariable activityId: Long, authentication: Authentication): ActivityResponse =
        service.get(authentication.currentProfile().id, activityId)

    @GetMapping("/activities/{activityId}/points")
    fun points(@PathVariable activityId: Long, authentication: Authentication): List<GpsPointResponse> =
        service.points(authentication.currentProfile().id, activityId)

    @DeleteMapping("/activities/{activityId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable activityId: Long, authentication: Authentication) {
        service.delete(authentication.currentProfile().id, activityId)
    }

    @GetMapping("/me/activities")
    fun mine(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "$DEFAULT_PAGE_SIZE") size: Int,
        authentication: Authentication,
    ): PageResponse<ActivitySummaryResponse> =
        service.listMine(authentication.currentProfile().id, PageRequest.of(page, size))
}
