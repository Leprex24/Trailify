package com.trailify.feature.profile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trailify.core.domain.model.ActivitySummary
import com.trailify.core.domain.model.PoiCollectionEntry
import com.trailify.core.domain.model.Profile
import com.trailify.core.domain.repository.ActivityRepository
import com.trailify.core.domain.repository.AuthRepository
import com.trailify.core.domain.repository.PoiRepository
import com.trailify.core.domain.repository.ProfileRepository
import com.trailify.core.ui.UiState
import com.trailify.core.ui.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val profile: UiState<Profile> = UiState.Loading,
    /** Zebrane ciekawe punkty (W24), od najnowszego. */
    val collection: UiState<List<PoiCollectionEntry>> = UiState.Loading,
    /** Historia aktywności (W25), od najnowszej. */
    val activities: UiState<List<ActivitySummary>> = UiState.Loading,
)

/** Profil (W23-W25): dane i statystyki, zebrane punkty, historia aktywności. */
class ProfileViewModel(
    private val profiles: ProfileRepository,
    private val auth: AuthRepository,
    private val pois: PoiRepository,
    private val activities: ActivityRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    /** Wołane przez ProfileScreen przy każdym wejściu na ekran. Trzy zapytania idą równolegle. */
    fun load() {
        viewModelScope.launch {
            if (_state.value.profile !is UiState.Success) _state.update { it.copy(profile = UiState.Loading) }
            val profile = async { result { profiles.getMe() } }
            val collection = async { result { pois.myCollection() } }
            val history = async { result { activities.getMine(page = 0, size = HISTORY_SIZE).items } }
            _state.value = ProfileUiState(profile.await(), collection.await(), history.await())
        }
    }

    fun signOut() {
        viewModelScope.launch { auth.signOut() }
    }

    private suspend fun <T> result(block: suspend () -> T): UiState<T> = try {
        UiState.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w("Profile", "Pobieranie danych profilu nie powiodło się", e)
        UiState.Error(e.toUserMessage())
    }

    internal companion object {
        /** Tyle ostatnich aktywności pokazujemy w profilu. */
        const val HISTORY_SIZE = 30
    }
}
