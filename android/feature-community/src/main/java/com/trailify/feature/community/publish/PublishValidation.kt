package com.trailify.feature.community.publish

// Granice zgodne ze schematem RouteCreate w docs/openapi.yaml.
const val ROUTE_NAME_MAX = 255
const val ROUTE_DESCRIPTION_MAX = 5000
const val ROUTE_PHOTOS_MAX = 10

fun validateRouteName(name: String): String? {
    val trimmed = name.trim()
    return when {
        trimmed.isEmpty() -> "Podaj nazwę trasy"
        trimmed.length > ROUTE_NAME_MAX -> "Nazwa może mieć maksymalnie $ROUTE_NAME_MAX znaków"
        else -> null
    }
}

fun validateRouteDescription(description: String): String? =
    if (description.trim().length > ROUTE_DESCRIPTION_MAX) {
        "Opis może mieć maksymalnie $ROUTE_DESCRIPTION_MAX znaków"
    } else {
        null
    }
