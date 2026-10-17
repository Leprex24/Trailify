package com.trailify.core.domain.model

/**
 * Błąd biznesowy z API. [code] to kod z odpowiedzi błędu (np. "POI_TOO_FAR",
 * "ACCOUNT_BLOCKED", "NOT_FOUND"), zgodny z docs/openapi.yaml.
 */
class AppException(
    val code: String,
    override val message: String,
) : Exception(message)
