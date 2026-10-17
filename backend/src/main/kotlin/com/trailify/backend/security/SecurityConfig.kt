package com.trailify.backend.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.AuthenticationException
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.AccessDeniedHandler

@Configuration
class SecurityConfig {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            // API bezstanowe z tokenem w nagłówku, więc bez sesji i bez CSRF
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                    // publiczne odczyty z openapi.yaml (security: [])
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/routes",
                        "/api/v1/routes/*",
                        "/api/v1/routes/*/reviews",
                        "/api/v1/pois",
                        "/api/v1/pois/*",
                    ).permitAll()
                    // TODO (tydzień 2): rola ADMIN będzie pochodzić z tabeli profile.
                    // Do tego czasu nikt nie ma tej roli, więc /admin jest zamknięte.
                    .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                    .anyRequest().authenticated()
            }
            .oauth2ResourceServer { oauth2 ->
                oauth2.jwt { }
                oauth2.authenticationEntryPoint(JsonAuthenticationEntryPoint)
                oauth2.accessDeniedHandler(JsonAccessDeniedHandler)
            }
        return http.build()
    }
}

/** 401 w formacie Error z openapi.yaml. */
object JsonAuthenticationEntryPoint : AuthenticationEntryPoint {
    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException,
    ) {
        response.status = HttpServletResponse.SC_UNAUTHORIZED
        response.setHeader("WWW-Authenticate", "Bearer")
        response.contentType = "application/json;charset=UTF-8"
        response.writer.write("""{"code":"UNAUTHORIZED","message":"Brak lub nieprawidłowy token"}""")
    }
}

/** 403 w formacie Error z openapi.yaml. */
object JsonAccessDeniedHandler : AccessDeniedHandler {
    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        accessDeniedException: AccessDeniedException,
    ) {
        response.status = HttpServletResponse.SC_FORBIDDEN
        response.contentType = "application/json;charset=UTF-8"
        response.writer.write("""{"code":"FORBIDDEN","message":"Brak uprawnień"}""")
    }
}
