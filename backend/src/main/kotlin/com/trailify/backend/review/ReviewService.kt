package com.trailify.backend.review

import com.trailify.backend.account.ProfileRecord
import com.trailify.backend.common.PageRequest
import com.trailify.backend.common.PageResponse
import com.trailify.backend.common.pageOf
import com.trailify.backend.route.RouteService
import com.trailify.backend.route.validationError
import com.trailify.backend.web.ApiException
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate

@Service
class ReviewService(
    private val repository: ReviewRepository,
    private val routes: RouteService,
    private val transactions: TransactionTemplate,
) {

    /** Recenzje trasy (W2). Trasa prywatna innego użytkownika to 404, tak jak przy GET /routes/{id}. */
    fun list(viewer: ProfileRecord?, routeId: Long, page: PageRequest): PageResponse<ReviewResponse> {
        routes.get(viewer, routeId)
        return pageOf(page, repository.countByRoute(routeId), repository.listByRoute(routeId, page))
    }

    /** Dodanie oceny ze zdjęciami (W20, W21). */
    fun create(user: ProfileRecord, routeId: Long, request: ReviewCreateRequest): ReviewResponse {
        val route = routes.get(user, routeId)

        val errors = validateReviewCreate(user.id, request)
        if (errors.isNotEmpty()) throw validationError(errors)

        reviewRejection(
            isOwner = route.summary.owner.id == user.id.toString(),
            completedRoute = repository.hasCompletedRoute(user.id, routeId),
            alreadyReviewed = repository.hasReviewed(user.id, routeId),
        )?.let { throw it }

        val id = try {
            // recenzja i jej zdjęcia zapisują się razem albo wcale
            transactions.execute {
                val reviewId = repository.insert(user.id, routeId, request)
                repository.insertPhotos(reviewId, request.photos.orEmpty())
                reviewId
            }!!
        } catch (e: DuplicateKeyException) {
            // dwa równoczesne zapisy tej samej osoby albo ponownie użyte zdjęcie
            if (e.message.orEmpty().contains("review_unique")) throw alreadyReviewed()
            throw ApiException(HttpStatus.CONFLICT, "CONFLICT", "Zdjęcie o tym kluczu jest już użyte")
        }
        return repository.findById(id)!!
    }

    /** Usunięcie recenzji: autor albo administrator (W33). */
    fun delete(user: ProfileRecord, reviewId: Long) {
        val review = repository.findOwnership(reviewId)
            ?: throw ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Recenzja nie istnieje")
        if (!canDeleteReview(review.authorId, user.id, isAdmin = user.role == ROLE_ADMIN)) {
            throw ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Możesz usunąć tylko własną recenzję")
        }
        repository.delete(reviewId)
    }

    private companion object {
        const val ROLE_ADMIN = "ADMIN"
    }
}
