package com.trailify.feature.auth

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trailify.core.domain.repository.AuthRepository
import com.trailify.core.ui.toUserMessage
import com.trailify.core.ui.validateDisplayName
import com.trailify.core.ui.validateEmail
import com.trailify.core.ui.validateNewPassword
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RegisterUiState(
    val displayName: String = "",
    val email: String = "",
    val password: String = "",
    val repeatPassword: String = "",
    val consent: Boolean = false, // domyślnie NIEzaznaczone - wymóg RODO
    val displayNameError: String? = null,
    val emailError: String? = null,
    val passwordError: String? = null,
    val repeatPasswordError: String? = null,
    val consentError: String? = null,
    val generalError: String? = null,
    val infoMessage: String? = null, // np. "sprawdź skrzynkę"
    val isLoading: Boolean = false,
)

/**
 * Rejestracja e-mailem (W5). Po udanej rejestracji użytkownik jest od razu zalogowany,
 * a nawigacja sama przenosi na mapę. Wyjątek: w Supabase włączone "Confirm email" -
 * wtedy pokazujemy prośbę o potwierdzenie adresu.
 */
class RegisterViewModel(private val auth: AuthRepository) : ViewModel() {

    private val _ui = MutableStateFlow(RegisterUiState())
    val ui: StateFlow<RegisterUiState> = _ui.asStateFlow()

    fun onDisplayNameChange(v: String) = _ui.update { it.copy(displayName = v, displayNameError = null) }
    fun onEmailChange(v: String) = _ui.update { it.copy(email = v.trim(), emailError = null, generalError = null) }
    fun onPasswordChange(v: String) = _ui.update { it.copy(password = v, passwordError = null) }
    fun onRepeatPasswordChange(v: String) = _ui.update { it.copy(repeatPassword = v, repeatPasswordError = null) }
    fun onConsentChange(v: Boolean) = _ui.update { it.copy(consent = v, consentError = null) }

    fun submit() {
        val s = _ui.value
        if (s.isLoading) return

        val withErrors = s.copy(
            displayNameError = validateDisplayName(s.displayName),
            emailError = validateEmail(s.email),
            passwordError = validateNewPassword(s.password),
            repeatPasswordError = if (s.repeatPassword != s.password) "Hasła się różnią" else null,
            consentError = if (!s.consent) "Zgoda jest wymagana do założenia konta" else null,
        )
        val hasErrors = listOf(
            withErrors.displayNameError,
            withErrors.emailError,
            withErrors.passwordError,
            withErrors.repeatPasswordError,
            withErrors.consentError,
        ).any { it != null }
        if (hasErrors) {
            _ui.value = withErrors
            return
        }

        viewModelScope.launch {
            _ui.update { it.copy(isLoading = true, generalError = null, infoMessage = null) }
            try {
                auth.signUp(s.email, s.password, s.displayName.trim())
                val info = if (auth.currentUser.value == null) {
                    "Wysłaliśmy link na ${s.email} - potwierdź adres i zaloguj się"
                } else {
                    null
                }
                _ui.update { it.copy(isLoading = false, infoMessage = info) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("Register", "Rejestracja nie powiodła się", e)
                _ui.update { it.copy(isLoading = false, generalError = e.toUserMessage()) }
            }
        }
    }
}
