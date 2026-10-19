package com.trailify.feature.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.trailify.core.domain.repository.CONSENT_VERSION

// SZKIC do uzupełnienia razem z promotorem. Wpiszcie prawdziwe dane administratora.
// Po zmianie treści podbij CONSENT_VERSION (core-domain), a użytkownicy zaakceptują ją ponownie.
private val POLICY_SECTIONS = listOf(
    "Kto przetwarza dane" to
        "Administratorem danych są autorzy aplikacji Trailify (projekt inżynierski). Kontakt: [WPISZ E-MAIL].",
    "Jakie dane zbieramy" to
        "Adres e-mail, nazwę wyświetlaną, zarejestrowane trasy i ślady GPS, zdjęcia oraz oceny dodane do tras, zebrane ciekawe punkty.",
    "Po co" to
        "Żeby prowadzić Twoje konto, zapisywać i pokazywać Twoje aktywności, udostępniać trasy, które sam opublikujesz, oraz przyznawać punkty POI.",
    "Lokalizacja" to
        "Lokalizację zbieramy tylko podczas aktywności, którą sam uruchomisz. Trasy oznaczone jako prywatne widzisz tylko Ty.",
    "Jak długo" to
        "Do czasu usunięcia konta. Usunięcie konta w aplikacji kasuje Twoje dane z naszej bazy.",
    "Twoje prawa" to
        "Możesz w każdej chwili zmienić dane konta, usunąć konto wraz z danymi (Profil → Ustawienia konta → Usuń konto) i wycofać zgodę.",
)

/** Sama treść polityki - używana na ekranie polityki i na ekranie zgody. */
@Composable
fun PrivacyPolicyText() {
    Column {
        Text("Wersja $CONSENT_VERSION", style = MaterialTheme.typography.labelMedium)
        POLICY_SECTIONS.forEach { (title, body) ->
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        TextButton(onClick = onBack) { Text("← Wróć") }
        Spacer(Modifier.height(8.dp))
        Text("Polityka prywatności", style = MaterialTheme.typography.headlineMedium)
        PrivacyPolicyText()
    }
}
