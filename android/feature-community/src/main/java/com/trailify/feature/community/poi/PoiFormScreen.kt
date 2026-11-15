package com.trailify.feature.community.poi

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trailify.core.ui.MapMarker
import com.trailify.core.ui.MarkersMap
import com.trailify.core.ui.PHOTO_TILE_SIZE
import com.trailify.core.ui.PhotoTile
import kotlinx.coroutines.delay
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import java.util.Locale

/** Zgłoszenie punktu przez użytkownika (W22) albo dodanie go przez administratora ([asAdmin], W31). */
@Composable
fun PoiFormScreen(
    asAdmin: Boolean,
    onDone: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PoiFormViewModel = koinViewModel(key = "poi-form-$asAdmin") { parametersOf(asAdmin) },
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.onPhotoPicked(it.toString()) }
    }

    LaunchedEffect(ui.done) {
        if (ui.done) {
            delay(DONE_VISIBLE_MS)
            onDone()
        }
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
    ) {
        TextButton(onClick = onBack) { Text("← Wróć") }
        Text(if (asAdmin) "Dodaj punkt" else "Zaproponuj punkt", style = MaterialTheme.typography.headlineMedium)
        Text(
            if (asAdmin) {
                "Punkt dodany przez administratora od razu pojawi się na mapie."
            } else {
                "Po zatwierdzeniu przez administratora punkt pojawi się na mapie i inni będą mogli go zebrać."
            },
            style = MaterialTheme.typography.bodyMedium,
        )

        if (ui.done) {
            Text(
                if (asAdmin) "Punkt dodany." else "Dziękujemy! Zgłoszenie czeka na zatwierdzenie.",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 24.dp),
            )
            return@Column
        }

        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = ui.name,
            onValueChange = viewModel::onNameChange,
            label = { Text("Nazwa") },
            isError = ui.nameError != null,
            supportingText = { ui.nameError?.let { Text(it) } },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = ui.description,
            onValueChange = viewModel::onDescriptionChange,
            label = { Text("Opis") },
            isError = ui.descriptionError != null,
            supportingText = { Text(ui.descriptionError ?: "${ui.description.trim().length} / $POI_DESCRIPTION_MAX") },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )

        Text("Miejsce", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        Text(
            when {
                ui.position == null -> "Dotknij mapy, żeby wybrać miejsce."
                ui.positionFromMap -> "Wybrane na mapie. Dotknij innego miejsca, żeby je zmienić."
                else -> "Twoja aktualna pozycja. Dotknij mapy, żeby wybrać inne miejsce."
            },
            style = MaterialTheme.typography.bodySmall,
        )
        MarkersMap(
            markers = listOfNotNull(ui.position?.let { MapMarker(1L, it) }),
            selectedId = null,
            onSelect = {},
            onMapTap = viewModel::onMapTap,
            // kamera ustawia się raz, potem przesuwa ją użytkownik
            fitToMarkers = false,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(220.dp).clip(RoundedCornerShape(16.dp)),
        )
        ui.position?.let {
            Text(
                String.format(Locale.ROOT, "%.5f, %.5f", it.latitude, it.longitude),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Text("Zdjęcie (opcjonalnie)", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
        val photo = ui.photo
        if (photo != null) {
            PhotoTile(photo = photo, onRemove = viewModel::removePhoto, onRetry = { viewModel.retryPhoto() })
        } else {
            OutlinedCard(
                onClick = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier.size(PHOTO_TILE_SIZE),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("Dodaj", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        ui.generalError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 16.dp)) }
        Row(Modifier.padding(top = 24.dp)) {
            Button(onClick = viewModel::submit, enabled = !ui.isSending, modifier = Modifier.fillMaxWidth()) {
                if (ui.isSending) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(if (asAdmin) "Dodaj punkt" else "Wyślij zgłoszenie")
                }
            }
        }
    }
}

private const val DONE_VISIBLE_MS = 1_500L
