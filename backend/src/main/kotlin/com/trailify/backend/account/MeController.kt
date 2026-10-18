package com.trailify.backend.account

import com.trailify.backend.security.currentProfile
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Odpowiedź GET /me (schemat Profile z openapi.yaml). */
data class ProfileResponse(
    val id: String,
    val email: String,
    val displayName: String,
    val avatarUrl: String?,
    val role: String,
    /** ISO 8601 w UTC, np. 2026-10-20T14:30:00Z. */
    val createdAt: String,
    val stats: ProfileStatsResponse,
)

data class ProfileStatsResponse(
    val totalDistance: Double,
    val activityCount: Long,
    val routeCount: Long,
    val collectedPoiCount: Long,
)

@RestController
@RequestMapping("/api/v1/me")
class MeController(private val profiles: ProfileRepository) {

    /**
     * Profil zalogowanego użytkownika. Dane profilu są już wczytane przy sprawdzaniu tokena
     * (patrz ProfileJwtAuthenticationConverter), więc tu dobieramy tylko statystyki.
     */
    @GetMapping
    fun getMe(authentication: Authentication): ProfileResponse {
        val profile = authentication.currentProfile()
        val stats = profiles.stats(profile.id)
        return ProfileResponse(
            id = profile.id.toString(),
            email = profile.email,
            displayName = profile.displayName,
            avatarUrl = profile.avatarUrl,
            role = profile.role,
            createdAt = profile.createdAt.toInstant().toString(),
            stats = ProfileStatsResponse(
                totalDistance = stats.totalDistance,
                activityCount = stats.activityCount,
                routeCount = stats.routeCount,
                collectedPoiCount = stats.collectedPoiCount,
            ),
        )
    }
}
