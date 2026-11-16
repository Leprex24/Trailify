package com.trailify.backend.social

import com.trailify.backend.route.validationError
import com.trailify.backend.web.ApiException
import com.trailify.backend.web.ApiFieldError
import org.springframework.http.HttpStatus

const val CODE_PRIVATE_PROFILE = "PRIVATE_PROFILE"
const val CODE_SELF_FOLLOW = "SELF_FOLLOW"

/**
 * Czy oglądający widzi statystyki, listy obserwujących, publiczne trasy i zebrane punkty tego profilu.
 * Własny profil widać zawsze, cudzy tylko gdy nie jest prywatny. Nazwa, avatar i tło są widoczne zawsze.
 */
fun canViewDetails(isOwnProfile: Boolean, isPrivate: Boolean): Boolean = isOwnProfile || !isPrivate

/** Wyjątek 403 dla prób wejścia w szczegóły prywatnego profilu. */
fun privateProfileError(): ApiException =
    ApiException(HttpStatus.FORBIDDEN, CODE_PRIVATE_PROFILE, "Ten profil jest prywatny")

/**
 * Czy można zacząć obserwować. Samego siebie nie, a profil prywatny tylko wtedy, gdy już go obserwujemy
 * (ponowne obserwowanie niczego nie zmienia). Zwraca wyjątek do rzucenia albo null.
 */
fun followRejection(isSelf: Boolean, targetIsPrivate: Boolean, alreadyFollowing: Boolean): ApiException? = when {
    isSelf -> ApiException(HttpStatus.CONFLICT, CODE_SELF_FOLLOW, "Nie można obserwować samego siebie")
    targetIsPrivate && !alreadyFollowing -> privateProfileError()
    else -> null
}

const val USER_QUERY_MIN = 2
const val USER_QUERY_MAX = 50

/** Fraza wyszukiwania osób (`query`): po obcięciu spacji od 2 do 50 znaków. Przy błędzie rzuca VALIDATION_ERROR. */
fun parseUserQuery(raw: String?): String {
    val query = raw?.trim().orEmpty()
    if (query.length < USER_QUERY_MIN || query.length > USER_QUERY_MAX) {
        throw validationError(listOf(ApiFieldError("query", "Fraza musi mieć od $USER_QUERY_MIN do $USER_QUERY_MAX znaków")))
    }
    return query
}

/** Znaki specjalne wzorca LIKE (`\`, `%`, `_`) traktujemy dosłownie, żeby fraza `a_b` nie pasowała do `axb`. */
fun escapeLike(text: String): String = text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
