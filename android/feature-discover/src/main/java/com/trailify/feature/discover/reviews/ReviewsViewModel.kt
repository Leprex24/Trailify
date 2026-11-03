package com.trailify.feature.discover.reviews

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trailify.core.domain.model.AppException
import com.trailify.core.domain.model.Review
import com.trailify.core.domain.model.ReviewDraft
import com.trailify.core.domain.repository.ActivityRepository
import com.trailify.core.domain.repository.AuthRepository
import com.trailify.core.domain.repository.PhotoUploader
import com.trailify.core.domain.repository.ReviewRepository
import com.trailify.core.ui.PhotoItem
import com.trailify.core.ui.UiState
import com.trailify.core.ui.photosThatFit
import com.trailify.core.ui.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Formularz oceny (arkusz "Oceń trasę"). */
data class ReviewForm(
    val rating: Int = 0,
    val comment: String = "",
    val photos: List<PhotoItem> = emptyList(),
    val ratingError: String? = null,
    val commentError: String? = null,
    val generalError: String? = null,
    val info: String? = null,
    val isSending: Boolean = false,
) {
    val canAddPhotos: Boolean get() = photos.size < REVIEW_PHOTOS_MAX
    val isUploading: Boolean get() = photos.any { it.isUploading }
}

data class ReviewsUiState(
    val reviews: UiState<List<Review>> = UiState.Loading,
    val totalCount: Long = 0,
    val eligibility: ReviewEligibility = ReviewEligibility.CHECKING,
    val currentUserId: String? = null,
    /** null = arkusz zamknięty. */
    val form: ReviewForm? = null,
    val deletingId: Long? = null,
    /** Komunikat po udanej akcji albo błędzie usuwania (pokazywany pod listą). */
    val message: String? = null,
    /** Rośnie po dodaniu/usunięciu recenzji: ekran odświeża wtedy średnią ocenę trasy. */
    val changeCount: Int = 0,
)

/**
 * Recenzje trasy (W20, W21): lista od najnowszej, dodanie oceny z komentarzem i zdjęciami,
 * usunięcie własnej recenzji. Zdjęcia, jak przy publikacji trasy, wysyłamy do Storage od razu po wybraniu.
 */
