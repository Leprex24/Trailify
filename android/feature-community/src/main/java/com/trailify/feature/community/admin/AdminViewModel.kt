package com.trailify.feature.community.admin

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trailify.core.domain.model.AdminUser
import com.trailify.core.domain.model.PoiAdmin
import com.trailify.core.domain.model.PoiStatus
import com.trailify.core.domain.repository.AdminRepository
import com.trailify.core.ui.UiState
import com.trailify.core.ui.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AdminTab { POIS, USERS }

data class AdminUiState(
    val tab: AdminTab = AdminTab.POIS,
    /** null = wszystkie. Domyślnie kolejka zgłoszeń do moderacji. */
    val poiStatus: PoiStatus? = PoiStatus.PENDING,
    val pois: UiState<List<PoiAdmin>> = UiState.Loading,
    val userQuery: String = "",
    val users: UiState<List<AdminUser>> = UiState.Loading,
    /** Element, na którym trwa operacja (przyciski wyłączone). */
    val busyId: String? = null,
    val message: String? = null,
    val isError: Boolean = false,
)

/**
 * Panel administratora (W29-W34): moderacja zgłoszonych punktów (zatwierdzanie, odrzucanie z powodem,
 * edycja, usuwanie) oraz konta użytkowników (wyszukiwanie, blokada z opcjonalnym usunięciem treści, usuwanie).
 */
class AdminViewModel(private val admin: AdminRepository) : ViewModel() {

    private val _ui = MutableStateFlow(AdminUiState())
    val ui: StateFlow<AdminUiState> = _ui.asStateFlow()

    private var usersJob: Job? = null

    init {
        loadPois()
        loadUsers(debounce = false)
    }

    fun onTabChange(tab: AdminTab) = _ui.update { it.copy(tab = tab, message = null) }

    fun onPoiStatusChange(status: PoiStatus?) {
        _ui.update { it.copy(poiStatus = status, pois = UiState.Loading) }
        loadPois()
    }

    fun loadPois() {
        viewModelScope.launch {
            _ui.update {
                it.copy(
                    pois = try {
                        UiState.Success(admin.pois(it.poiStatus, page = 0, size = PAGE_SIZE).items)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Pobieranie punktów nie powiodło się", e)
                        UiState.Error(e.toUserMessage())
                    },
                )
            }
        }
    }

    fun approve(poi: PoiAdmin) = act(poi.poi.id.toString(), "Zatwierdzono: ${poi.poi.name}", ::loadPois) {
        admin.approvePoi(poi.poi.id)
    }

    fun reject(poi: PoiAdmin, reason: String) = act(poi.poi.id.toString(), "Odrzucono: ${poi.poi.name}", ::loadPois) {
        admin.rejectPoi(poi.poi.id, reason.trim().ifEmpty { null })
    }

    fun deletePoi(poi: PoiAdmin) = act(poi.poi.id.toString(), "Usunięto: ${poi.poi.name}", ::loadPois) {
        admin.deletePoi(poi.poi.id)
    }

    fun updatePoi(poi: PoiAdmin, name: String, description: String) =
        act(poi.poi.id.toString(), "Zapisano zmiany", ::loadPois) {
            admin.updatePoi(
                poi.poi.id,
                name = name.trim().takeIf { it.isNotEmpty() && it != poi.poi.name },
                description = description.trim().takeIf { it.isNotEmpty() && it != poi.poi.description },
            )
        }

    fun onUserQueryChange(query: String) {
        _ui.update { it.copy(userQuery = query) }
        loadUsers(debounce = true)
    }

    fun loadUsers(debounce: Boolean = false) {
        usersJob?.cancel()
        usersJob = viewModelScope.launch {
            if (debounce) delay(SEARCH_DEBOUNCE_MS)
            val result = try {
                UiState.Success(admin.users(_ui.value.userQuery, page = 0, size = PAGE_SIZE).items)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Pobieranie użytkowników nie powiodło się", e)
                UiState.Error(e.toUserMessage())
            }
            _ui.update { it.copy(users = result) }
        }
    }

    fun block(user: AdminUser, deleteContent: Boolean) = act(
        user.profile.id,
        if (deleteContent) "Zablokowano i usunięto treści: ${user.profile.displayName}" else "Zablokowano: ${user.profile.displayName}",
        { loadUsers() },
    ) { admin.blockUser(user.profile.id, deleteContent) }

    fun unblock(user: AdminUser) = act(user.profile.id, "Odblokowano: ${user.profile.displayName}", { loadUsers() }) {
        admin.unblockUser(user.profile.id)
    }

    fun deleteUser(user: AdminUser) = act(user.profile.id, "Usunięto konto: ${user.profile.displayName}", { loadUsers() }) {
        admin.deleteUser(user.profile.id)
    }

    /** Wspólna obsługa przycisków: blokada elementu na czas operacji, komunikat i odświeżenie listy. */
    private fun act(id: String, success: String, reload: () -> Unit, action: suspend () -> Unit) {
        if (_ui.value.busyId != null) return
        viewModelScope.launch {
            _ui.update { it.copy(busyId = id, message = null) }
            try {
                action()
                _ui.update { it.copy(busyId = null, message = success, isError = false) }
                reload()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Operacja administratora nie powiodła się", e)
                _ui.update { it.copy(busyId = null, message = e.toUserMessage(), isError = true) }
            }
        }
    }

    internal companion object {
        const val TAG = "Admin"
        const val PAGE_SIZE = 50
        const val SEARCH_DEBOUNCE_MS = 400L
    }
}
