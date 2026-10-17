package com.trailify.core.data.mock

import com.trailify.core.domain.model.Activity
import com.trailify.core.domain.model.ActivityDraft
import com.trailify.core.domain.model.ActivitySummary
import com.trailify.core.domain.model.AdminUser
import com.trailify.core.domain.model.AppException
import com.trailify.core.domain.model.BoundingBox
import com.trailify.core.domain.model.LatLng
import com.trailify.core.domain.model.Page
import com.trailify.core.domain.model.Photo
import com.trailify.core.domain.model.Poi
import com.trailify.core.domain.model.PoiAdmin
import com.trailify.core.domain.model.PoiCollectionEntry
import com.trailify.core.domain.model.PoiDraft
import com.trailify.core.domain.model.PoiStatus
import com.trailify.core.domain.model.Profile
import com.trailify.core.domain.model.Review
import com.trailify.core.domain.model.ReviewDraft
import com.trailify.core.domain.model.Route
import com.trailify.core.domain.model.RouteDraft
import com.trailify.core.domain.model.RouteQuery
import com.trailify.core.domain.model.RouteSort
import com.trailify.core.domain.model.RouteSummary
import com.trailify.core.domain.model.RouteUpdate
import com.trailify.core.domain.model.UserStatus
import com.trailify.core.domain.model.UserSummary
import com.trailify.core.domain.repository.ActivityRepository
import com.trailify.core.domain.repository.AdminRepository
import com.trailify.core.domain.repository.AuthRepository
import com.trailify.core.domain.repository.PoiRepository
import com.trailify.core.domain.repository.ProfileRepository
import com.trailify.core.domain.repository.ReviewRepository
import com.trailify.core.domain.repository.RouteRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// Atrapy repozytoriów - dane trzymane w pamięci, z krótkim opóźnieniem udającym sieć.
// Zostaną zastąpione implementacjami z Retrofit, interfejsy zostają bez zmian.

private const val FAKE_LATENCY_MS = 300L

private suspend fun fakeNetwork() = delay(FAKE_LATENCY_MS)

private fun <T> List<T>.toPage(page: Int, size: Int): Page<T> {
    val from = (page * size).coerceAtMost(this.size)
    val to = (from + size).coerceAtMost(this.size)
    val totalPages = if (this.isEmpty()) 0 else (this.size + size - 1) / size
    return Page(
        items = subList(from, to),
        page = page,
        size = size,
        totalElements = this.size.toLong(),
        totalPages = totalPages,
    )
}

/** Odległość w metrach (wzór haversine). */
internal fun distanceMeters(a: LatLng, b: LatLng): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(b.latitude - a.latitude)
    val dLng = Math.toRadians(b.longitude - a.longitude)
    val h = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(dLng / 2) * sin(dLng / 2)
    return 2 * r * atan2(sqrt(h), sqrt(1 - h))
}

class MockAuthRepository : AuthRepository {
    // W atrapie użytkownik jest od razu zalogowany, żeby dało się pracować nad pozostałymi ekranami.
    private val user = MutableStateFlow<UserSummary?>(MockData.me)
    override val currentUser: StateFlow<UserSummary?> = user.asStateFlow()

    override suspend fun signIn(email: String, password: String) {
        fakeNetwork()
        user.value = MockData.me
    }

    override suspend fun signUp(email: String, password: String, displayName: String) {
        fakeNetwork()
        user.value = MockData.me.copy(displayName = displayName)
    }

    override suspend fun signOut() {
        user.value = null
    }
}

class MockProfileRepository : ProfileRepository {
    private var profile: Profile = MockData.myProfile

    override suspend fun getMe(): Profile {
        fakeNetwork()
        return profile
    }

    override suspend fun updateMe(displayName: String?, avatarUrl: String?): Profile {
        fakeNetwork()
        profile = profile.copy(
            displayName = displayName ?: profile.displayName,
            avatarUrl = avatarUrl ?: profile.avatarUrl,
        )
        return profile
    }

    override suspend fun deleteMe() {
        fakeNetwork()
    }
}

class MockRouteRepository : RouteRepository {
    private val routes = MockData.routes.toMutableList()
    private var nextId = 100L

