package com.trailify.feature.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trailify.core.ui.PasswordField
import org.koin.compose.viewmodel.koinViewModel

/** Rejestracja e-mailem ze zgodą RODO (W5). */
@Composable
fun RegisterScreen(
    onOpenPrivacyPolicy: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RegisterViewModel = koinViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        TextButton(onClick = onBack) { Text("← Wróć") }
        Spacer(Modifier.height(8.dp))
        Text("Załóż konto", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = ui.displayName,
            onValueChange = viewModel::onDisplayNameChange,
            label = { Text("Nazwa widoczna dla innych") },
            isError = ui.displayNameError != null,
            supportingText = { ui.displayNameError?.let { Text(it) } },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = ui.email,
            onValueChange = viewModel::onEmailChange,
            label = { Text("E-mail") },
            isError = ui.emailError != null,
            supportingText = { ui.emailError?.let { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        PasswordField(
            label = "Hasło (min. 8 znaków, w tym cyfra)",
            value = ui.password,
            onValueChange = viewModel::onPasswordChange,
            error = ui.passwordError,
            imeAction = ImeAction.Next,
        )
        PasswordField(
            label = "Powtórz hasło",
            value = ui.repeatPassword,
            onValueChange = viewModel::onRepeatPasswordChange,
            error = ui.repeatPasswordError,
        )

        // Zgoda RODO: niezaznaczona domyślnie, z linkiem do polityki prywatności
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = ui.consent, onCheckedChange = viewModel::onConsentChange)
            Text(
                "Akceptuję regulamin i ",
                modifier = Modifier.clickable { viewModel.onConsentChange(!ui.consent) },
            )
            TextButton(onClick = onOpenPrivacyPolicy, contentPadding = PaddingValues(0.dp)) {
                Text("politykę prywatności")
            }
        }
        ui.consentError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        ui.generalError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp))
        }
        ui.infoMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 8.dp))
        }
        Spacer(Modifier.height(16.dp))

        Button(
            onClick = viewModel::submit,
            enabled = !ui.isLoading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (ui.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text("Załóż konto")
            }
        }
    }
}
