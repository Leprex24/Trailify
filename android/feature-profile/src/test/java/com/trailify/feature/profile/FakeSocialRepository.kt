package com.trailify.feature.profile

import com.trailify.core.domain.model.ActivityType
import com.trailify.core.domain.model.AppException
import com.trailify.core.domain.model.Page
import com.trailify.core.domain.model.Poi
import com.trailify.core.domain.model.PoiCollectionEntry
import com.trailify.core.domain.model.PublicProfile
import com.trailify.core.domain.model.PublicStats
import com.trailify.core.domain.model.RouteSummary
import com.trailify.core.domain.model.UserSummary
import com.trailify.core.domain.repository.SocialRepository
import java.time.Instant

/** Atrapa serwera dla testów: prywatny profil kończy listy i trasy błędem PRIVATE_PROFILE, jak backend. */
class FakeSocialRepository(
    var profile: PublicProfile = publicProfile(),
    var users: List<UserSummary> = emptyList(),
    var routes: List<RouteSummary> = emptyList(),
    var failFollow: Boolean = false,
) : SocialRepository {
    val followCalls = mutableListOf<String>()
    val unfollowCalls = mutableListOf<String>()
    val requestedPages = mutableListOf<Int>()

    override suspend fun getProfile(userId: String) = profile

    override suspend fun follow(userId: String) {
        if (failFollow) throw AppException("NETWORK_ERROR", "offline")
        followCalls += userId
    }

    override suspend fun unfollow(userId: String) {
        if (failFollow) throw AppException("NETWORK_ERROR", "offline")
        unfollowCalls += userId
    }

    override suspend fun followers(userId: String, page: Int, size: Int) = userPage(page, size)

    override suspend fun following(userId: String, page: Int, size: Int) = userPage(page, size)

    override suspend fun routes(userId: String, page: Int, size: Int): Page<RouteSummary> {
        requirePublic()
        return Page(routes, 0, size, routes.size.toLong(), 1)
    }

    override suspend fun poiCollection(userId: String): List<PoiCollectionEntry> {
        requirePublic()
        return listOf(PoiCollectionEntry(Poi(1, "Brama Krakowska", null, 51.2, 22.5, null), Instant.EPOCH))
    }

    private fun userPage(page: Int, size: Int): Page<UserSummary> {
        requirePublic()
        requestedPages += page
        val items = users.drop(page * size).take(size)
        return Page(items, page, size, users.size.toLong(), (users.size + size - 1) / size)
    }

    private fun requirePublic() {
        if (profile.stats == null) throw AppException("PRIVATE_PROFILE", "Ten profil jest prywatny")
    }
}

fun publicProfile(
    id: String = "anna",
    isFollowing: Boolean = false,
    isPrivate: Boolean = false,
    isMe: Boolean = false,
    followers: Int = 3,
) = PublicProfile(
    id = id,
    displayName = "Anna",
    avatarUrl = null,
    backgroundUrl = null,
    isPrivate = isPrivate,
    isMe = isMe,
    isFollowing = isFollowing,
    stats = if (isPrivate && !isMe) null else PublicStats(12_000.0, 2, followers, 5),
)

fun routeSummary(id: Long) = RouteSummary(
    id, "Trasa $id", ActivityType.WALKING, false, false, true, 3000.0, 1800, 4.5, 2, null, null, null, null,
    UserSummary("anna", "Anna"), Instant.EPOCH,
)
