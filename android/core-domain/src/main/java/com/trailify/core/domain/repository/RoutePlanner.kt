package com.trailify.core.domain.repository

import com.trailify.core.domain.model.ActivityType
import com.trailify.core.domain.model.LatLng
import com.trailify.core.domain.model.PlannedRoute

/**
 * Wyznacza trasę po drogach i ścieżkach przez podane punkty (od 2 do 25), dla wybranego rodzaju aktywności.
 * Rzuca AppException, np. ROUTE_NOT_FOUND (brak połączenia drogowego), ROUTING_UNAVAILABLE, NETWORK.
 */
interface RoutePlanner {
    suspend fun plan(type: ActivityType, waypoints: List<LatLng>): PlannedRoute
}

/** Maksymalna liczba punktów w jednym zapytaniu, tyle samo co po stronie backendu. */
const val MAX_PLAN_POINTS = 25
