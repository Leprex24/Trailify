package com.trailify.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel

/** Logowanie i rejestracja (W26-W27). Na razie jeden przycisk logujący atrapę. */
@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = koinViewModel(),
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Trailify", style = MaterialTheme.typography.headlineMedium)
        Button(
            onClick = { viewModel.signIn("kacper@example.com", "haslo") },
            modifier = Modifier.padding(top = 16.dp),
        ) {
            Text("Zaloguj (atrapa)")
        }
    }
}
