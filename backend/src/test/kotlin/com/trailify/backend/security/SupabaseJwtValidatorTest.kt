package com.trailify.backend.security

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.jwt.Jwt
import java.time.Duration
import java.time.Instant

class SupabaseJwtValidatorTest {

    private val issuer = "https://abc.supabase.co/auth/v1"
    private val validator = supabaseJwtValidator(issuer)

    private fun token(
        iss: String = issuer,
        aud: List<String> = listOf("authenticated"),
        expiresIn: Duration = Duration.ofHours(1),
    ): Jwt {
        val now = Instant.now()
        return Jwt.withTokenValue("test-token")
            .header("alg", "ES256")
            .claim("iss", iss)
            .claim("aud", aud)
            .subject("00000000-0000-0000-0000-000000000001")
            .issuedAt(now.minus(Duration.ofHours(2)))
            .expiresAt(now.plus(expiresIn))
            .build()
    }

    @Test
    fun `poprawny token jest akceptowany`() {
        assertThat(validator.validate(token()).hasErrors()).isFalse()
    }

    @Test
    fun `token z cudzego projektu jest odrzucany`() {
        val result = validator.validate(token(iss = "https://inny.supabase.co/auth/v1"))
        assertThat(result.hasErrors()).isTrue()
    }

    @Test
    fun `token z inna wartoscia aud jest odrzucany`() {
        val result = validator.validate(token(aud = listOf("service_role")))
        assertThat(result.hasErrors()).isTrue()
    }

    @Test
    fun `wygasniety token jest odrzucany`() {
        val result = validator.validate(token(expiresIn = Duration.ofHours(-1)))
        assertThat(result.hasErrors()).isTrue()
    }
}
