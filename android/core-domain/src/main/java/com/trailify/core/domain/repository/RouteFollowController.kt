package com.trailify.core.domain.repository

import com.trailify.core.domain.model.ActivityType
import com.trailify.core.domain.model.PlannedRoute
import kotlinx.coroutines.flow.StateFlow

/** Prośba "przejdź tę trasę": opublikowana trasa, którą mapa START ma pokazać i poprowadzić. */
data class FollowRequest(
    val routeId: Long,
    val name: String,
    val activityType: ActivityType,
    val route: PlannedRoute,
)

/**
 * Most między ekranem szczegółów trasy a mapą START. Szczegóły wołają [follow], mapa odbiera
 * prośbę z [request] i zaznacza ją jako [consume]. Dzięki temu moduły ekranów nie znają się nawzajem.
 */
interface RouteFollowController {
    val request: StateFlow<FollowRequest?>
    fun follow(request: FollowRequest)
    fun consume()
}
