package com.trailify.feature.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.security.MessageDigest
import java.util.UUID

data class GoogleToken(val idToken: String, val rawNonce: String)

/** Pokazuje systemowe okno wyboru konta Google i zwraca token dla Supabase. */
internal object GoogleSignIn {

    /**
     * Zwraca token albo null, gdy użytkownik zamknął okno.
     * Inne problemy (zła konfiguracja, brak sieci) rzucają wyjątek - szczegóły w Logcat.
     * [context] musi być ekranem (Activity) - w Compose to LocalContext.current.
     * [webClientId] to Web Client ID z Google Cloud (nie Android Client ID).
     */
    suspend fun requestToken(context: Context, webClientId: String): GoogleToken? {
        // Nonce = jednorazowy losowy kod. Google dostaje jego skrót (SHA-256), Supabase oryginał,
        // dzięki temu Supabase wie, że token powstał przed chwilą w naszej aplikacji.
        val rawNonce = UUID.randomUUID().toString()
        val hashedNonce = MessageDigest.getInstance("SHA-256")
            .digest(rawNonce.toByteArray())
            .joinToString("") { "%02x".format(it) }

        val option = GetSignInWithGoogleOption.Builder(webClientId)
            .setNonce(hashedNonce)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()

        return try {
            val result = CredentialManager.create(context).getCredential(context, request)
            val credential = GoogleIdTokenCredential.createFrom(result.credential.data)
            GoogleToken(idToken = credential.idToken, rawNonce = rawNonce)
        } catch (e: GetCredentialCancellationException) {
            null
        }
    }
}
