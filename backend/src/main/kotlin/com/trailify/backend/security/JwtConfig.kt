package com.trailify.backend.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder

@Configuration
class JwtConfig {

    /**
     * Dekoder tokenów Supabase. Klucze publiczne pobiera z JWKS (przy pierwszym żądaniu).
     * Nowe projekty Supabase podpisują tokeny algorytmem ES256 (starsze RS256),
     * a Spring domyślnie przyjmuje tylko RS256, dlatego jawnie dopuszczamy oba.
     */
    @Bean
    fun jwtDecoder(
        @Value("\${supabase.url}") supabaseUrl: String,
        @Value("\${supabase.jwks-url}") jwksUrl: String,
    ): JwtDecoder {
        val decoder = NimbusJwtDecoder.withJwkSetUri(jwksUrl)
            .jwsAlgorithm(SignatureAlgorithm.ES256)
            .jwsAlgorithm(SignatureAlgorithm.RS256)
            .build()
        decoder.setJwtValidator(supabaseJwtValidator("${supabaseUrl.trimEnd('/')}/auth/v1"))
        return decoder
    }
}
