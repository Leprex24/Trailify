package com.trailify.backend.social

import com.trailify.backend.account.ProfileRecord
import com.trailify.backend.account.ProfileRepository
import com.trailify.backend.common.PageRequest
import com.trailify.backend.common.PageResponse
import com.trailify.backend.common.pageOf
import com.trailify.backend.poi.PoiCollectionEntryResponse
import com.trailify.backend.poi.PoiRepository
import com.trailify.backend.route.RouteRepository
import com.trailify.backend.route.RouteSummaryResponse
import com.trailify.backend.route.UserSummaryResponse
import com.trailify.backend.web.ApiException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.util.UUID

/** Statystyki widoczne dla innych (tylko gdy profil nie jest prywatny). Bez aktywności i zebranych punktów. */
data class PublicStatsResponse(
    val totalDistance: Double,
    /** Tylko trasy publiczne. */
    val routeCount: Long,
    val followerCount: Long,
    val followingCount: Long,
)

/** Cudzy profil (GET /users/{id}). [stats] jest null, gdy profil jest prywatny i nie należy do oglądającego. */
data class PublicProfileResponse(
    val id: String,
    val displayName: String,
    val avatarUrl: String?,
    val backgroundUrl: String?,
    val isPrivate: Boolean,
    val isMe: Boolean,
    val isFollowing: Boolean,
    val stats: PublicStatsResponse?,
)

/** Profil publiczny, obserwowanie oraz to, co widać na cudzym profilu (trasy, punkty, listy osób). */
@Service
class SocialService(
    private val profiles: ProfileRepository,
    private val follows: FollowRepository,
    private val routes: RouteRepository,
    private val pois: PoiRepository,
) {

    fun profileOf(viewer: ProfileRecord, userId: UUID): PublicProfileResponse {
        val target = findActive(userId)
        val isMe = target.id == viewer.id
        val stats = if (canViewDetails(isMe, target.isPrivate)) {
            profiles.stats(target.id).let {
                PublicStatsResponse(it.totalDistance, it.routeCount, it.followerCount, it.followingCount)
            }
        } else {
            null
        }
        return PublicProfileResponse(
            id = target.id.toString(),
            displayName = target.displayName,
            avatarUrl = target.avatarUrl,
            backgroundUrl = target.backgroundUrl,
            isPrivate = target.isPrivate,
            isMe = isMe,
            isFollowing = !isMe && follows.isFollowing(viewer.id, target.id),
            stats = stats,
        )
    }

    fun follow(viewer: ProfileRecord, userId: UUID) {
        val target = findActive(userId)
        val rejection = followRejection(
            isSelf = viewer.id == target.id,
            targetIsPrivate = target.isPrivate,
            alreadyFollowing = follows.isFollowing(viewer.id, target.id),
        )
        if (rejection != null) throw rejection
        follows.follow(viewer.id, target.id)
    }

    fun unfollow(viewer: ProfileRecord, userId: UUID) = follows.unfollow(viewer.id, userId)

    fun followers(viewer: ProfileRecord, userId: UUID, page: PageRequest): PageResponse<UserSummaryResponse> {
        requireVisible(viewer, findActive(userId))
        return pageOf(page, follows.countFollowers(userId), follows.followers(userId, page))
    }

    fun following(viewer: ProfileRecord, userId: UUID, page: PageRequest): PageResponse<UserSummaryResponse> {
        requireVisible(viewer, findActive(userId))
        return pageOf(page, follows.countFollowing(userId), follows.following(userId, page))
    }

    /** Publiczne trasy użytkownika (prywatnych nie widać nawet na własnym profilu oglądanym przez innych). */
    fun routesOf(viewer: ProfileRecord, userId: UUID, page: PageRequest): PageResponse<RouteSummaryResponse> {
        requireVisible(viewer, findActive(userId))
        return pageOf(page, routes.countPublicByUser(userId), routes.listPublicByUser(userId, page))
    }

    fun collectionOf(viewer: ProfileRecord, userId: UUID): List<PoiCollectionEntryResponse> {
        requireVisible(viewer, findActive(userId))
        return pois.collectionOf(userId)
    }

    /** Zablokowane i nieistniejące konta wyglądają tak samo: 404. */
    private fun findActive(userId: UUID): ProfileRecord =
        profiles.findById(userId)?.takeUnless { it.isBlocked }
            ?: throw ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Użytkownik nie istnieje")

    private fun requireVisible(viewer: ProfileRecord, target: ProfileRecord) {
        if (!canViewDetails(viewer.id == target.id, target.isPrivate)) throw privateProfileError()
    }
}
