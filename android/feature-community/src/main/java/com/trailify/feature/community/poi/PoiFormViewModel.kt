package com.trailify.feature.community.poi

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trailify.core.domain.model.LatLng
import com.trailify.core.domain.model.PoiDraft
import com.trailify.core.domain.repository.AdminRepository
import com.trailify.core.domain.repository.PhotoUploader
import com.trailify.core.domain.repository.PoiRepository
import com.trailify.core.domain.repository.UserLocationSource
import com.trailify.core.ui.PhotoItem
import com.trailify.core.ui.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Granice zgodne ze schematem PoiCreate w docs/openapi.yaml.
const val POI_NAME_MAX = 255
const val POI_DESCRIPTION_MAX = 5000

// Limit pola reason w RejectRequest (openapi.yaml).
const val REJECT_REASON_MAX = 500

fun validatePoiName(name: String): String? = when {
    name.isBlank() -> "Podaj nazwę punktu"
    name.trim().length > POI_NAME_MAX -> "Nazwa może mieć maksymalnie $POI_NAME_MAX znaków"
    else -> null
}

fun validatePoiDescription(description: String): String? = when {
    description.isBlank() -> "Opisz krótko, co jest ciekawego w tym miejscu"
    description.trim().length > POI_DESCRIPTION_MAX -> "Opis może mieć maksymalnie $POI_DESCRIPTION_MAX znaków"
    else -> null
}

data class PoiFormUiState(
    val name: String = "",
    val description: String = "",
    /** Wybrane miejsce: domyślnie aktualna pozycja, można dotknąć mapy, żeby je zmienić. */
    val position: LatLng? = null,
    val positionFromMap: Boolean = false,
    val photo: PhotoItem? = null,
    val nameError: String? = null,
    val descriptionError: String? = null,
    val generalError: String? = null,
    val isSending: Boolean = false,
    /** Wysłano: ekran pokazuje podziękowanie i wraca. */
    val done: Boolean = false,
)

/**
 * Zgłoszenie nowego ciekawego punktu (W22) albo, gdy [asAdmin], dodanie go przez administratora
 * od razu jako zatwierdzony (W31). Zdjęcie (najwyżej jedno) wysyłamy do Storage zaraz po wybraniu.
 */
class PoiFormViewModel(
    val asAdmin: Boolean,
    private val pois: PoiRepository,
    private val admin: AdminRepository,
    private val uploader: PhotoUploader,
    location: UserLocationSource,
) : ViewModel() {

    private val _ui = MutableStateFlow(PoiFormUiState(position = location.location.value))
    val ui: StateFlow<PoiFormUiState> = _ui.asStateFlow()

    init {
        // dopóki użytkownik sam nie wybrał miejsca, podążamy za jego pozycją
        viewModelScope.launch {
            location.location.collect { position ->
                if (position != null && !_ui.value.positionFromMap) _ui.update { it.copy(position = position) }
            }
        }
    }

    fun onNameChange(v: String) = _ui.update { it.copy(name = v, nameError = null, generalError = null) }
    fun onDescriptionChange(v: String) = _ui.update { it.copy(description = v, descriptionError = null) }
    fun onMapTap(position: LatLng) = _ui.update { it.copy(position = position, positionFromMap = true, generalError = null) }

    fun onPhotoPicked(uri: String) {
        _ui.update { it.copy(photo = PhotoItem(uri)) }
        upload(uri)
    }

    fun retryPhoto() = _ui.value.photo?.let {
        _ui.update { s -> s.copy(photo = s.photo?.copy(error = null)) }
        upload(it.localUri)
    }

    fun removePhoto() = _ui.update { it.copy(photo = null) }

    fun submit() {
        val s = _ui.value
        if (s.isSending) return
        val checked = s.copy(
            nameError = validatePoiName(s.name),
            descriptionError = validatePoiDescription(s.description),
            generalError = when {
                s.position == null -> "Wybierz miejsce na mapie (albo włącz lokalizację)"
                s.photo?.isUploading == true -> "Poczekaj, aż zdjęcie się wyśle"
                s.photo?.error != null -> "Ponów albo usuń zdjęcie, którego nie udało się wysłać"
                else -> null
            },
        )
        if (checked.nameError != null || checked.descriptionError != null || checked.generalError != null) {
            _ui.value = checked
            return
        }
        val draft = PoiDraft(
            name = s.name.trim(),
            description = s.description.trim(),
            latitude = s.position!!.latitude,
            longitude = s.position.longitude,
            photo = s.photo?.uploaded,
        )
        viewModelScope.launch {
            _ui.update { it.copy(isSending = true, generalError = null) }
            try {
                if (asAdmin) admin.createPoi(draft) else pois.propose(draft)
                _ui.update { it.copy(isSending = false, done = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("PoiForm", "Zapis punktu nie powiódł się", e)
                _ui.update { it.copy(isSending = false, generalError = e.toUserMessage()) }
            }
        }
    }

    private fun upload(uri: String) {
        viewModelScope.launch {
            try {
                val ref = uploader.upload(uri)
                updatePhoto(uri) { it.copy(uploaded = ref) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("PoiForm", "Wysyłanie zdjęcia nie powiodło się", e)
                updatePhoto(uri) { it.copy(error = e.toUserMessage()) }
            }
        }
    }

    /** Zmienia zdjęcie tylko, jeśli użytkownik w międzyczasie nie wybrał innego. */
    private fun updatePhoto(uri: String, change: (PhotoItem) -> PhotoItem) =
        _ui.update { s -> s.copy(photo = s.photo?.let { if (it.localUri == uri) change(it) else it }) }
}
