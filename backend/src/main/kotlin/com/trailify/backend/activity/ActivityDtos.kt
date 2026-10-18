package com.trailify.backend.activity

import com.fasterxml.jackson.annotation.JsonProperty
import com.trailify.backend.common.ActivityType
import com.trailify.backend.common.GeoJsonLineString
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import java.time.Instant
import java.util.UUID

/** Maksymalna liczba punktów GPS w jednej aktywności (ok. 14 godzin przy jednym punkcie na sekundę). */
const val MAX_POINTS = 50_000

/**
 * Punkt GPS w żądaniu. Pola są nullable tylko po to, żeby brak pola dawał czytelny błąd walidacji
 * (VALIDATION_ERROR) zamiast błędu odczytu JSON.
 */
data class GpsPointRequest(
    @field:NotNull @field:DecimalMin("-90") @field:DecimalMax("90") val latitude: Double?,
    @field:NotNull @field:DecimalMin("-180") @field:DecimalMax("180") val longitude: Double?,
    val altitude: Double?,
    @field:PositiveOrZero val speed: Double?,
    @field:NotNull val recordedAt: Instant?,
    // jawna nazwa, bo Jackson mógłby uciąć przedrostek "is" z nazwy pola
    @param:JsonProperty("isReconstructed") val isReconstructed: Boolean? = false,
)

/** Schemat ActivityCreate z openapi.yaml. */
data class ActivityCreateRequest(
    @field:NotNull val clientId: UUID?,
    @field:NotNull val type: ActivityType?,
    val routeId: Long?,
    @field:NotNull val startTime: Instant?,
    @field:NotNull val endTime: Instant?,
    @field:PositiveOrZero val distance: Double?,
    @field:PositiveOrZero val durationSeconds: Int?,
    @field:PositiveOrZero val avgPace: Double?,
    @field:PositiveOrZero val stepCount: Int?,
    @field:NotNull
    @field:Size(min = 2, max = MAX_POINTS, message = "Aktywność musi mieć od 2 do $MAX_POINTS punktów")
    @field:Valid
    val points: List<GpsPointRequest>?,
)

/** Schemat ActivitySummary z openapi.yaml (lista aktywności). */
data class ActivitySummaryResponse(
    val id: Long,
    val type: String,
    val routeId: Long?,
    val startTime: String,
    val endTime: String?,
    val distance: Double?,
    val durationSeconds: Int?,
    val avgPace: Double?,
    val stepCount: Int?,
)

/** Schemat Activity z openapi.yaml (szczegóły z geometrią). */
data class ActivityResponse(
    val id: Long,
    val type: String,
    val routeId: Long?,
    val startTime: String,
    val endTime: String?,
    val distance: Double?,
    val durationSeconds: Int?,
    val avgPace: Double?,
    val stepCount: Int?,
    val geometry: GeoJsonLineString,
)

/** Schemat GpsPoint z openapi.yaml. */
data class GpsPointResponse(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
    val speed: Double?,
    val recordedAt: String,
    @get:JsonProperty("isReconstructed") val isReconstructed: Boolean,
)
