package com.trailify.backend.web

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/** Format błędu z openapi.yaml (schemat Error). */
data class ApiError(
    val code: String,
    val message: String,
    val fieldErrors: List<ApiFieldError>? = null,
)

data class ApiFieldError(val field: String, val message: String)

/**
 * Wyjątek do rzucania w serwisach i kontrolerach, np.:
 *   throw ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Trasa nie istnieje")
 */
class ApiException(
    val status: HttpStatus,
    val code: String,
    message: String,
) : RuntimeException(message)

@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(ApiException::class)
    fun handleApi(e: ApiException): ResponseEntity<ApiError> =
        ResponseEntity.status(e.status).body(ApiError(e.code, e.message ?: e.code))

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(e: MethodArgumentNotValidException): ResponseEntity<ApiError> {
        val fields = e.bindingResult.fieldErrors.map {
            ApiFieldError(it.field, it.defaultMessage ?: "Niepoprawna wartość")
        }
        return ResponseEntity.badRequest()
            .body(ApiError("VALIDATION_ERROR", "Niepoprawne dane", fields))
    }

    // Celowo nie ma tu ogólnego @ExceptionHandler(Exception::class): przechwyciłby
    // wyjątki Spring Security (401/403) i zamieniłby je w błąd 500.
}
