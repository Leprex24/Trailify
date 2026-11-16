package com.trailify.backend.social

import com.trailify.backend.web.ApiException
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
