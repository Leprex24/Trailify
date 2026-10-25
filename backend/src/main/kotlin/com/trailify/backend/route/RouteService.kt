package com.trailify.backend.route

import com.trailify.backend.account.ProfileRecord
import com.trailify.backend.activity.ActivityRepository
import com.trailify.backend.common.PageRequest
import com.trailify.backend.common.PageResponse
import com.trailify.backend.common.pageOf
import com.trailify.backend.web.ApiException
import com.trailify.backend.web.ApiFieldError
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

@Service
class RouteService(
    private val repository: RouteRepository,
    private val activities: ActivityRepository,
    private val transactions: TransactionTemplate,
) {

    /** Publikacja trasy (W16-W19) z aktywności albo z samej geometrii. */
    fun create(user: ProfileRecord, request: RouteCreateRequest): RouteResponse {
        val errors = validateRouteCreate(user.id, request)
        if (errors.isNotEmpty()) throw validationError(errors)

        val (coordinates, durationSeconds) = request.activityId?.let { activityId ->
            val activity = activities.findOwned(user.id, activityId)
                ?: throw validationError(listOf(ApiFieldError("activityId", "Aktywność nie istnieje lub nie należy do Ciebie")))
            activity.geometry.coordinates to activity.durationSeconds
        } ?: (request.geometry!!.coordinates to null)

        if (lengthMeters(coordinates) < MIN_ROUTE_LENGTH_METERS) {
            val field = if (request.activityId != null) "activityId" else "geometry"
            throw validationError(listOf(ApiFieldError(field, "Trasa jest za krótka (minimum ${MIN_ROUTE_LENGTH_METERS.toInt()} m)")))
        }

        val id = try {
            // trasa i jej zdjęcia zapisują się razem albo wcale
            transactions.execute {
                val routeId = repository.insert(user.id, request, coordinates, durationSeconds)
                repository.insertPhotos(routeId, request.photos.orEmpty())
                routeId
            }!!
        } catch (e: DuplicateKeyException) {
            throw ApiException(HttpStatus.CONFLICT, "CONFLICT", "Zdjęcie o tym kluczu jest już użyte")
        }
        return repository.findDetails(id)!!
    }

    /** Trasa prywatna jest widoczna tylko dla właściciela, dla pozostałych to 404. */
    fun get(viewer: ProfileRecord?, routeId: Long): RouteResponse {
        val route = repository.findDetails(routeId) ?: notFound()
        if (!isVisible(route, viewer?.id)) notFound()
        return route
    }

    fun search(filter: RouteSearchFilter, page: PageRequest): PageResponse<RouteSummaryResponse> {
        val (total, items) = repository.search(filter, page)
        return pageOf(page, total, items)
    }

    fun listMine(userId: UUID, page: PageRequest): PageResponse<RouteSummaryResponse> =
        pageOf(page, repository.countByUser(userId), repository.listByUser(userId, page))

    fun update(user: ProfileRecord, routeId: Long, body: Map<String, Any?>): RouteResponse {
        val update = parseRouteUpdate(body)
        val route = repository.findDetails(routeId) ?: notFound()
        requireOwner(route, user)
        repository.update(routeId, update)
        return repository.findDetails(routeId)!!
    }

    /** Usunąć może właściciel albo administrator (W33). */
    fun delete(user: ProfileRecord, routeId: Long) {
        val route = repository.findDetails(routeId) ?: notFound()
        if (user.role != ROLE_ADMIN) requireOwner(route, user)
        repository.delete(routeId)
    }

    private fun isVisible(route: RouteResponse, viewerId: UUID?): Boolean =
        route.summary.isPublic || route.summary.owner.id == viewerId?.toString()

    /** Cudza trasa prywatna udaje, że nie istnieje (404), cudza publiczna daje 403. */
    private fun requireOwner(route: RouteResponse, user: ProfileRecord) {
        if (route.summary.owner.id == user.id.toString()) return
        if (!route.summary.isPublic) notFound()
        throw ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Tylko właściciel może zmienić tę trasę")
    }

    private fun notFound(): Nothing = throw ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Trasa nie istnieje")

    private companion object {
        const val ROLE_ADMIN = "ADMIN"
    }
}
