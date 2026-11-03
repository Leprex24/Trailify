package com.trailify.feature.discover

import com.trailify.core.domain.model.Activity
import com.trailify.core.domain.model.ActivityDraft
import com.trailify.core.domain.model.ActivitySummary
import com.trailify.core.domain.model.ActivityType
import com.trailify.core.domain.model.Page
import com.trailify.core.domain.model.PhotoRef
import com.trailify.core.domain.model.Review
import com.trailify.core.domain.model.ReviewDraft
import com.trailify.core.domain.model.Route
import com.trailify.core.domain.model.RouteDraft
import com.trailify.core.domain.model.RouteQuery
import com.trailify.core.domain.model.RouteSummary
import com.trailify.core.domain.model.RouteUpdate
import com.trailify.core.domain.model.UserSummary
import com.trailify.core.domain.repository.ActivityRepository
import com.trailify.core.domain.repository.AuthRepository
import com.trailify.core.domain.repository.PhotoUploader
import com.trailify.core.domain.repository.ReviewRepository
import com.trailify.core.domain.repository.RouteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant

// Proste atrapy do testów jednostkowych (bez sieci i bez Koin).

fun <T> pageOf(items: List<T>) = Page(items, page = 0, size = items.size, totalElements = items.size.toLong(), totalPages = 1)

fun summary(id: Long, name: String = "Trasa $id", ownerId: String = "owner") = RouteSummary(
    id = id, name = name, activityType = ActivityType.WALKING, isTourist = false, isScenic = false, isPublic = true,
    totalDistance = 1000.0, durationSeconds = 600, averageRating = null, reviewCount = 0, recommendationScore = null,
    startPoint = null, thumbnailUrl = null, distanceFromQuery = null, owner = UserSummary(ownerId, "Autor"),
    createdAt = Instant.EPOCH,
)

fun review(id: Long, userId: String, routeId: Long = 1L) =
    Review(id, routeId, UserSummary(userId, "U$userId"), rating = 4, comment = null, photos = emptyList(), createdAt = Instant.EPOCH)

class FakeRouteRepository : RouteRepository {
    val queries = mutableListOf<RouteQuery>()
    var result: List<RouteSummary> = listOf(summary(1), summary(2))

    override suspend fun search(query: RouteQuery): Page<RouteSummary> {
        queries += query
        return pageOf(result)
    }

    override suspend fun get(id: Long): Route = error("nieużywane")
    override suspend fun create(draft: RouteDraft): Route = error("nieużywane")
    override suspend fun update(id: Long, update: RouteUpdate): Route = error("nieużywane")
    override suspend fun delete(id: Long) = error("nieużywane")
    override suspend fun getMine(page: Int, size: Int): Page<RouteSummary> = error("nieużywane")
}

class FakeReviewRepository : ReviewRepository {
    val reviews = mutableListOf<Review>()
    val created = mutableListOf<ReviewDraft>()
    var failCreateWith: Exception? = null

    override suspend fun list(routeId: Long, page: Int, size: Int): Page<Review> = pageOf(reviews.toList())

    override suspend fun create(routeId: Long, draft: ReviewDraft): Review {
        failCreateWith?.let { throw it }
        created += draft
        return review(100L + created.size, ME).also { reviews.add(0, it) }
    }

    override suspend fun delete(reviewId: Long) {
        reviews.removeAll { it.id == reviewId }
    }
}

class FakeActivityRepository(var completedRouteIds: Set<Long> = emptySet()) : ActivityRepository {
    override suspend fun getMine(page: Int, size: Int): Page<ActivitySummary> = pageOf(
        completedRouteIds.mapIndexed { i, routeId ->
            ActivitySummary(i.toLong(), ActivityType.WALKING, routeId, Instant.EPOCH, null, null, null, null, null)
        },
    )

    override suspend fun save(draft: ActivityDraft): Activity = error("nieużywane")
    override suspend fun get(id: Long): Activity = error("nieużywane")
    override suspend fun delete(id: Long) = error("nieużywane")
}

class FakeAuth(userId: String? = ME) : AuthRepository {
    override val currentUser: StateFlow<UserSummary?> = MutableStateFlow(userId?.let { UserSummary(it, "Ja") })
    override suspend fun signIn(email: String, password: String) = Unit
    override suspend fun signUp(email: String, password: String, displayName: String) = Unit
    override suspend fun signOut() = Unit
}

class InstantUploader : PhotoUploader {
    override suspend fun upload(imageUri: String) = PhotoRef("me/$imageUri", "https://example.com/$imageUri")
}

const val ME = "me"
