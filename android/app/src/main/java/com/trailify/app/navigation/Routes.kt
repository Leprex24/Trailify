package com.trailify.app.navigation

object Routes {
    // zakładki dolnego paska
    const val DISCOVER = "discover"
    const val ROUTES = "routes"
    const val MAP = "map" // zakładka START
    const val COMMUNITY = "community"
    const val PROFILE = "profile"

    // pozostałe ekrany
    const val LOGIN = "login"
    const val RECORDING = "recording"

    const val ROUTE_ID_ARG = "routeId"
    const val ROUTE_DETAILS = "route/{$ROUTE_ID_ARG}"
    fun routeDetails(id: Long) = "route/$id"
}