class ReviewsViewModel(
    private val routeId: Long,
    private val ownerId: String,
    private val reviews: ReviewRepository,
    private val activities: ActivityRepository,
    private val auth: AuthRepository,
    private val uploader: PhotoUploader,
) : ViewModel() {

    private val _ui = MutableStateFlow(ReviewsUiState())
    val ui: StateFlow<ReviewsUiState> = _ui.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val me = auth.currentUser.value?.id
            if (_ui.value.reviews !is UiState.Success) _ui.update { it.copy(reviews = UiState.Loading) }
            try {
                val page = reviews.list(routeId, page = 0, size = PAGE_SIZE)
                _ui.update {
                    it.copy(
                        reviews = UiState.Success(page.items),
                        totalCount = page.totalElements,
                        currentUserId = me,
                        eligibility = decideEligibility(me, ownerId, page.items, completedRoute(me)),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Pobieranie recenzji nie powiodło się", e)
                _ui.update { it.copy(reviews = UiState.Error(e.toUserMessage())) }
            }
        }
    }

    fun openForm() = _ui.update { it.copy(form = ReviewForm(), message = null) }

    fun closeForm() = _ui.update { it.copy(form = null) }

    fun onRatingChange(rating: Int) = updateForm { it.copy(rating = rating, ratingError = null, generalError = null) }

    fun onCommentChange(comment: String) = updateForm { it.copy(comment = comment, commentError = null) }

    fun onPhotosPicked(uris: List<String>) {
        val form = _ui.value.form ?: return
        val fresh = uris.filter { uri -> form.photos.none { it.localUri == uri } }
        val fit = photosThatFit(form.photos.size, fresh.size, REVIEW_PHOTOS_MAX)
        val accepted = fresh.take(fit)
        updateForm {
            it.copy(
                photos = it.photos + accepted.map { uri -> PhotoItem(uri) },
                info = if (fit < fresh.size) "Do recenzji można dodać maksymalnie $REVIEW_PHOTOS_MAX zdjęć" else null,
            )
        }
        accepted.forEach(::upload)
    }

    fun retryPhoto(localUri: String) {
        updatePhoto(localUri) { it.copy(error = null) }
        upload(localUri)
    }

    fun removePhoto(localUri: String) =
        updateForm { f -> f.copy(photos = f.photos.filterNot { it.localUri == localUri }, info = null) }

    fun submit() {
        val form = _ui.value.form ?: return
        if (form.isSending) return
        val checked = form.copy(
            ratingError = validateRating(form.rating),
            commentError = validateReviewComment(form.comment),
            generalError = when {
                form.isUploading -> "Poczekaj, aż zdjęcia się wyślą"
                form.photos.any { it.error != null } -> "Ponów albo usuń zdjęcia, których nie udało się wysłać"
                else -> null
            },
        )
        if (checked.ratingError != null || checked.commentError != null || checked.generalError != null) {
            _ui.update { it.copy(form = checked) }
            return
        }

        val draft = ReviewDraft(
            rating = form.rating,
            comment = form.comment.trim().ifEmpty { null },
            photos = form.photos.mapNotNull { it.uploaded },
        )
        viewModelScope.launch {
            updateForm { it.copy(isSending = true, generalError = null) }
            try {
                reviews.create(routeId, draft)
                _ui.update { it.copy(form = null, message = "Dziękujemy za ocenę!", changeCount = it.changeCount + 1) }
                load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Dodanie recenzji nie powiodło się", e)
                // serwer wie lepiej: np. trasa nieukończona albo już oceniona
                val eligibility = when ((e as? AppException)?.code) {
                    CODE_NOT_COMPLETED -> ReviewEligibility.NOT_COMPLETED
                    CODE_ALREADY_REVIEWED -> ReviewEligibility.ALREADY_REVIEWED
                    CODE_OWN_ROUTE -> ReviewEligibility.OWN_ROUTE
                    else -> null
                }
                _ui.update {
                    it.copy(
                        form = it.form?.copy(isSending = false, generalError = e.toUserMessage()),
                        eligibility = eligibility ?: it.eligibility,
                    )
                }
            }
        }
    }

    fun deleteReview(reviewId: Long) {
        if (_ui.value.deletingId != null) return
        viewModelScope.launch {
            _ui.update { it.copy(deletingId = reviewId, message = null) }
            try {
                reviews.delete(reviewId)
                _ui.update { it.copy(deletingId = null, message = "Recenzja usunięta", changeCount = it.changeCount + 1) }
                load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Usunięcie recenzji nie powiodło się", e)
                _ui.update { it.copy(deletingId = null, message = e.toUserMessage()) }
            }
        }
    }

    /** null = nie udało się sprawdzić (serwer zdecyduje przy wysyłce). */
    private suspend fun completedRoute(me: String?): Boolean? {
        if (me == null || me == ownerId) return false
        return try {
            activities.getMine(page = 0, size = ACTIVITIES_TO_CHECK).items.any { it.routeId == routeId }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Nie udało się sprawdzić aktywności", e)
            null
        }
    }

    private fun upload(localUri: String) {
        viewModelScope.launch {
            try {
                val ref = uploader.upload(localUri)
                updatePhoto(localUri) { it.copy(uploaded = ref) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Wysyłanie zdjęcia nie powiodło się", e)
                updatePhoto(localUri) { it.copy(error = e.toUserMessage()) }
            }
        }
    }

    private fun updateForm(change: (ReviewForm) -> ReviewForm) = _ui.update { s -> s.copy(form = s.form?.let(change)) }

    private fun updatePhoto(localUri: String, change: (PhotoItem) -> PhotoItem) =
        updateForm { f -> f.copy(photos = f.photos.map { if (it.localUri == localUri) change(it) else it }) }

    internal companion object {
        const val TAG = "Reviews"
        const val PAGE_SIZE = 50

        /** Tyle ostatnich aktywności przeglądamy, szukając przejścia tej trasy. */
        const val ACTIVITIES_TO_CHECK = 100

        // kody błędów z backendu (POST /routes/{id}/reviews)
        const val CODE_NOT_COMPLETED = "ROUTE_NOT_COMPLETED"
        const val CODE_ALREADY_REVIEWED = "ALREADY_REVIEWED"
        const val CODE_OWN_ROUTE = "OWN_ROUTE"
    }
}
