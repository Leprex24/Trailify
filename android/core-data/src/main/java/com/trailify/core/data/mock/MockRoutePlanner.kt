package com.trailify.core.data.mock

import com.trailify.core.domain.model.ActivityType
import com.trailify.core.domain.model.LatLng
import com.trailify.core.domain.model.PlannedRoute
import com.trailify.core.domain.recording.distanceMeters
import com.trailify.core.domain.repository.RoutePlanner
import kotlinx.coroutines.delay

/** Atrapa bez backendu: łączy punkty prostymi liniami i szacuje czas ze średniej prędkości. */
class MockRoutePlanner : RoutePlanner {
    override suspend fun plan(type: ActivityType, waypoints: List<LatLng>): PlannedRoute {
        delay(300)
        val distance = waypoints.zipWithNext { a, b -> distanceMeters(a, b) }.sum()
        val speedMps = when (type) {
            ActivityType.CYCLING -> 4.2
            ActivityType.RUNNING -> 2.8
            ActivityType.WALKING -> 1.4
        }
        return PlannedRoute(distance = distance, durationSeconds = (distance / speedMps).toInt(), geometry = waypoints)
    }
}
