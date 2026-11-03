package com.trailify.backend.review

import com.trailify.backend.route.validatePhotoInputs
import com.trailify.backend.web.ApiException
import com.trailify.backend.web.ApiFieldError
import org.springframework.http.HttpStatus
import java.util.UUID

// Kody błędów POST /routes/{id}/reviews (opisane w openapi.yaml).
const val CODE_OWN_ROUTE = "OWN_ROUTE"
const val CODE_ROUTE_NOT_COMPLETED = "ROUTE_NOT_COMPLETED"
const val CODE_ALREADY_REVIEWED = "ALREADY_REVIEWED"

/** Sprawdza treść recenzji poza adnotacjami (zdjęcia w folderze autora, https, bez powtórzeń). */
fun validateReviewCreate(userId: UUID, request: ReviewCreateRequest): List<ApiFieldError> =
    validatePhotoInputs(userId, request.photos.orEmpty())

/**
 * Zasady dodawania oceny (W20): właściciel nie ocenia własnej trasy, trzeba ją wcześniej przejść
 * (aktywność z routeId tej trasy) i można ocenić tylko raz. Zwraca wyjątek do rzucenia albo null.
 */
fun reviewRejection(isOwner: Boolean, completedRoute: Boolean, alreadyReviewed: Boolean): ApiException? = when {
    isOwner -> ApiException(HttpStatus.FORBIDDEN, CODE_OWN_ROUTE, "Nie można oceniać własnej trasy")
    alreadyReviewed -> alreadyReviewed()
    !completedRoute -> ApiException(
        HttpStatus.FORBIDDEN,
        CODE_ROUTE_NOT_COMPLETED,
        "Trasę można ocenić dopiero po jej przejściu (zapisanej aktywności z tą trasą)",
    )
    else -> null
}

fun alreadyReviewed() = ApiException(HttpStatus.CONFLICT, CODE_ALREADY_REVIEWED, "Ta trasa ma już Twoją ocenę")

/** Usunąć recenzję może jej autor albo administrator (W33). */
fun canDeleteReview(authorId: UUID, userId: UUID, isAdmin: Boolean): Boolean = isAdmin || authorId == userId
