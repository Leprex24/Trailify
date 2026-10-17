package com.trailify.core.ui

/** Stan ekranu ładującego dane: ładowanie, dane albo błąd. */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
