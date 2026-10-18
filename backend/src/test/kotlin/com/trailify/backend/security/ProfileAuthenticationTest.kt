package com.trailify.backend.security

import com.trailify.backend.account.ProfileLookup
import com.trailify.backend.account.ProfileRecord
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID

class ProfileAuthenticationTest {

    private val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    private fun profile(role: String = "USER", status: String = "ACTIVE") = ProfileRecord(
        id = userId,
        email = "jan@example.com",
        displayName = "Jan",
        avatarUrl = null,
        role = role,
        status = status,
        createdAt = OffsetDateTime.now(),
    )

    private fun lookup(vararg records: ProfileRecord) = object : ProfileLookup {
        override fun findById(id: UUID): ProfileRecord? = records.firstOrNull { it.id == id }
    }

    private fun jwt(subject: String = userId.toString()): Jwt {
        val now = Instant.now()
        return Jwt.withTokenValue("test-token")
            .header("alg", "ES256")
            .claim("role", "authenticated")
            .subject(subject)
            .issuedAt(now)
            .expiresAt(now.plus(Duration.ofHours(1)))
            .build()
    }

    @AfterEach
    fun clearContext() = SecurityContextHolder.clearContext()

    @Test
    fun `zwykly uzytkownik dostaje role USER z bazy a nie z tokena`() {
        val converter = ProfileJwtAuthenticationConverter(lookup(profile(role = "USER")))
        val authentication = converter.convert(jwt())
        assertThat(authentication.authorities.map { it.authority }).containsExactly("ROLE_USER")
    }

    @Test
    fun `administrator dostaje role ADMIN`() {
        val converter = ProfileJwtAuthenticationConverter(lookup(profile(role = "ADMIN")))
        val authentication = converter.convert(jwt())
        assertThat(authentication.authorities.map { it.authority }).containsExactly("ROLE_ADMIN")
    }

    @Test
    fun `brak profilu w bazie konczy sie bledem uwierzytelnienia`() {
        val converter = ProfileJwtAuthenticationConverter(lookup())
        assertThatThrownBy { converter.convert(jwt()) }.isInstanceOf(BadCredentialsException::class.java)
    }

    @Test
    fun `identyfikator uzytkownika, ktory nie jest UUID, jest odrzucany`() {
        val converter = ProfileJwtAuthenticationConverter(lookup(profile()))
        assertThatThrownBy { converter.convert(jwt(subject = "nie-uuid")) }
            .isInstanceOf(BadCredentialsException::class.java)
    }

    @Test
    fun `zablokowane konto dostaje 403 ACCOUNT_BLOCKED`() {
        val converter = ProfileJwtAuthenticationConverter(lookup(profile(status = "BLOCKED")))
        SecurityContextHolder.getContext().authentication = converter.convert(jwt())

        val response = MockHttpServletResponse()
        val chain = MockFilterChain()
        BlockedAccountFilter().doFilter(MockHttpServletRequest(), response, chain)

        assertThat(response.status).isEqualTo(403)
        assertThat(response.contentAsString).contains("ACCOUNT_BLOCKED")
        assertThat(chain.request).isNull() // żądanie nie poszło dalej
    }

    @Test
    fun `aktywne konto przechodzi dalej`() {
        val converter = ProfileJwtAuthenticationConverter(lookup(profile()))
        SecurityContextHolder.getContext().authentication = converter.convert(jwt())

        val response = MockHttpServletResponse()
        val chain = MockFilterChain()
        BlockedAccountFilter().doFilter(MockHttpServletRequest(), response, chain)

        assertThat(response.status).isEqualTo(200)
        assertThat(chain.request).isNotNull()
    }
}
