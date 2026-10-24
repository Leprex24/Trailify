package com.trailify.core.domain.repository

import kotlinx.coroutines.flow.StateFlow

/**
 * Funkcje konta, których nie ma w [AuthRepository]: logowanie Google, zmiana hasła i zgoda RODO.
 * Osobny interfejs, żeby nie zmieniać wspólnego kontraktu [AuthRepository].
 * Błędy są zgłaszane jako AppException (kody w [AuthErrorCodes]).
 */
interface AccountAuthRepository {

    /** Web Client ID z Google Cloud. null = logowanie Google wyłączone (atrapa albo brak konfiguracji). */
    val googleWebClientId: String?

    /**
     * true, gdy zalogowany użytkownik nie zaakceptował aktualnej polityki prywatności
     * (np. nowe konto Google, które powstało bez formularza rejestracji z checkboxem).
     */
    val consentRequired: StateFlow<Boolean>

    /** Logowanie tokenem z systemowego okna Google. [rawNonce] to nonce przed hashowaniem. */
    suspend fun signInWithGoogle(idToken: String, rawNonce: String)

    suspend fun changePassword(newPassword: String)

    /** Zapisuje akceptację polityki prywatności w wersji [version]. */
    suspend fun giveConsent(version: String)
}

/** Wersja polityki prywatności. Podbij ją po zmianie treści polityki, a użytkownicy zaakceptują ją ponownie. */
const val CONSENT_VERSION = "2026-10"

/** Kody AppException zgłaszane przez logowanie i rejestrację. */
object AuthErrorCodes {
    const val INVALID_CREDENTIALS = "INVALID_CREDENTIALS"
    const val EMAIL_TAKEN = "EMAIL_TAKEN"
    const val WEAK_PASSWORD = "WEAK_PASSWORD"
    const val EMAIL_NOT_CONFIRMED = "EMAIL_NOT_CONFIRMED"
    const val NETWORK = "NETWORK_ERROR"
    const val UNKNOWN = "UNKNOWN"
}
