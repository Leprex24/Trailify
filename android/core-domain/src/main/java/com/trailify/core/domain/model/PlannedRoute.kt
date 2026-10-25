package com.trailify.core.domain.model

/** Trasa wyznaczona po drogach między punktami zaznaczonymi na mapie (POST /routing/plan). */
data class PlannedRoute(
    val distance: Double, // metry
    val durationSeconds: Int,
    val geometry: List<LatLng>,
)
