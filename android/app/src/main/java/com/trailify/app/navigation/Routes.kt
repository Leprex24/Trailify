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
    const val REGISTER = "register"
    const val PRIVACY_POLICY = "privacy-policy"
    const val ACCOUNT = "account"

    /** Ekrany dostępne bez logowania - wylogowanie nie przerzuca z nich na LOGIN. */
    val LOGGED_OUT = setOf(LOGIN, REGISTER, PRIVACY_POLICY)

    /** Ekrany logowania i rejestracji - po zalogowaniu przechodzimy z nich na mapę. */
    val SIGN_IN = setOf(LOGIN, REGISTER)

    const val ROUTE_ID_ARG = "routeId"
    const val ROUTE_DETAILS = "route/{$ROUTE_ID_ARG}"
    fun routeDetails(id: Long) = "route/$id"
}
