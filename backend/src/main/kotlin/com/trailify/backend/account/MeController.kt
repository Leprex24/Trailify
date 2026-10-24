package com.trailify.backend.account

import com.trailify.backend.security.currentProfile
import com.trailify.backend.web.ApiException
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** Odpowiedź GET i PATCH /me (schemat Profile z openapi.yaml). */
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
class MeController(
    private val profiles: ProfileRepository,
    private val accounts: AccountService,
) {

    /**
     * Profil zalogowanego użytkownika. Dane profilu są już wczytane przy sprawdzaniu tokena
     * (patrz ProfileJwtAuthenticationConverter), więc tu dobieramy tylko statystyki.
     */
    @GetMapping
    fun getMe(authentication: Authentication): ProfileResponse = toResponse(authentication.currentProfile())

    /** Edycja konta (W26). Body jako mapa, bo trzeba odróżnić brak pola od pola ustawionego na null. */
    @PatchMapping
    fun updateMe(@RequestBody body: Map<String, Any?>, authentication: Authentication): ProfileResponse {
        val profile = authentication.currentProfile()
        profiles.update(profile.id, parseProfileUpdate(body))
        val fresh = profiles.findById(profile.id)
            ?: throw ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Profil nie istnieje")
        return toResponse(fresh)
    }

    /** Usunięcie konta wraz z danymi (W27). Operacja nieodwracalna. */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteMe(authentication: Authentication) {
        accounts.deleteAccount(authentication.currentProfile().id)
    }

    private fun toResponse(profile: ProfileRecord): ProfileResponse {
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
