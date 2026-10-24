package com.trailify.feature.auth

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trailify.core.domain.repository.AccountAuthRepository
import com.trailify.core.domain.repository.AuthRepository
import com.trailify.core.ui.toUserMessage
import com.trailify.core.ui.validateEmail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Wszystko, co ekran logowania ma pokazać. */
data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val emailError: String? = null,
    val passwordError: String? = null,
    val generalError: String? = null,
    val isLoading: Boolean = false,
)

/**
 * Logowanie e-mailem i przez Google. Po udanym logowaniu AuthRepository.currentUser przestaje
 * być null, a nawigacja sama przenosi na mapę (TrailifyNavHost). ViewModel przeżywa obrót
 * telefonu, więc wpisany tekst nie znika.
 */
class LoginViewModel(
    private val auth: AuthRepository,
    private val account: AccountAuthRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(LoginUiState())
    val ui: StateFlow<LoginUiState> = _ui.asStateFlow()

    /** null = przycisk Google ukryty. */
    val googleWebClientId: String? = account.googleWebClientId

    fun onEmailChange(value: String) =
        _ui.update { it.copy(email = value.trim(), emailError = null, generalError = null) }

    fun onPasswordChange(value: String) =
        _ui.update { it.copy(password = value, passwordError = null, generalError = null) }

    fun submit() {
        val s = _ui.value
        if (s.isLoading) return
        val emailError = validateEmail(s.email)
        val passwordError = if (s.password.isEmpty()) "Wpisz hasło" else null
        if (emailError != null || passwordError != null) {
            _ui.update { it.copy(emailError = emailError, passwordError = passwordError) }
            return
        }
        perform { auth.signIn(s.email, s.password) }
    }

    fun onGoogleToken(token: GoogleToken) = perform {
        account.signInWithGoogle(token.idToken, token.rawNonce)
    }

    fun onGoogleError() =
        _ui.update { it.copy(isLoading = false, generalError = "Nie udało się zalogować przez Google") }

    private fun perform(action: suspend () -> Unit) {
        viewModelScope.launch {
            _ui.update { it.copy(isLoading = true, generalError = null) }
            try {
                action()
                _ui.update { it.copy(isLoading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Logowanie nie powiodło się", e)
                _ui.update { it.copy(isLoading = false, generalError = e.toUserMessage()) }
            }
        }
    }

    private companion object {
        const val TAG = "Login"
    }
}
