package com.trailify.backend.web

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

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
    val fieldErrors: List<ApiFieldError>? = null,
) : RuntimeException(message)

@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(ApiException::class)
    fun handleApi(e: ApiException): ResponseEntity<ApiError> =
        ResponseEntity.status(e.status).body(ApiError(e.code, e.message ?: e.code, e.fieldErrors))

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(e: MethodArgumentNotValidException): ResponseEntity<ApiError> {
        val fields = e.bindingResult.fieldErrors.map {
            ApiFieldError(it.field, it.defaultMessage ?: "Niepoprawna wartość")
        }
        return ResponseEntity.badRequest()
            .body(ApiError("VALIDATION_ERROR", "Niepoprawne dane", fields))
    }

    /** Uszkodzony JSON, brakujące pole obowiązkowe w typie albo nieznana wartość enuma. */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadable(e: HttpMessageNotReadableException): ResponseEntity<ApiError> =
        ResponseEntity.badRequest()
            .body(ApiError("VALIDATION_ERROR", "Nie można odczytać treści żądania (niepoprawny JSON lub wartość)"))

    /** Np. identyfikator w adresie, który nie jest liczbą. */
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(e: MethodArgumentTypeMismatchException): ResponseEntity<ApiError> =
        ResponseEntity.badRequest()
            .body(ApiError("VALIDATION_ERROR", "Niepoprawna wartość parametru ${e.name}"))

    // Celowo nie ma tu ogólnego @ExceptionHandler(Exception::class): przechwyciłby
    // wyjątki Spring Security (401/403) i zamieniłby je w błąd 500.
}
