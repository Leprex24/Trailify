package com.trailify.backend.security

import com.trailify.backend.account.ProfileLookup
import com.trailify.backend.account.ProfileRecord
import com.trailify.backend.web.ApiException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.convert.converter.Converter
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.core.Authentication
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

/** Zalogowany użytkownik: token JWT razem z jego profilem z bazy (rola i status konta). */
class ProfileAuthentication(
    jwt: Jwt,
    authorities: Collection<GrantedAuthority>,
    val profile: ProfileRecord,
) : JwtAuthenticationToken(jwt, authorities, jwt.subject.orEmpty())

/** Profil zalogowanego użytkownika. Użycie w kontrolerach: `authentication.currentProfile()`. */
fun Authentication.currentProfile(): ProfileRecord =
    (this as? ProfileAuthentication)?.profile
        ?: throw ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Brak zalogowanego użytkownika")

/**
 * Zamienia zweryfikowany token Supabase na użytkownika Trailify.
 *
 * Rola (USER / ADMIN) pochodzi z tabeli `profile`, a nie z tokena: w tokenie Supabase pole `role`
 * zawsze ma wartość "authenticated", więc nie mówi nic o uprawnieniach w naszej aplikacji.
 * Brak profilu to błąd uwierzytelnienia (401).
 */
@Component
class ProfileJwtAuthenticationConverter(
    private val profiles: ProfileLookup,
) : Converter<Jwt, AbstractAuthenticationToken> {

    override fun convert(jwt: Jwt): AbstractAuthenticationToken {
        val userId = try {
            UUID.fromString(jwt.subject)
        } catch (e: IllegalArgumentException) {
            throw BadCredentialsException("Nieprawidłowy identyfikator użytkownika w tokenie")
        }
        val profile = profiles.findById(userId)
            ?: throw BadCredentialsException("Brak profilu dla użytkownika z tokena")
        val authorities = listOf(SimpleGrantedAuthority("ROLE_${profile.role}"))
        return ProfileAuthentication(jwt, authorities, profile)
    }
}

/**
 * Zablokowane konto dostaje 403 z kodem ACCOUNT_BLOCKED na każdym endpoincie, także publicznym,
 * jeśli aplikacja wysłała jego token. Filtr działa zaraz po sprawdzeniu tokena.
 */
class BlockedAccountFilter : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val authentication = SecurityContextHolder.getContext().authentication
        if (authentication is ProfileAuthentication && authentication.profile.isBlocked) {
            response.status = HttpServletResponse.SC_FORBIDDEN
            response.contentType = "application/json;charset=UTF-8"
            response.writer.write("""{"code":"ACCOUNT_BLOCKED","message":"Konto zostało zablokowane"}""")
            return
        }
        filterChain.doFilter(request, response)
    }
}