    override suspend fun search(query: RouteQuery): Page<RouteSummary> {
        fakeNetwork()
        var result = routes.map { it.summary }.filter { it.isPublic }
        if (query.activityTypes.isNotEmpty()) result = result.filter { it.activityType in query.activityTypes }
        query.minDistance?.let { min -> result = result.filter { (it.totalDistance ?: 0.0) >= min } }
        query.maxDistance?.let { max -> result = result.filter { (it.totalDistance ?: 0.0) <= max } }
        query.minRating?.let { min -> result = result.filter { (it.averageRating ?: 0.0) >= min } }
        query.isTourist?.let { v -> result = result.filter { it.isTourist == v } }
        query.isScenic?.let { v -> result = result.filter { it.isScenic == v } }
        query.text?.takeIf { it.isNotBlank() }?.let { t ->
            result = result.filter { it.name.contains(t, ignoreCase = true) }
        }
        val center = query.center
        if (center != null) {
            result = result.map { it.copy(distanceFromQuery = it.startPoint?.let { p -> distanceMeters(center, p) }) }
            query.radiusKm?.let { km ->
                result = result.filter { (it.distanceFromQuery ?: Double.MAX_VALUE) <= km * 1000 }
            }
        }
        result = when (query.sort) {
            RouteSort.RECOMMENDED -> result.sortedByDescending { it.recommendationScore ?: 0.0 }
            RouteSort.RATING -> result.sortedByDescending { it.averageRating ?: 0.0 }
            RouteSort.NEWEST -> result.sortedByDescending { it.createdAt }
            RouteSort.NEAREST -> result.sortedBy { it.distanceFromQuery ?: Double.MAX_VALUE }
            RouteSort.DISTANCE -> result.sortedBy { it.totalDistance ?: 0.0 }
        }
        return result.toPage(query.page, query.size)
    }

    override suspend fun get(id: Long): Route {
        fakeNetwork()
        return routes.firstOrNull { it.summary.id == id }
            ?: throw AppException("NOT_FOUND", "Nie znaleziono trasy")
    }

    override suspend fun create(draft: RouteDraft): Route {
        fakeNetwork()
        val id = nextId++
        val geometry = draft.geometry ?: emptyList()
        val route = Route(
            summary = RouteSummary(
                id = id,
                name = draft.name,
                activityType = draft.activityType,
                isTourist = draft.isTourist,
                isScenic = draft.isScenic,
                isPublic = draft.isPublic,
                totalDistance = null,
                durationSeconds = null,
                averageRating = null,
                reviewCount = 0,
                recommendationScore = null,
                startPoint = geometry.firstOrNull(),
                thumbnailUrl = null,
                distanceFromQuery = null,
                owner = MockData.me,
                createdAt = Instant.now(),
            ),
            description = draft.description,
            geometry = geometry,
            photos = draft.photos.mapIndexed { i, p -> Photo(id * 100 + i, p.url) },
        )
        routes.add(route)
        return route
    }

    override suspend fun update(id: Long, update: RouteUpdate): Route {
        fakeNetwork()
        val index = routes.indexOfFirst { it.summary.id == id }
        if (index < 0) throw AppException("NOT_FOUND", "Nie znaleziono trasy")
        val old = routes[index]
        val s = old.summary
        val updated = old.copy(
            summary = s.copy(
                name = update.name ?: s.name,
                activityType = update.activityType ?: s.activityType,
                isTourist = update.isTourist ?: s.isTourist,
                isScenic = update.isScenic ?: s.isScenic,
                isPublic = update.isPublic ?: s.isPublic,
            ),
            description = update.description ?: old.description,
        )
        routes[index] = updated
        return updated
    }

    override suspend fun delete(id: Long) {
        fakeNetwork()
        routes.removeAll { it.summary.id == id }
    }

    override suspend fun getMine(page: Int, size: Int): Page<RouteSummary> {
        fakeNetwork()
        return routes.map { it.summary }.filter { it.owner.id == MockData.me.id }
            .sortedByDescending { it.createdAt }
            .toPage(page, size)
    }
}

class MockActivityRepository : ActivityRepository {
    private val activities = mutableListOf<Activity>()
    private val clientIds = mutableMapOf<String, Long>()
    private var nextId = 1L

    override suspend fun save(draft: ActivityDraft): Activity {
        fakeNetwork()
        // Idempotencja: ta sama clientId zwraca już zapisaną aktywność.
        clientIds[draft.clientId]?.let { existing -> return activities.first { it.summary.id == existing } }
        val id = nextId++
        val activity = Activity(
            summary = ActivitySummary(
                id = id,
                type = draft.type,
                routeId = draft.routeId,
                startTime = draft.startTime,
                endTime = draft.endTime,
                distance = draft.distance,
                durationSeconds = draft.durationSeconds,
                avgPace = draft.avgPace,
                stepCount = draft.stepCount,
            ),
            geometry = draft.points.map { LatLng(it.latitude, it.longitude) },
        )
        activities.add(activity)
        clientIds[draft.clientId] = id
        return activity
    }

    override suspend fun get(id: Long): Activity {
        fakeNetwork()
        return activities.firstOrNull { it.summary.id == id }
            ?: throw AppException("NOT_FOUND", "Nie znaleziono aktywności")
    }

    override suspend fun delete(id: Long) {
        fakeNetwork()
        activities.removeAll { it.summary.id == id }
    }

