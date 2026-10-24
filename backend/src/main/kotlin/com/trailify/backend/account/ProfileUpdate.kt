package com.trailify.backend.account

import com.trailify.backend.web.ApiException
import com.trailify.backend.web.ApiFieldError
import org.springframework.http.HttpStatus
import java.net.URI

const val DISPLAY_NAME_MIN = 2
const val DISPLAY_NAME_MAX = 50
const val AVATAR_URL_MAX = 1024

/**
 * Zmiana profilu (schemat ProfileUpdate z openapi.yaml). Pola nieprzesłane zostają bez zmian,
 * a `avatarUrl: null` usuwa avatar, dlatego odróżniamy "brak pola" od "pole = null".
 */
data class ProfileUpdate(
    val displayName: String?,
    /** true, gdy żądanie zawierało pole avatarUrl (nawet z wartością null). */
    val changeAvatar: Boolean,
    val avatarUrl: String?,
)

/** Sprawdza treść PATCH /me. Przy błędach rzuca ApiException 400 z listą pól. */
fun parseProfileUpdate(body: Map<String, Any?>): ProfileUpdate {
    val errors = mutableListOf<ApiFieldError>()

    var displayName: String? = null
    if (body.containsKey("displayName")) {
        val raw = body["displayName"]
        if (raw !is String) {
            errors.add(ApiFieldError("displayName", "Nazwa musi być tekstem"))
        } else {
            val trimmed = raw.trim()
            if (trimmed.length !in DISPLAY_NAME_MIN..DISPLAY_NAME_MAX) {
                errors.add(ApiFieldError("displayName", "Nazwa musi mieć od $DISPLAY_NAME_MIN do $DISPLAY_NAME_MAX znaków"))
            } else {
                displayName = trimmed
            }
        }
    }

    val changeAvatar = body.containsKey("avatarUrl")
    var avatarUrl: String? = null
    if (changeAvatar) {
        val raw = body["avatarUrl"]
        when {
            raw == null -> avatarUrl = null
            raw !is String || !isValidAvatarUrl(raw) ->
                errors.add(ApiFieldError("avatarUrl", "Adres avatara musi być poprawnym adresem https"))
            else -> avatarUrl = raw
        }
    }

    if (errors.isNotEmpty()) {
        throw ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Niepoprawne dane", errors)
    }
    return ProfileUpdate(displayName, changeAvatar, avatarUrl)
}

fun isValidAvatarUrl(url: String): Boolean {
    if (url.length > AVATAR_URL_MAX) return false
    return try {
        val uri = URI(url)
        uri.scheme == "https" && !uri.host.isNullOrBlank()
    } catch (e: Exception) {
        false
    }
}
