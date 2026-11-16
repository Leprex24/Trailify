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
    const val ACTIVITY_SUMMARY = "activity-summary"
    const val ADMIN = "admin"
    const val PROPOSE_POI = "poi-form/user"

    // propozycja punktu z mapy (przytrzymanie palca) niesie wybrane miejsce; z innych miejsc bez współrzędnych
    const val POI_LAT_ARG = "lat"
    const val POI_LNG_ARG = "lng"
    const val PROPOSE_POI_ROUTE = "$PROPOSE_POI?$POI_LAT_ARG={$POI_LAT_ARG}&$POI_LNG_ARG={$POI_LNG_ARG}"
    fun proposePoiAt(latitude: Double, longitude: Double) = "$PROPOSE_POI?$POI_LAT_ARG=$latitude&$POI_LNG_ARG=$longitude"
    const val ADMIN_ADD_POI = "poi-form/admin"

    /** Ekrany dostępne bez logowania - wylogowanie nie przerzuca z nich na LOGIN. */
    val LOGGED_OUT = setOf(LOGIN, REGISTER, PRIVACY_POLICY)

    /** Ekrany logowania i rejestracji - po zalogowaniu przechodzimy z nich na mapę. */
    val SIGN_IN = setOf(LOGIN, REGISTER)

    const val ACTIVITY_ID_ARG = "activityId"
    const val ACTIVITY_TYPE_ARG = "activityType"
    const val PUBLISH_ROUTE = "publish-route/{$ACTIVITY_ID_ARG}/{$ACTIVITY_TYPE_ARG}"
    fun publishRoute(activityId: Long, activityType: String) = "publish-route/$activityId/$activityType"
    // własna aktywność (domyślnie) albo udostępniona aktywność innej osoby (own=false)
    const val ACTIVITY_OWN_ARG = "own"
    const val ACTIVITY_DETAILS = "activity/{$ACTIVITY_ID_ARG}?$ACTIVITY_OWN_ARG={$ACTIVITY_OWN_ARG}"
    fun activityDetails(id: Long) = "activity/$id"
    fun otherActivityDetails(id: Long) = "activity/$id?$ACTIVITY_OWN_ARG=false"

    const val ROUTE_ID_ARG = "routeId"
    const val ROUTE_DETAILS = "route/{$ROUTE_ID_ARG}"
    fun routeDetails(id: Long) = "route/$id"

    // cudzy profil i listy osób (obserwujący / obserwowani)
    const val USER_ID_ARG = "userId"
    const val USER_LIST_KIND_ARG = "kind"
    const val USER_PROFILE = "user/{$USER_ID_ARG}"
    fun userProfile(id: String) = "user/$id"
    const val USER_SEARCH = "user-search"
    const val USER_LIST = "user/{$USER_ID_ARG}/{$USER_LIST_KIND_ARG}"
    fun userList(id: String, kind: String) = "user/$id/$kind"
}
