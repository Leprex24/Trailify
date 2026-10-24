package com.trailify.feature.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

/**
 * Bramka zgody RODO (W7): gdy zalogowany użytkownik nie zaakceptował aktualnej polityki
 * prywatności (np. nowe konto Google), zamiast aplikacji ([content]) pokazujemy ekran zgody.
 */
@Composable
fun ConsentGate(
    viewModel: ConsentViewModel = koinViewModel(),
    content: @Composable () -> Unit,
) {
    val required by viewModel.consentRequired.collectAsStateWithLifecycle()
    if (required) {
        val ui by viewModel.ui.collectAsStateWithLifecycle()
        ConsentScreen(
            isLoading = ui.isLoading,
            error = ui.error,
            onAccept = viewModel::accept,
            onDecline = viewModel::decline,
        )
    } else {
        content()
    }
}

@Composable
private fun ConsentScreen(
    isLoading: Boolean,
    error: String?,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
        ) {
            Text("Zanim zaczniesz", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Przeczytaj, jak Trailify korzysta z Twoich danych. " +
                    "Bez akceptacji nie możemy prowadzić Twojego konta - zostanie ono usunięte.",
                style = MaterialTheme.typography.bodyMedium,
            )
            PrivacyPolicyText()
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 16.dp))
            }
            Spacer(Modifier.height(24.dp))
            Button(onClick = onAccept, enabled = !isLoading, modifier = Modifier.fillMaxWidth()) {
                Text("Akceptuję regulamin i politykę prywatności")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onDecline, enabled = !isLoading, modifier = Modifier.fillMaxWidth()) {
                Text("Nie akceptuję - usuń konto")
            }
        }
    }
}
