package com.trailify.core.ui

import com.trailify.core.domain.model.AppException
import com.trailify.core.domain.repository.AuthErrorCodes

// Walidacja pól i komunikaty po polsku - wspólne dla logowania, rejestracji i ekranu konta.
// Funkcje zwracają tekst błędu albo null, gdy wszystko jest OK.

private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

/** Granice długości nazwy zgodne ze schematem ProfileUpdate w docs/openapi.yaml. */
const val DISPLAY_NAME_MIN = 2
const val DISPLAY_NAME_MAX = 50

const val PASSWORD_MIN = 8

fun validateEmail(email: String): String? = when {
    email.isBlank() -> "Wpisz adres e-mail"
    !EMAIL_REGEX.matches(email) -> "To nie wygląda na adres e-mail"
    else -> null
}

fun validateNewPassword(password: String): String? = when {
    password.length < PASSWORD_MIN -> "Hasło musi mieć co najmniej $PASSWORD_MIN znaków"
    password.none { it.isDigit() } -> "Dodaj co najmniej jedną cyfrę"
    else -> null
}

fun validateDisplayName(name: String): String? {
    val trimmed = name.trim()
    return when {
        trimmed.isEmpty() -> "Podaj nazwę"
        trimmed.length < DISPLAY_NAME_MIN -> "Nazwa musi mieć co najmniej $DISPLAY_NAME_MIN znaki"
        trimmed.length > DISPLAY_NAME_MAX -> "Nazwa może mieć maksymalnie $DISPLAY_NAME_MAX znaków"
        else -> null
    }
}

/** Komunikat dla użytkownika na podstawie błędu z repozytorium. */
fun Throwable.toUserMessage(): String = when ((this as? AppException)?.code) {
    AuthErrorCodes.INVALID_CREDENTIALS -> "Nieprawidłowy e-mail lub hasło"
    AuthErrorCodes.EMAIL_TAKEN -> "Konto z tym adresem już istnieje - zaloguj się"
    AuthErrorCodes.WEAK_PASSWORD -> "Hasło jest za słabe"
    AuthErrorCodes.EMAIL_NOT_CONFIRMED -> "Najpierw potwierdź adres e-mail (sprawdź skrzynkę)"
    AuthErrorCodes.NETWORK -> "Brak połączenia z internetem"
    "ACCOUNT_BLOCKED" -> "Konto zostało zablokowane"
    else -> "Coś poszło nie tak, spróbuj ponownie"
}
