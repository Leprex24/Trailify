package com.trailify.backend.security

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtValidators

/** Wartość `aud` w tokenach użytkowników Supabase Auth. */
const val SUPABASE_AUDIENCE = "authenticated"

/**
 * Reguły, które musi spełnić token z Supabase:
 *  - poprawny podpis (sprawdza dekoder na podstawie JWKS),
 *  - token nie wygasł i nie jest jeszcze nieważny (domyślne walidatory Spring Security),
 *  - `iss` zgadza się z naszym projektem Supabase (https://<ID>.supabase.co/auth/v1),
 *  - `aud` zawiera "authenticated".
 */
fun supabaseJwtValidator(issuer: String): OAuth2TokenValidator<Jwt> =
    DelegatingOAuth2TokenValidator<Jwt>(
        JwtValidators.createDefaultWithIssuer(issuer),
        AudienceValidator(SUPABASE_AUDIENCE),
    )

class AudienceValidator(private val expectedAudience: String) : OAuth2TokenValidator<Jwt> {
    override fun validate(token: Jwt): OAuth2TokenValidatorResult =
        if (token.audience?.contains(expectedAudience) == true) {
            OAuth2TokenValidatorResult.success()
        } else {
            OAuth2TokenValidatorResult.failure(
                OAuth2Error("invalid_token", "Nieprawidłowy odbiorca tokena (aud)", null),
            )
        }
}
