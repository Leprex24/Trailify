package com.trailify.backend.activity

import com.trailify.backend.web.ApiException
import com.trailify.backend.web.ApiFieldError
import org.springframework.http.HttpStatus
import java.time.Instant

/**
 * Przedział czasu na liście aktywności (GET /me/activities?from=...&to=...), np. jeden miesiąc do statystyk.
 * [from] jest włącznie, [to] wyłącznie, więc kolejne miesiące się nie nakładają. Brak granicy = bez ograniczenia.
 */
data class ActivityRange(val from: Instant? = null, val to: Instant? = null) {
    companion object {
        val ALL = ActivityRange()
    }
}

/** Sprawdza parametry from/to; odwrócony albo pusty przedział to błąd 400. */
fun activityRange(from: Instant?, to: Instant?): ActivityRange {
    if (from != null && to != null && !from.isBefore(to)) {
        throw ApiException(
            HttpStatus.BAD_REQUEST,
            "VALIDATION_ERROR",
            "Niepoprawne dane",
            listOf(ApiFieldError("from", "Początek przedziału musi być wcześniejszy niż koniec")),
        )
    }
    return ActivityRange(from, to)
}
