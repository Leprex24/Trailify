package com.trailify.backend.dev

import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * TYMCZASOWY endpoint do sprawdzenia, że walidacja tokenów działa.
 * USUNĄĆ pod koniec tygodnia 2 (27.10), gdy będzie prawdziwy GET /me.
 */
@RestController
@RequestMapping("/api/v1/dev")
class DevController {

    @GetMapping("/whoami")
    fun whoami(@AuthenticationPrincipal jwt: Jwt): Map<String, Any?> = mapOf(
        "userId" to jwt.subject,
        "email" to jwt.getClaimAsString("email"),
        "role" to jwt.getClaimAsString("role"),
    )
}
