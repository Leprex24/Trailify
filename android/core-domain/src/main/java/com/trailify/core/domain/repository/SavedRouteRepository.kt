package com.trailify.core.domain.repository

import com.trailify.core.domain.model.Page
import com.trailify.core.domain.model.RouteSummary

/** Trasy zapisane gwiazdką: /me/saved-routes, /routes/{id}/saved. Zapisać można publiczną trasę innej osoby. */
interface SavedRouteRepository {
    suspend fun getSaved(page: Int = 0, size: Int = 100): Page<RouteSummary>

    suspend fun isSaved(routeId: Long): Boolean

    /** [saved] = true zapisuje trasę, false ją odznacza. Własna trasa daje AppException("OWN_ROUTE"). */
    suspend fun setSaved(routeId: Long, saved: Boolean)
}
