package com.trailify.backend.route

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonUnwrapped
import com.trailify.backend.common.ActivityType
import com.trailify.backend.common.GeoJsonLineString
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

/** Maksymalna liczba punktów geometrii jednej trasy (tyle samo co w aktywności). */
const val MAX_ROUTE_POINTS = 50_000
const val MAX_ROUTE_PHOTOS = 10

/** Krótsza trasa nie ma sensu (np. przypadkowe dwa dotknięcia mapy w tym samym miejscu). */
const val MIN_ROUTE_LENGTH_METERS = 10.0

/** Schemat PhotoInput z openapi.yaml. Plik leży już w Supabase Storage, tu przychodzą tylko metadane. */
data class PhotoInputRequest(
    @field:NotBlank @field:Size(max = 512) val storageKey: String?,
    @field:NotBlank @field:Size(max = 1024) val url: String?,
)

/**
 * Schemat RouteCreate z openapi.yaml. Pola są nullable tylko po to, żeby brak pola dawał
 * czytelny błąd walidacji (VALIDATION_ERROR) zamiast błędu odczytu JSON.
 */
data class RouteCreateRequest(
    @field:NotBlank @field:Size(max = 255) val name: String?,
    @field:Size(max = 5000) val description: String?,
    @field:NotNull val activityType: ActivityType?,
    // jawne nazwy, bo Jackson mógłby uciąć przedrostek "is" z nazwy pola
    @field:NotNull @param:JsonProperty("isTourist") val isTourist: Boolean?,
    @field:NotNull @param:JsonProperty("isScenic") val isScenic: Boolean?,
    @field:NotNull @param:JsonProperty("isPublic") val isPublic: Boolean?,
    val activityId: Long?,
    val geometry: GeoJsonLineString?,
    @field:Size(max = MAX_ROUTE_PHOTOS, message = "Trasa może mieć najwyżej $MAX_ROUTE_PHOTOS zdjęć")
    @field:Valid
    val photos: List<PhotoInputRequest>?,
)

/** Schemat UserSummary z openapi.yaml. */
data class UserSummaryResponse(
    val id: String,
    val displayName: String,
    val avatarUrl: String?,
)

data class LatLngResponse(val latitude: Double, val longitude: Double)

data class PhotoResponse(val id: Long, val url: String)

/** Schemat RouteSummary z openapi.yaml (lista tras). */
data class RouteSummaryResponse(
    val id: Long,
    val name: String,
    val activityType: String,
    @get:JsonProperty("isTourist") val isTourist: Boolean,
    @get:JsonProperty("isScenic") val isScenic: Boolean,
    @get:JsonProperty("isPublic") val isPublic: Boolean,
    val totalDistance: Double?,
    val durationSeconds: Int?,
    val averageRating: Double?,
    val reviewCount: Int,
    val recommendationScore: Double?,
    val startPoint: LatLngResponse?,
    val thumbnailUrl: String?,
    val distanceFromQuery: Double?,
    val owner: UserSummaryResponse,
    val createdAt: String,
)

/** Schemat Route z openapi.yaml: pola RouteSummary plus opis, geometria i zdjęcia. */
data class RouteResponse(
    @get:JsonUnwrapped val summary: RouteSummaryResponse,
    val description: String?,
    val geometry: GeoJsonLineString,
    val photos: List<PhotoResponse>,
)
