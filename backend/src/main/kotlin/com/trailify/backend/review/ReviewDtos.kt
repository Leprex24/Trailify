package com.trailify.backend.review

import com.trailify.backend.route.PhotoInputRequest
import com.trailify.backend.route.PhotoResponse
import com.trailify.backend.route.UserSummaryResponse
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

const val REVIEW_COMMENT_MAX = 2000
const val MAX_REVIEW_PHOTOS = 5

/** Schemat ReviewCreate z openapi.yaml. Pola nullable, żeby brak pola dawał VALIDATION_ERROR. */
data class ReviewCreateRequest(
    @field:NotNull @field:Min(1) @field:Max(5) val rating: Int?,
    @field:Size(max = REVIEW_COMMENT_MAX) val comment: String?,
    @field:Size(max = MAX_REVIEW_PHOTOS, message = "Recenzja może mieć najwyżej $MAX_REVIEW_PHOTOS zdjęć")
    @field:Valid
    val photos: List<PhotoInputRequest>?,
)

/** Schemat Review z openapi.yaml. */
data class ReviewResponse(
    val id: Long,
    val routeId: Long,
    val user: UserSummaryResponse,
    val rating: Int,
    val comment: String?,
    val photos: List<PhotoResponse>,
    /** ISO 8601 w UTC. */
    val createdAt: String,
)
