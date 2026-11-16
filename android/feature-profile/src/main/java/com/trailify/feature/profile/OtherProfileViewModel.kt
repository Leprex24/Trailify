package com.trailify.feature.profile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trailify.core.domain.model.ActivitySummary
import com.trailify.core.domain.model.PoiCollectionEntry
import com.trailify.core.domain.model.PublicProfile
import com.trailify.core.domain.model.RouteSummary
import com.trailify.core.domain.repository.SocialRepository
import com.trailify.core.ui.UiState
import com.trailify.core.ui.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OtherProfileUiState(
    val profile: UiState<PublicProfile> = UiState.Loading,
    /** Publiczne trasy użytkownika. Dla profilu prywatnego nie są pobierane. */
    val routes: UiState<List<RouteSummary>> = UiState.Loading,
    val collection: UiState<List<PoiCollectionEntry>> = UiState.Loading,
    /** Aktywności udostępnione przez tę osobę. */
    val activities: UiState<List<ActivitySummary>> = UiState.Loading,
    val followBusy: Boolean = false,
    val followError: String? = null,
)

/** Przycisk "Obserwuj" jest dla cudzych profili; przy prywatnym tylko do przestania obserwowania. */
fun PublicProfile.canToggleFollow(): Boolean = !isMe && (!isPrivate || isFollowing)

/** Profil po zmianie obserwowania: znacznik i licznik obserwujących zmieniają się razem. */
fun PublicProfile.withFollowing(following: Boolean): PublicProfile {
    if (following == isFollowing) return this
    val delta = if (following) 1 else -1
    return copy(
        isFollowing = following,
        stats = stats?.let { it.copy(followerCount = (it.followerCount + delta).coerceAtLeast(0)) },
    )
}

/** Cudzy profil: dane, obserwowanie, publiczne trasy, udostępnione aktywności i zebrane punkty. */
class OtherProfileViewModel(
    private val userId: String,
    private val social: SocialRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(OtherProfileUiState())
    val state: StateFlow<OtherProfileUiState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            if (_state.value.profile !is UiState.Success) _state.update { it.copy(profile = UiState.Loading) }
            val profile = result { social.getProfile(userId) }
            _state.update { it.copy(profile = profile) }
            // trasy i punkty tylko wtedy, gdy profil jest jawny (albo nasz); prywatny kończyłby się błędem 403
            val details = (profile as? UiState.Success)?.data?.stats != null
            if (details) {
                val routes = async { result { social.routes(userId, page = 0, size = ROUTES_SIZE).items } }
                val collection = async { result { social.poiCollection(userId) } }
                val activities = async { result { social.activities(userId, page = 0, size = ROUTES_SIZE).items } }
                _state.update {
                    it.copy(routes = routes.await(), collection = collection.await(), activities = activities.await())
                }
            }
        }
    }

    fun toggleFollow() {
        val current = (_state.value.profile as? UiState.Success)?.data ?: return
        if (_state.value.followBusy || !current.canToggleFollow()) return
        _state.update { it.copy(followBusy = true, followError = null) }
        viewModelScope.launch {
            try {
                if (current.isFollowing) social.unfollow(userId) else social.follow(userId)
                _state.update {
                    it.copy(profile = UiState.Success(current.withFollowing(!current.isFollowing)), followBusy = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Zmiana obserwowania nie powiodła się", e)
                _state.update { it.copy(followBusy = false, followError = e.toUserMessage()) }
            }
        }
    }

    private suspend fun <T> result(block: suspend () -> T): UiState<T> = try {
        UiState.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Pobieranie cudzego profilu nie powiodło się", e)
        UiState.Error(e.toUserMessage())
    }

    internal companion object {
        const val TAG = "OtherProfile"

        /** Tyle ostatnich tras i aktywności pokazujemy na cudzym profilu. */
        const val ROUTES_SIZE = 50
    }
}
