package com.trailify.feature.auth

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trailify.core.domain.repository.AccountAuthRepository
import com.trailify.core.domain.repository.AuthRepository
import com.trailify.core.domain.repository.CONSENT_VERSION
import com.trailify.core.domain.repository.ProfileRepository
import com.trailify.core.ui.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ConsentUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
)

/** Akceptacja polityki prywatności albo usunięcie konta, gdy użytkownik jej nie akceptuje. */
class ConsentViewModel(
    private val account: AccountAuthRepository,
    private val profiles: ProfileRepository,
    private val auth: AuthRepository,
) : ViewModel() {

    val consentRequired: StateFlow<Boolean> = account.consentRequired

    private val _ui = MutableStateFlow(ConsentUiState())
    val ui: StateFlow<ConsentUiState> = _ui.asStateFlow()

    fun accept() = perform { account.giveConsent(CONSENT_VERSION) }

    /** Bez zgody nie możemy prowadzić konta (W7), więc usuwamy je razem z danymi (DELETE /me). */
    fun decline() = perform {
        profiles.deleteMe()
        auth.signOut()
    }

    private fun perform(action: suspend () -> Unit) {
        if (_ui.value.isLoading) return
        viewModelScope.launch {
            _ui.value = ConsentUiState(isLoading = true)
            _ui.value = try {
                action()
                ConsentUiState()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("Consent", "Operacja zgody nie powiodła się", e)
                ConsentUiState(error = e.toUserMessage())
            }
        }
    }
}
