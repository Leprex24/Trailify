package com.trailify.core.data.mock

import com.trailify.core.domain.repository.AccountAuthRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Atrapa funkcji konta: Google wyłączony, zgoda zaakceptowana, zmiana hasła zawsze się udaje. */
class MockAccountAuthRepository : AccountAuthRepository {
    override val googleWebClientId: String? = null

    private val consent = MutableStateFlow(false)
    override val consentRequired: StateFlow<Boolean> = consent.asStateFlow()

    override suspend fun signInWithGoogle(idToken: String, rawNonce: String) = Unit

    override suspend fun changePassword(newPassword: String) {
        delay(300)
    }

    override suspend fun giveConsent(version: String) {
        delay(300)
        consent.value = false
    }
}
