package com.trailify.core.domain.repository

import com.trailify.core.domain.model.Activity
import com.trailify.core.domain.model.ActivityDraft
import com.trailify.core.domain.model.ActivitySummary
import com.trailify.core.domain.model.AdminUser
import com.trailify.core.domain.model.BoundingBox
import com.trailify.core.domain.model.LatLng
import com.trailify.core.domain.model.Page
import com.trailify.core.domain.model.Poi
import com.trailify.core.domain.model.PoiAdmin
import com.trailify.core.domain.model.PoiCollectionEntry
import com.trailify.core.domain.model.PoiDraft
import com.trailify.core.domain.model.Profile
import com.trailify.core.domain.model.Review
import com.trailify.core.domain.model.ReviewDraft
import com.trailify.core.domain.model.Route
import com.trailify.core.domain.model.RouteDraft
import com.trailify.core.domain.model.RouteQuery
import com.trailify.core.domain.model.RouteSummary
import com.trailify.core.domain.model.RouteUpdate
import com.trailify.core.domain.model.UserSummary
import kotlinx.coroutines.flow.StateFlow

// Interfejsy odpowiadają endpointom z docs/openapi.yaml.
// Ekrany zależą WYŁĄCZNIE od tych interfejsów. Na początku działają na nich atrapy (mocki)
// z modułu core-data, potem podmieniamy je na implementacje z Retrofit - ekrany się nie zmieniają.
// Błędy API są zgłaszane jako AppException.

/** Logowanie przez Supabase Auth. null = niezalogowany. */
interface AuthRepository {
    val currentUser: StateFlow<UserSummary?>
    suspend fun signIn(email: String, password: String)
    suspend fun signUp(email: String, password: String, displayName: String)
    suspend fun signOut()
}

/** /me */
interface ProfileRepository {
    suspend fun getMe(): Profile
    suspend fun updateMe(displayName: String? = null, avatarUrl: String? = null): Profile
    suspend fun deleteMe()
}

/** /routes, /me/routes */
interface RouteRepository {
    suspend fun search(query: RouteQuery): Page<RouteSummary>
    suspend fun get(id: Long): Route
    suspend fun create(draft: RouteDraft): Route
    suspend fun update(id: Long, update: RouteUpdate): Route
    suspend fun delete(id: Long)
    suspend fun getMine(page: Int = 0, size: Int = 20): Page<RouteSummary>
}

/** /activities, /me/activities */
interface ActivityRepository {
    suspend fun save(draft: ActivityDraft): Activity
    suspend fun get(id: Long): Activity
    suspend fun delete(id: Long)
    suspend fun getMine(page: Int = 0, size: Int = 20): Page<ActivitySummary>
}

/** /routes/{id}/reviews, /reviews/{id} */
interface ReviewRepository {
    suspend fun list(routeId: Long, page: Int = 0, size: Int = 20): Page<Review>
    suspend fun create(routeId: Long, draft: ReviewDraft): Review
    suspend fun delete(reviewId: Long)
}

/** /pois, /pois/{id}/collect, /me/poi-collection */
interface PoiRepository {
    suspend fun inBounds(box: BoundingBox): List<Poi>
    suspend fun propose(draft: PoiDraft): PoiAdmin

    /** Zbiera punkt, gdy użytkownik jest w pobliżu (serwer sprawdza 50 m, inaczej AppException("POI_TOO_FAR")). */
    suspend fun collect(poiId: Long, position: LatLng): PoiCollectionEntry
    suspend fun myCollection(): List<PoiCollectionEntry>
}

/** /admin/... (tylko rola ADMIN) */
interface AdminRepository {
    suspend fun pendingPois(page: Int = 0, size: Int = 20): Page<PoiAdmin>
    suspend fun approvePoi(id: Long)
    suspend fun rejectPoi(id: Long, reason: String?)
    suspend fun users(page: Int = 0, size: Int = 20): Page<AdminUser>
    suspend fun blockUser(id: String)
    suspend fun unblockUser(id: String)
}
