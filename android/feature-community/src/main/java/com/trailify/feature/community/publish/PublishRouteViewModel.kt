package com.trailify.feature.community.publish

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trailify.core.domain.model.ActivityType
import com.trailify.core.domain.model.PhotoRef
import com.trailify.core.domain.model.RouteDraft
import com.trailify.core.domain.repository.PhotoUploader
import com.trailify.core.domain.repository.RouteRepository
import com.trailify.core.ui.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Zdjęcie w formularzu: wybrane z telefonu ([localUri]) i wysyłane od razu do Storage. */
data class PhotoItem(
    val localUri: String,
    val uploaded: PhotoRef? = null,
    val error: String? = null,
) {
    val isUploading: Boolean get() = uploaded == null && error == null
}

data class PublishUiState(
    val name: String = "",
    val description: String = "",
    val activityType: ActivityType = ActivityType.WALKING,
    val isTourist: Boolean = false,
    val isScenic: Boolean = false,
    val isPublic: Boolean = true,
    val photos: List<PhotoItem> = emptyList(),
    val nameError: String? = null,
    val descriptionError: String? = null,
    val generalError: String? = null,
    val info: String? = null,
    val isPublishing: Boolean = false,
    /** Id opublikowanej trasy: ekran ma przejść do jej szczegółów. */
    val publishedRouteId: Long? = null,
) {
    val canAddPhotos: Boolean get() = photos.size < ROUTE_PHOTOS_MAX
    val isUploading: Boolean get() = photos.any { it.isUploading }
}

/**
 * Publikacja trasy z zapisanej aktywności (W16-W19): nazwa, opis, typ aktywności, flagi
 * turystyczna/widokowa, prywatność i zdjęcia. Zdjęcia wysyłamy do Storage zaraz po wybraniu,
 * a do POST /routes trafiają tylko ich storageKey i url.
 */
class PublishRouteViewModel(
    private val activityId: Long,
    initialType: ActivityType,
    private val routes: RouteRepository,
    private val uploader: PhotoUploader,
) : ViewModel() {

    private val _ui = MutableStateFlow(PublishUiState(activityType = initialType))
    val ui: StateFlow<PublishUiState> = _ui.asStateFlow()

    fun onNameChange(v: String) = _ui.update { it.copy(name = v, nameError = null, generalError = null) }
    fun onDescriptionChange(v: String) = _ui.update { it.copy(description = v, descriptionError = null) }
    fun onActivityTypeChange(v: ActivityType) = _ui.update { it.copy(activityType = v) }
    fun onTouristChange(v: Boolean) = _ui.update { it.copy(isTourist = v) }
    fun onScenicChange(v: Boolean) = _ui.update { it.copy(isScenic = v) }
    fun onPublicChange(v: Boolean) = _ui.update { it.copy(isPublic = v) }

    fun onPhotosPicked(uris: List<String>) {
        val current = _ui.value.photos
        val fresh = uris.filter { uri -> current.none { it.localUri == uri } }
        val fit = photosThatFit(current.size, fresh.size)
        val accepted = fresh.take(fit)
        _ui.update {
            it.copy(
                photos = it.photos + accepted.map { uri -> PhotoItem(uri) },
                info = if (fit < fresh.size) "Można dodać maksymalnie $ROUTE_PHOTOS_MAX zdjęć" else null,
            )
        }
        accepted.forEach(::upload)
    }

    fun retryPhoto(localUri: String) {
        updatePhoto(localUri) { it.copy(error = null) }
        upload(localUri)
    }

    /** Usuwa zdjęcie z formularza. Plik wysłany już do Storage zostaje tam (sprzątanie po stronie serwera). */
    fun removePhoto(localUri: String) =
        _ui.update { s -> s.copy(photos = s.photos.filterNot { it.localUri == localUri }, info = null) }

    fun publish() {
        val s = _ui.value
        if (s.isPublishing) return
        val withErrors = s.copy(
            nameError = validateRouteName(s.name),
            descriptionError = validateRouteDescription(s.description),
            generalError = when {
                s.isUploading -> "Poczekaj, aż zdjęcia się wyślą"
                s.photos.any { it.error != null } -> "Ponów albo usuń zdjęcia, których nie udało się wysłać"
                else -> null
            },
        )
        if (withErrors.nameError != null || withErrors.descriptionError != null || withErrors.generalError != null) {
            _ui.value = withErrors
            return
        }

        val draft = RouteDraft(
            name = s.name.trim(),
            description = s.description.trim().ifEmpty { null },
            activityType = s.activityType,
            isTourist = s.isTourist,
            isScenic = s.isScenic,
            isPublic = s.isPublic,
            activityId = activityId,
            photos = s.photos.mapNotNull { it.uploaded },
        )
        viewModelScope.launch {
            _ui.update { it.copy(isPublishing = true, generalError = null) }
            try {
                val route = routes.create(draft)
                _ui.update { it.copy(isPublishing = false, publishedRouteId = route.summary.id) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Publikacja trasy nie powiodła się", e)
                _ui.update { it.copy(isPublishing = false, generalError = e.toUserMessage()) }
            }
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

    private fun updatePhoto(localUri: String, change: (PhotoItem) -> PhotoItem) =
        _ui.update { s -> s.copy(photos = s.photos.map { if (it.localUri == localUri) change(it) else it }) }

    private companion object {
        const val TAG = "PublishRoute"
    }
}
