package com.trailify.backend.common

import com.trailify.backend.web.ApiException
import com.trailify.backend.web.ApiFieldError
import org.springframework.http.HttpStatus

/** Odpowiedź ze stroną wyników (PageMeta + items z openapi.yaml). */
data class PageResponse<T>(
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val items: List<T>,
)

const val DEFAULT_PAGE_SIZE = 20
const val MAX_PAGE_SIZE = 100

/** Parametry `page` (od 0) i `size` (1..100) z zapytania. */
data class PageRequest(val page: Int, val size: Int) {
    val offset: Long get() = page.toLong() * size

    companion object {
        fun of(page: Int, size: Int): PageRequest {
            val errors = buildList {
                if (page < 0) add(ApiFieldError("page", "Numer strony nie może być ujemny"))
                if (size < 1 || size > MAX_PAGE_SIZE) add(ApiFieldError("size", "Rozmiar strony musi mieścić się w zakresie 1-$MAX_PAGE_SIZE"))
            }
            if (errors.isNotEmpty()) {
                throw ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Niepoprawne dane", errors)
            }
            return PageRequest(page, size)
        }
    }
}

fun <T> pageOf(request: PageRequest, total: Long, items: List<T>) = PageResponse(
    page = request.page,
    size = request.size,
    totalElements = total,
    totalPages = ((total + request.size - 1) / request.size).toInt(),
    items = items,
)
