package com.trailify.backend.account

import com.trailify.backend.web.ApiException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClientException
import java.util.UUID

@Service
class AccountService(
    private val profiles: ProfileRepository,
    private val admin: SupabaseAdminClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Usuwa konto wraz ze wszystkimi danymi (W27). Kolejność ma znaczenie:
     *  1. niezatwierdzone zgłoszenia punktów (po usunięciu konta zostałyby bez właściciela),
     *  2. pliki ze Storage,
     *  3. konto w Supabase Auth: kaskadowo znika profil, trasy, aktywności, recenzje i zebrane punkty.
     * Każdy krok można bezpiecznie powtórzyć, więc po błędzie użytkownik po prostu próbuje jeszcze raz.
     */
    fun deleteAccount(userId: UUID) {
        if (!admin.isConfigured) {
            throw ApiException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "SERVICE_UNAVAILABLE",
                "Usuwanie kont nie jest skonfigurowane na serwerze (brak SUPABASE_SECRET_KEY)",
            )
        }
        try {
            profiles.deleteUnpublishedPoiSubmissions(userId)
            admin.deleteUserFiles(userId)
            admin.deleteUser(userId)
        } catch (e: RestClientException) {
            log.error("Usuwanie konta {} nie powiodło się", userId, e)
            throw ApiException(HttpStatus.BAD_GATEWAY, "UPSTREAM_ERROR", "Nie udało się usunąć konta, spróbuj ponownie")
        }
    }
}
