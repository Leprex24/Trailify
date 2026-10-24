package com.trailify.feature.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trailify.core.ui.PasswordField
import org.koin.compose.viewmodel.koinViewModel

/** Ustawienia konta (W8, W9): nazwa, hasło, polityka prywatności, wylogowanie, usunięcie konta. */
@Composable
fun AccountScreen(
    onOpenPrivacyPolicy: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AccountViewModel = koinViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    if (ui.showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showDeleteDialog(false) },
            title = { Text("Usunąć konto?") },
            text = { Text("Usuniemy konto oraz Twoje trasy, aktywności i oceny. Tego nie da się cofnąć.") },
            confirmButton = {
                TextButton(onClick = viewModel::deleteAccount) {
                    Text("Usuń na zawsze", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showDeleteDialog(false) }) { Text("Anuluj") }
            },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        TextButton(onClick = onBack) { Text("← Wróć") }
        Text("Ustawienia konta", style = MaterialTheme.typography.headlineMedium)
        Text(ui.email, style = MaterialTheme.typography.bodyMedium)

        if (ui.isLoading) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        ui.message?.let {
            Text(
                it,
                color = if (ui.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }

        // --- Nazwa ---
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = ui.displayName,
            onValueChange = viewModel::onDisplayNameChange,
            label = { Text("Nazwa widoczna dla innych") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = viewModel::saveDisplayName, enabled = !ui.isLoading) { Text("Zapisz nazwę") }

        // --- Hasło ---
        Spacer(Modifier.height(24.dp))
        PasswordField(
            label = "Nowe hasło",
            value = ui.newPassword,
            onValueChange = viewModel::onNewPasswordChange,
            error = null,
        )
        Button(onClick = viewModel::changePassword, enabled = !ui.isLoading) { Text("Zmień hasło") }
        Text(
            "Konta Google logują się przez Google - hasło w Trailify nie jest im potrzebne.",
            style = MaterialTheme.typography.bodySmall,
        )

        // --- Prywatność i wylogowanie ---
        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        TextButton(onClick = onOpenPrivacyPolicy) { Text("Polityka prywatności") }
        OutlinedButton(
            onClick = viewModel::signOut,
            enabled = !ui.isLoading,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Wyloguj") }

        // --- Usunięcie konta ---
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = { viewModel.showDeleteDialog(true) },
            enabled = !ui.isLoading,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Usuń konto") }
    }
}
