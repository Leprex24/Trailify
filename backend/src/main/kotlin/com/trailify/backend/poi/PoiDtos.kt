package com.trailify.backend.poi

import com.fasterxml.jackson.annotation.JsonUnwrapped
import com.trailify.backend.route.PhotoInputRequest
import com.trailify.backend.route.UserSummaryResponse
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

const val POI_NAME_MAX = 255
const val POI_DESCRIPTION_MAX = 5000
const val REJECT_REASON_MAX = 500

/** Schemat PoiCreate z openapi.yaml (zgłoszenie użytkownika i dodanie przez administratora). */
data class PoiCreateRequest(
    @field:NotBlank @field:Size(max = POI_NAME_MAX) val name: String?,
    @field:NotBlank @field:Size(max = POI_DESCRIPTION_MAX) val description: String?,
    @field:NotNull @field:DecimalMin("-90") @field:DecimalMax("90") val latitude: Double?,
    @field:NotNull @field:DecimalMin("-180") @field:DecimalMax("180") val longitude: Double?,
    @field:Valid val photo: PhotoInputRequest?,
)

/** Schemat CollectRequest: aktualna pozycja użytkownika. */
data class CollectRequest(
    @field:NotNull @field:DecimalMin("-90") @field:DecimalMax("90") val latitude: Double?,
    @field:NotNull @field:DecimalMin("-180") @field:DecimalMax("180") val longitude: Double?,
)

/** Schemat RejectRequest. */
data class RejectRequest(@field:Size(max = REJECT_REASON_MAX) val reason: String?)

/** Schemat Poi. */
data class PoiResponse(
    val id: Long,
    val name: String,
    val description: String?,
    val latitude: Double,
    val longitude: Double,
    val photoUrl: String?,
)

/** Schemat PoiAdmin: Poi plus dane moderacji. */
data class PoiAdminResponse(
    @get:JsonUnwrapped val poi: PoiResponse,
    val status: String,
    val submittedBy: UserSummaryResponse?,
    val rejectionReason: String?,
    val createdAt: String,
)

/** Schemat PoiCollectionEntry. */
data class PoiCollectionEntryResponse(val poi: PoiResponse, val collectedAt: String)