    override suspend fun getMine(page: Int, size: Int): Page<ActivitySummary> {
        fakeNetwork()
        return activities.map { it.summary }.sortedByDescending { it.startTime }.toPage(page, size)
    }
}

class MockReviewRepository : ReviewRepository {
    private val reviews = mutableListOf(
        Review(1, 1, MockData.marek, 5, "Świetna trasa, polecam o zachodzie słońca.", emptyList(),
            Instant.parse("2026-10-05T16:00:00Z")),
        Review(2, 1, MockData.me, 4, "Trochę tłoczno w weekend.", emptyList(),
            Instant.parse("2026-10-06T10:00:00Z")),
    )
    private var nextId = 100L

    override suspend fun list(routeId: Long, page: Int, size: Int): Page<Review> {
        fakeNetwork()
        return reviews.filter { it.routeId == routeId }.sortedByDescending { it.createdAt }.toPage(page, size)
    }

    override suspend fun create(routeId: Long, draft: ReviewDraft): Review {
        fakeNetwork()
        if (reviews.any { it.routeId == routeId && it.user.id == MockData.me.id }) {
            throw AppException("CONFLICT", "Ta trasa jest już oceniona")
        }
        val review = Review(
            id = nextId++,
            routeId = routeId,
            user = MockData.me,
            rating = draft.rating,
            comment = draft.comment,
            photos = draft.photos.mapIndexed { i, p -> Photo(i.toLong(), p.url) },
            createdAt = Instant.now(),
        )
        reviews.add(review)
        return review
    }

    override suspend fun delete(reviewId: Long) {
        fakeNetwork()
        reviews.removeAll { it.id == reviewId }
    }
}

class MockPoiRepository : PoiRepository {
    private val collected = mutableListOf<PoiCollectionEntry>()
    private val proposed = mutableListOf<PoiAdmin>()
    private var nextId = 100L

    override suspend fun inBounds(box: BoundingBox): List<Poi> {
        fakeNetwork()
        return MockData.pois.filter {
            it.latitude in box.minLat..box.maxLat && it.longitude in box.minLng..box.maxLng
        }
    }

    override suspend fun propose(draft: PoiDraft): PoiAdmin {
        fakeNetwork()
        val item = PoiAdmin(
            poi = Poi(nextId++, draft.name, draft.description, draft.latitude, draft.longitude, draft.photo?.url),
            status = PoiStatus.PENDING,
            submittedBy = MockData.me,
            rejectionReason = null,
            createdAt = Instant.now(),
        )
        proposed.add(item)
        return item
    }

    override suspend fun collect(poiId: Long, position: LatLng): PoiCollectionEntry {
        fakeNetwork()
        val poi = MockData.pois.firstOrNull { it.id == poiId }
            ?: throw AppException("NOT_FOUND", "Nie znaleziono punktu")
        if (distanceMeters(position, LatLng(poi.latitude, poi.longitude)) > 50.0) {
            throw AppException("POI_TOO_FAR", "Jesteś zbyt daleko od punktu")
        }
        collected.firstOrNull { it.poi.id == poiId }?.let { return it }
        val entry = PoiCollectionEntry(poi, Instant.now())
        collected.add(entry)
        return entry
    }

    override suspend fun myCollection(): List<PoiCollectionEntry> {
        fakeNetwork()
        return collected.sortedByDescending { it.collectedAt }
    }
}

class MockAdminRepository : AdminRepository {
    private val pending = mutableListOf(
        PoiAdmin(
            poi = Poi(50, "Stary młyn", "Zabytkowy młyn nad rzeką.", 51.2300, 22.5400, null),
            status = PoiStatus.PENDING,
            submittedBy = MockData.anna,
            rejectionReason = null,
            createdAt = Instant.parse("2026-10-08T09:00:00Z"),
        ),
    )
    private val userList = mutableListOf(
        AdminUser(MockData.myProfile, UserStatus.ACTIVE),
    )

    override suspend fun pendingPois(page: Int, size: Int): Page<PoiAdmin> {
        fakeNetwork()
        return pending.toPage(page, size)
    }

    override suspend fun approvePoi(id: Long) {
        fakeNetwork()
        pending.removeAll { it.poi.id == id }
    }

    override suspend fun rejectPoi(id: Long, reason: String?) {
        fakeNetwork()
        pending.removeAll { it.poi.id == id }
    }

    override suspend fun users(page: Int, size: Int): Page<AdminUser> {
        fakeNetwork()
        return userList.toPage(page, size)
    }

    override suspend fun blockUser(id: String) = setStatus(id, UserStatus.BLOCKED)

    override suspend fun unblockUser(id: String) = setStatus(id, UserStatus.ACTIVE)

    private suspend fun setStatus(id: String, status: UserStatus) {
        fakeNetwork()
        val i = userList.indexOfFirst { it.profile.id == id }
        if (i >= 0) userList[i] = userList[i].copy(status = status)
    }
}
