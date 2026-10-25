package com.trailify.backend.routing

import com.trailify.backend.common.ActivityType
import com.trailify.backend.common.GeoJsonLineString
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

/** Maksymalna liczba punktów trasy (start, punkty pośrednie, koniec) w jednym zapytaniu. */
const val MAX_PLAN_POINTS = 25

/** Punkt trasy. Pola nullable tylko po to, by brak pola dawał czytelny błąd walidacji. */
data class PlanPointRequest(
    @field:NotNull @field:DecimalMin("-90") @field:DecimalMax("90") val latitude: Double?,
    @field:NotNull @field:DecimalMin("-180") @field:DecimalMax("180") val longitude: Double?,
)

/** Schemat RoutePlanRequest z openapi.yaml. */
data class RoutePlanRequest(
    @field:NotNull val profile: ActivityType?,
    @field:NotNull
    @field:Size(min = 2, max = MAX_PLAN_POINTS, message = "Trasa musi mieć od 2 do $MAX_PLAN_POINTS punktów")
    @field:Valid
    val points: List<PlanPointRequest>?,
)

/** Schemat RoutePlan z openapi.yaml: trasa poprowadzona po drogach i ścieżkach. */
data class RoutePlanResponse(
    /** Metry. */
    val distance: Double,
    /** Szacowany czas przejścia w sekundach (dla biegu liczony jako dwa razy szybciej niż spacer). */
    val durationSeconds: Int,
    val geometry: GeoJsonLineString,
)
