package com.trailify.feature.profile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trailify.core.domain.repository.PhotoUploader
import com.trailify.core.domain.repository.ProfileRepository
import com.trailify.core.ui.toUserMessage
import com.trailify.core.ui.validateDisplayName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Które zdjęcie profilu wybieramy. */
enum class ProfilePhoto { AVATAR, BACKGROUND }

data class EditProfileUiState(
    val userId: String = "",
    val displayName: String = "",
    val isPrivate: Boolean = false,
    /** Adresy zdjęć: bieżące z serwera albo świeżo wysłane (zapisujemy je dopiero przyciskiem "Zapisz"). */
    val avatarUrl: String? = null,
    val backgroundUrl: String? = null,
    val uploading: Set<ProfilePhoto> = emptySet(),
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    /** true po udanym zapisie: ekran zamyka okno i odświeża profil. */
    val saved: Boolean = false,
) {
    val canSave: Boolean get() = !isLoading && !isSaving && uploading.isEmpty()
}

/**
 * Edycja profilu: nazwa, prywatność oraz zdjęcie profilowe i tło. Zdjęcie wysyłamy do Storage od razu po wyborze
 * ([PhotoUploader]), a adres trafia do profilu razem z resztą zmian po "Zapisz" (PATCH /me).
 */
class EditProfileViewModel(
    private val profiles: ProfileRepository,
    private val uploader: PhotoUploader,
) : ViewModel() {

    private val _ui = MutableStateFlow(EditProfileUiState())
    val ui: StateFlow<EditProfileUiState> = _ui.asStateFlow()

    /** Wołane przy każdym otwarciu okna: wczytuje aktualny profil i czyści stan po poprzedniej edycji. */
    fun start() {
        _ui.value = EditProfileUiState(isLoading = true)
        viewModelScope.launch {
            try {
                val profile = profiles.getMe()
                _ui.value = EditProfileUiState(
                    userId = profile.id,
                    displayName = profile.displayName,
                    isPrivate = profile.isPrivate,
                    avatarUrl = profile.avatarUrl,
                    backgroundUrl = profile.backgroundUrl,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Wczytanie profilu do edycji nie powiodło się", e)
                _ui.update { it.copy(isLoading = false, error = e.toUserMessage()) }
            }
        }
    }

    fun onDisplayNameChange(value: String) = _ui.update { it.copy(displayName = value, error = null) }

    fun onPrivateChange(value: Boolean) = _ui.update { it.copy(isPrivate = value, error = null) }

    /** [imageUri] to adres zdjęcia wybranego w selektorze (content://...). */
    fun onPhotoPicked(which: ProfilePhoto, imageUri: String) {
        _ui.update { it.copy(uploading = it.uploading + which, error = null) }
        viewModelScope.launch {
            try {
                val url = uploader.upload(imageUri).url
                _ui.update {
                    when (which) {
                        ProfilePhoto.AVATAR -> it.copy(avatarUrl = url, uploading = it.uploading - which)
                        ProfilePhoto.BACKGROUND -> it.copy(backgroundUrl = url, uploading = it.uploading - which)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Wysłanie zdjęcia profilu nie powiodło się", e)
                _ui.update { it.copy(uploading = it.uploading - which, error = e.toUserMessage()) }
            }
        }
    }

    fun consumeSaved() = _ui.update { it.copy(saved = false) }

    fun save() {
        val current = _ui.value
        if (!current.canSave) return
        val name = current.displayName.trim()
        validateDisplayName(name)?.let { message ->
            _ui.update { it.copy(error = message) }
            return
        }
        _ui.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                profiles.updateMe(
                    displayName = name,
                    avatarUrl = current.avatarUrl,
                    backgroundUrl = current.backgroundUrl,
                    isPrivate = current.isPrivate,
                )
                _ui.update { it.copy(isSaving = false, saved = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Zapis profilu nie powiódł się", e)
                _ui.update { it.copy(isSaving = false, error = e.toUserMessage()) }
            }
        }
    }

    private companion object {
        const val TAG = "EditProfile"
    }
}
