package com.trailify.core.data.supabase

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.trailify.core.domain.model.AppException
import com.trailify.core.domain.model.UserSummary
import com.trailify.core.domain.repository.AccountAuthRepository
import com.trailify.core.domain.repository.AuthErrorCodes
import com.trailify.core.domain.repository.AuthRepository
import com.trailify.core.domain.repository.CONSENT_VERSION
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.exceptions.HttpRequestException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.Instant

/**
 * Logowanie przez Supabase Auth (e-mail i Google).
 *
 * Supabase sam zapisuje sesję w telefonie i odtwarza ją po restarcie aplikacji.
 * Profil (tabela `profile`) tworzy trigger z migracji V1 na podstawie `display_name`
 * z metadanych konta. Zgodę RODO zapisujemy w metadanych konta Supabase
 * (`gdpr_consent_version`, `gdpr_consent_at`).
 */
class SupabaseAuthRepository(
    private val supabase: SupabaseClient,
    private val context: Context,
    scope: CoroutineScope,
    googleWebClientId: String,
) : AuthRepository, AccountAuthRepository {

    override val googleWebClientId: String? = googleWebClientId.ifBlank { null }

    private val user = MutableStateFlow<UserSummary?>(null)
    override val currentUser: StateFlow<UserSummary?> = user.asStateFlow()

    private val consent = MutableStateFlow(false)
    override val consentRequired: StateFlow<Boolean> = consent.asStateFlow()

    init {
        scope.launch {
            supabase.auth.sessionStatus.collect { status ->
                val info = when (status) {
                    is SessionStatus.Authenticated -> status.session.user ?: supabase.auth.currentUserOrNull()
                    // np. brak internetu: sesja jest, tylko nie udało się jej odświeżyć
                    is SessionStatus.RefreshFailure -> supabase.auth.currentUserOrNull()
                    else -> null
                }
                user.value = info?.toUserSummary()
                consent.value = info != null && info.metadata(KEY_CONSENT_VERSION) != CONSENT_VERSION
            }
        }
    }

    override suspend fun signIn(email: String, password: String) = runAuth {
        supabase.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    /**
     * Rejestracja jest możliwa tylko po zaznaczeniu zgody na ekranie, dlatego od razu zapisujemy
     * akceptację aktualnej polityki. Gdy w Supabase włączone jest "Confirm email", konto powstaje,
     * ale [currentUser] zostaje null do czasu potwierdzenia adresu.
     */
    override suspend fun signUp(email: String, password: String, displayName: String) = runAuth {
        supabase.auth.signUpWith(Email) {
            this.email = email
            this.password = password
            data = buildJsonObject {
                put(KEY_DISPLAY_NAME, displayName)
                put(KEY_CONSENT_VERSION, CONSENT_VERSION)
                put(KEY_CONSENT_AT, Instant.now().toString())
            }
        }
    }

    override suspend fun signInWithGoogle(idToken: String, rawNonce: String) = runAuth {
        supabase.auth.signInWith(IDToken) {
            this.idToken = idToken
            provider = Google
            nonce = rawNonce
        }
    }

    override suspend fun changePassword(newPassword: String) = runAuth {
        supabase.auth.updateUser { password = newPassword }
    }

    override suspend fun giveConsent(version: String) = runAuth {
        supabase.auth.updateUser {
            data = buildJsonObject {
                put(KEY_CONSENT_VERSION, version)
                put(KEY_CONSENT_AT, Instant.now().toString())
            }
        }
        consent.value = version != CONSENT_VERSION
    }

    override suspend fun signOut() {
        try {
            supabase.auth.signOut()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // np. brak internetu albo konto już usunięte - sesję w telefonie i tak czyścimy
            Log.w(TAG, "Wylogowanie na serwerze nie powiodło się, czyszczę sesję lokalnie", e)
            supabase.auth.clearSession()
        }
        // Przy następnym logowaniu Google znów zapyta, którego konta użyć.
        runCatching {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        }
    }

    /** Wykonuje operację i zamienia wyjątki Supabase na AppException z kodem z [AuthErrorCodes]. */
    private suspend fun runAuth(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: AuthRestException) {
            Log.w(TAG, "Błąd Supabase Auth: ${e.error}", e)
            val code = when (e.errorCode) {
                AuthErrorCode.InvalidCredentials -> AuthErrorCodes.INVALID_CREDENTIALS
                AuthErrorCode.UserAlreadyExists, AuthErrorCode.EmailExists -> AuthErrorCodes.EMAIL_TAKEN
                AuthErrorCode.WeakPassword -> AuthErrorCodes.WEAK_PASSWORD
                AuthErrorCode.EmailNotConfirmed -> AuthErrorCodes.EMAIL_NOT_CONFIRMED
                else -> AuthErrorCodes.UNKNOWN
            }
            throw AppException(code, e.message ?: code)
        } catch (e: HttpRequestException) {
            Log.w(TAG, "Brak połączenia z Supabase", e)
            throw AppException(AuthErrorCodes.NETWORK, e.message ?: "Brak połączenia")
        } catch (e: AppException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Nieznany błąd logowania", e)
            throw AppException(AuthErrorCodes.UNKNOWN, e.message ?: "Nieznany błąd")
        }
    }

    private fun UserInfo.metadata(key: String): String? =
        userMetadata?.get(key)?.jsonPrimitive?.contentOrNull

    private fun UserInfo.toUserSummary() = UserSummary(
        id = id,
        displayName = metadata(KEY_DISPLAY_NAME)
            ?: metadata("full_name") // konta Google
            ?: email?.substringBefore('@')
            ?: "Użytkownik",
        avatarUrl = metadata("avatar_url"),
    )

    private companion object {
        const val TAG = "SupabaseAuth"
        const val KEY_DISPLAY_NAME = "display_name"
        const val KEY_CONSENT_VERSION = "gdpr_consent_version"
        const val KEY_CONSENT_AT = "gdpr_consent_at"
    }
}
