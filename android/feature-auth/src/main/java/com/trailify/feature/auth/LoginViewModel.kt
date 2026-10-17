package com.trailify.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trailify.core.domain.repository.AuthRepository
import kotlinx.coroutines.launch

class LoginViewModel(private val auth: AuthRepository) : ViewModel() {

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            // TODO (Mikołaj): obsługa stanu ładowania i błędów
            auth.signIn(email, password)
        }
    }
}
