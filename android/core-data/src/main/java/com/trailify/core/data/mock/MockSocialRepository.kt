package com.trailify.core.data.mock

import com.trailify.core.domain.model.AppException
import com.trailify.core.domain.model.ActivitySummary
import com.trailify.core.domain.model.Page
import com.trailify.core.domain.model.PoiCollectionEntry
import com.trailify.core.domain.model.PublicProfile
import com.trailify.core.domain.model.PublicStats
import com.trailify.core.domain.model.RouteSummary
import com.trailify.core.domain.model.UserSummary
import com.trailify.core.domain.repository.SocialRepository
import kotlinx.coroutines.delay
import java.time.Instant

/**
 * Atrapa cudzych profili: Anna ma profil publiczny, Marek prywatny (widać tylko nazwę),
 * a Kacper to my. Obserwowanie jest pamiętane do zamknięcia aplikacji.
 */
class MockSocialRepository : SocialRepository {
    private val users = listOf(MockData.me, MockData.anna, MockData.marek)
    private val privateIds = setOf(MockData.marek.id)
    private val followedIds = mutableSetOf(MockData.anna.id)

    override suspend fun getProfile(userId: String): PublicProfile {
        delay(LATENCY_MS)
        val user = find(userId)
        val isMe = user.id == MockData.me.id
        val isPrivate = user.id in privateIds
        val followers = users.count { it.id != user.id } + if (user.id in followedIds) 1 else 0
        return PublicProfile(
            id = user.id,
            displayName = user.displayName,
            avatarUrl = user.avatarUrl,
            backgroundUrl = null,
            isPrivate = isPrivate,
            isMe = isMe,
            isFollowing = user.id in followedIds,
            stats = if (isPrivate && !isMe) {
                null
            } else {
                PublicStats(
                    totalDistance = 18_400.0,
                    routeCount = ownRoutes(user.id).size,
                    followerCount = followers,
                    followingCount = users.size - 1,
                )
            },
        )
    }

    override suspend fun follow(userId: String) {
        delay(LATENCY_MS)
        if (userId == MockData.me.id) throw AppException("SELF_FOLLOW", "Nie można obserwować samego siebie")
        if (userId in privateIds) throw AppException("PRIVATE_PROFILE", "Ten profil jest prywatny")
        followedIds += find(userId).id
    }

    override suspend fun unfollow(userId: String) {
        delay(LATENCY_MS)
        followedIds -= find(userId).id
    }

    override suspend fun followers(userId: String, page: Int, size: Int): Page<UserSummary> =
        pageOf(visibleList(userId) { users.filter { it.id != userId } }, page, size)

    override suspend fun following(userId: String, page: Int, size: Int): Page<UserSummary> =
        pageOf(visibleList(userId) { users.filter { it.id != userId } }, page, size)

    override suspend fun routes(userId: String, page: Int, size: Int): Page<RouteSummary> =
        pageOf(visibleList(userId) { ownRoutes(userId) }, page, size)

    override suspend fun poiCollection(userId: String): List<PoiCollectionEntry> {
        delay(LATENCY_MS)
        requireVisible(userId)
        return MockData.pois.take(2).map { PoiCollectionEntry(it, Instant.parse("2026-10-20T12:00:00Z")) }
    }

    override suspend fun activities(userId: String, page: Int, size: Int): Page<ActivitySummary> =
        pageOf(visibleList(userId) { emptyList<ActivitySummary>() }, page, size)

    override suspend fun search(query: String, page: Int, size: Int): Page<UserSummary> {
        delay(LATENCY_MS)
        val found = users.filter { it.id != MockData.me.id && it.displayName.contains(query.trim(), ignoreCase = true) }
        return pageOf(found, page, size)
    }

    override suspend fun removeFollower(userId: String) {
        delay(LATENCY_MS)
        find(userId)
    }

    private fun ownRoutes(userId: String) = MockData.routes.map { it.summary }.filter { it.owner.id == userId && it.isPublic }

    private suspend fun <T> visibleList(userId: String, items: () -> List<T>): List<T> {
        delay(LATENCY_MS)
        requireVisible(userId)
        return items()
    }

    private fun requireVisible(userId: String) {
        find(userId)
        if (userId in privateIds) throw AppException("PRIVATE_PROFILE", "Ten profil jest prywatny")
    }

    private fun find(userId: String) =
        users.firstOrNull { it.id == userId } ?: throw AppException("NOT_FOUND", "Użytkownik nie istnieje")

    private fun <T> pageOf(all: List<T>, page: Int, size: Int): Page<T> {
        val items = all.drop(page * size).take(size)
        return Page(items, page, size, all.size.toLong(), if (all.isEmpty()) 0 else (all.size + size - 1) / size)
    }

    private companion object {
        const val LATENCY_MS = 300L
    }
}
