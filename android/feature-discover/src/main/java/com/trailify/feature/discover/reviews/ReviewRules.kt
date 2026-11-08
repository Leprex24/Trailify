package com.trailify.feature.discover.reviews

import com.trailify.core.domain.model.Review

// Granice zgodne ze schematem ReviewCreate w docs/openapi.yaml.
const val REVIEW_COMMENT_MAX = 2000
const val REVIEW_PHOTOS_MAX = 5

/** Czy zalogowany użytkownik może ocenić trasę (zasady z opisu POST /routes/{id}/reviews). */
enum class ReviewEligibility {
    /** Jeszcze sprawdzamy (pobieranie recenzji i aktywności). */
    CHECKING,
    CAN_REVIEW,

    /** Właściciel nie ocenia własnej trasy. */
    OWN_ROUTE,

    /** Jedna recenzja użytkownika na trasę. */
    ALREADY_REVIEWED,

    /** Ocenić można dopiero po przejściu trasy (aktywność z routeId tej trasy). */
    NOT_COMPLETED,
    LOGGED_OUT,
}

/**
 * Wylicza, czy pokazać przycisk "Oceń trasę". [completedRoute] = null, gdy nie udało się sprawdzić
 * aktywności (np. brak sieci): wtedy pozwalamy spróbować, a ostatecznie decyduje serwer.
 */
fun decideEligibility(
    currentUserId: String?,
    ownerId: String,
    reviews: List<Review>,
    completedRoute: Boolean?,
): ReviewEligibility = when {
    currentUserId == null -> ReviewEligibility.LOGGED_OUT
    currentUserId == ownerId -> ReviewEligibility.OWN_ROUTE
    reviews.any { it.user.id == currentUserId } -> ReviewEligibility.ALREADY_REVIEWED
    completedRoute == false -> ReviewEligibility.NOT_COMPLETED
    else -> ReviewEligibility.CAN_REVIEW
}

fun validateRating(rating: Int): String? = if (rating in 1..5) null else "Wybierz ocenę od 1 do 5 gwiazdek"

fun validateReviewComment(comment: String): String? =
    if (comment.trim().length > REVIEW_COMMENT_MAX) "Komentarz może mieć maksymalnie $REVIEW_COMMENT_MAX znaków" else null
