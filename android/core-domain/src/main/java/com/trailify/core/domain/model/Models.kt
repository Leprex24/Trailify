package com.trailify.core.domain.model

import java.time.Instant

// Modele odpowiadają schematom z docs/openapi.yaml.
// Współrzędne trzymamy jako LatLng (szerokość, długość) - zamiana na GeoJSON [lng, lat]
// dzieje się dopiero w warstwie danych.

enum class ActivityType { CYCLING, RUNNING, WALKING }

enum class Role { USER, ADMIN }

enum class UserStatus { ACTIVE, BLOCKED }

enum class PoiStatus { PENDING, APPROVED, REJECTED }

data class LatLng(val latitude: Double, val longitude: Double)

data class Page<T>(
    val items: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)

data class UserSummary(
    val id: String,
    val displayName: String,
    val avatarUrl: String? = null,
)

data class ProfileStats(
    val totalDistance: Double, // metry
    val activityCount: Int,
    val routeCount: Int,
    val collectedPoiCount: Int,
)

data class Profile(
    val id: String,
    val email: String,
    val displayName: String,
    val avatarUrl: String?,
    val role: Role,
    val createdAt: Instant,
    val stats: ProfileStats,
)

data class AdminUser(
    val profile: Profile,
    val status: UserStatus,
)

/** Zdjęcie już zapisane na serwerze. */
data class Photo(val id: Long, val url: String)

/** Zdjęcie wgrane do Storage, przesyłane do API przy tworzeniu trasy/recenzji/punktu. */
data class PhotoRef(val storageKey: String, val url: String)

data class RouteSummary(
    val id: Long,
    val name: String,
    val activityType: ActivityType,
    val isTourist: Boolean,
    val isScenic: Boolean,
    val isPublic: Boolean,
    val totalDistance: Double?, // metry
    val durationSeconds: Int?,
    val averageRating: Double?, // 1-5, null gdy brak recenzji
    val reviewCount: Int,
    val recommendationScore: Double?,
    val startPoint: LatLng?,
    val thumbnailUrl: String?,
    val distanceFromQuery: Double?, // metry, tylko przy wyszukiwaniu z lat/lng
    val owner: UserSummary,
    val createdAt: Instant,
)

data class Route(
    val summary: RouteSummary,
    val description: String?,
    val geometry: List<LatLng>,
    val photos: List<Photo>,
)

/** Dane do publikacji trasy. Podaj dokładnie jedno: activityId albo geometry. */
data class RouteDraft(
    val name: String,
    val description: String?,
    val activityType: ActivityType,
    val isTourist: Boolean,
    val isScenic: Boolean,
    val isPublic: Boolean,
    val activityId: Long? = null,
    val geometry: List<LatLng>? = null,
    val photos: List<PhotoRef> = emptyList(),
)

/** Pola null = bez zmian. */
data class RouteUpdate(
    val name: String? = null,
    val description: String? = null,
    val activityType: ActivityType? = null,
    val isTourist: Boolean? = null,
    val isScenic: Boolean? = null,
    val isPublic: Boolean? = null,
)

enum class RouteSort { RECOMMENDED, RATING, NEWEST, NEAREST, DISTANCE }

data class RouteQuery(
    val center: LatLng? = null,
    val radiusKm: Double? = null,
    val activityTypes: Set<ActivityType> = emptySet(),
    val minDistance: Double? = null, // metry
    val maxDistance: Double? = null, // metry
    val minRating: Double? = null,
    val isTourist: Boolean? = null,
    val isScenic: Boolean? = null,
    val text: String? = null,
    val sort: RouteSort = RouteSort.RECOMMENDED,
    val page: Int = 0,
    val size: Int = 20,
)

data class TrackPoint(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double? = null,
    val speed: Double? = null, // m/s
    val recordedAt: Instant,
    val isReconstructed: Boolean = false, // true = punkt odtworzony przez AI
)

data class ActivitySummary(
    val id: Long,
    val type: ActivityType,
    val routeId: Long?,
    val startTime: Instant,
    val endTime: Instant?,
    val distance: Double?, // metry
    val durationSeconds: Int?,
    val avgPace: Double?, // min/km
    val stepCount: Int?,
)

data class Activity(
    val summary: ActivitySummary,
    val geometry: List<LatLng>,
)

/** Zapisywana po zakończeniu nagrywania. clientId (UUID z telefonu) chroni przed duplikatami. */
data class ActivityDraft(
    val clientId: String,
    val type: ActivityType,
    val routeId: Long?,
    val startTime: Instant,
    val endTime: Instant,
    val distance: Double,
    val durationSeconds: Int,
    val avgPace: Double,
    val stepCount: Int?,
    val points: List<TrackPoint>,
)

data class Review(
    val id: Long,
    val routeId: Long,
    val user: UserSummary,
    val rating: Int,
    val comment: String?,
    val photos: List<Photo>,
    val createdAt: Instant,
)

data class ReviewDraft(
    val rating: Int,
    val comment: String?,
    val photos: List<PhotoRef> = emptyList(),
)

data class Poi(
    val id: Long,
    val name: String,
    val description: String?,
    val latitude: Double,
    val longitude: Double,
    val photoUrl: String?,
)

data class PoiAdmin(
    val poi: Poi,
    val status: PoiStatus,
    val submittedBy: UserSummary?,
    val rejectionReason: String?,
    val createdAt: Instant,
)

data class PoiDraft(
    val name: String,
    val description: String,
    val latitude: Double,
    val longitude: Double,
    val photo: PhotoRef? = null,
)

data class PoiCollectionEntry(
    val poi: Poi,
    val collectedAt: Instant,
)

data class BoundingBox(
    val minLat: Double,
    val minLng: Double,
    val maxLat: Double,
    val maxLng: Double,
)
