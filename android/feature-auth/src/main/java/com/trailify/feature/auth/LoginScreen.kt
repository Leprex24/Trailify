package com.trailify.feature.auth

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trailify.core.ui.PasswordField
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

/** Logowanie e-mailem lub przez Google (W5, W6). Przejście dalej obsługuje TrailifyNavHost. */
@Composable
fun LoginScreen(
    onGoToRegister: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = koinViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Trailify", style = MaterialTheme.typography.headlineMedium)
        Text("Zaloguj się", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(24.dp))

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
            label = "Hasło",
            value = ui.password,
            onValueChange = viewModel::onPasswordChange,
            error = ui.passwordError,
        )

        ui.generalError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp))
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
                Text("Zaloguj")
            }
        }

        viewModel.googleWebClientId?.let { webClientId ->
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    scope.launch {
                        try {
                            GoogleSignIn.requestToken(context, webClientId)?.let(viewModel::onGoogleToken)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.e("GoogleSignIn", "Błąd logowania Google", e) // szczegóły w Logcat
                            viewModel.onGoogleError()
                        }
                    }
                },
                enabled = !ui.isLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Zaloguj przez Google")
            }
        }

        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onGoToRegister, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Nie masz konta? Zarejestruj się")
        }
    }
}
