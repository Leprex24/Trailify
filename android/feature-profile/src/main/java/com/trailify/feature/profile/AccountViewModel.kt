package com.trailify.feature.profile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trailify.core.domain.model.Role
import com.trailify.core.domain.repository.AccountAuthRepository
import com.trailify.core.domain.repository.AuthRepository
import com.trailify.core.domain.repository.ProfileRepository
import com.trailify.core.domain.repository.ThemeMode
import com.trailify.core.domain.repository.ThemeSettings
import com.trailify.core.ui.toUserMessage
import com.trailify.core.ui.validateNewPassword
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AccountUiState(
    val email: String = "",
    val isAdmin: Boolean = false,
    val newPassword: String = "",
    val message: String? = null,
    val isError: Boolean = false,
    val isLoading: Boolean = false,
    val showDeleteDialog: Boolean = false,
)

/**
 * Ustawienia (W8, W9): motyw aplikacji, zmiana hasła, wylogowanie i usunięcie konta (DELETE /me),
 * a dla administratora przejście do panelu. Nazwę i prywatność profilu zmienia się w "Edytuj profil".
 * Po wylogowaniu i usunięciu konta AuthRepository.currentUser staje się null, a nawigacja sama przenosi
 * na ekran logowania.
 */
class AccountViewModel(
    private val profiles: ProfileRepository,
    private val auth: AuthRepository,
    private val account: AccountAuthRepository,
    private val theme: ThemeSettings,
) : ViewModel() {

    private val _ui = MutableStateFlow(AccountUiState())
    val ui: StateFlow<AccountUiState> = _ui.asStateFlow()

    val themeMode: StateFlow<ThemeMode> = theme.mode

    init {
        perform(successMessage = null) {
            val profile = profiles.getMe()
            _ui.update { it.copy(email = profile.email, isAdmin = profile.role == Role.ADMIN) }
        }
    }

    fun setThemeMode(mode: ThemeMode) = theme.setMode(mode)

    fun onNewPasswordChange(v: String) = _ui.update { it.copy(newPassword = v, message = null) }

    fun changePassword() {
        val newPassword = _ui.value.newPassword
        validateNewPassword(newPassword)?.let { showError(it); return }
        perform("Hasło zmienione") {
            account.changePassword(newPassword)
            _ui.update { it.copy(newPassword = "") }
        }
    }

    fun signOut() = perform(successMessage = null) { auth.signOut() }

    fun showDeleteDialog(show: Boolean) = _ui.update { it.copy(showDeleteDialog = show) }

    fun deleteAccount() {
        _ui.update { it.copy(showDeleteDialog = false) }
        perform(successMessage = null) {
            profiles.deleteMe()
            auth.signOut() // konta już nie ma - czyścimy sesję w telefonie
        }
    }

    private fun showError(text: String) = _ui.update { it.copy(message = text, isError = true) }

    /** Wspólna obsługa: pokaż ładowanie, wykonaj, pokaż komunikat sukcesu albo błędu. */
    private fun perform(successMessage: String?, block: suspend () -> Unit) {
        viewModelScope.launch {
            _ui.update { it.copy(isLoading = true, message = null) }
            try {
                block()
                _ui.update { it.copy(isLoading = false, message = successMessage, isError = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("Account", "Operacja na koncie nie powiodła się", e) // szczegóły w Logcat
                _ui.update { it.copy(isLoading = false, isError = true, message = e.toUserMessage()) }
            }
        }
    }
}
