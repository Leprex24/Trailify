package com.trailify.feature.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.trailify.core.ui.UserAvatar
import org.koin.compose.viewmodel.koinViewModel

/**
 * Okno "Edytuj profil": nazwa, zdjęcie profilowe, tło i przełącznik profilu prywatnego.
 * Zdjęcia wysyłają się od razu po wyborze, a wszystkie zmiany zapisuje przycisk "Zapisz".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileSheet(
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    viewModel: EditProfileViewModel = koinViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.start() }
    LaunchedEffect(ui.saved) {
        if (ui.saved) {
            viewModel.consumeSaved() // ViewModel żyje dłużej niż okno, więc stary "saved" nie może zamknąć następnego
            onSaved()
        }
    }

    val pickAvatar = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.onPhotoPicked(ProfilePhoto.AVATAR, uri.toString())
    }
    val pickBackground = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.onPhotoPicked(ProfilePhoto.BACKGROUND, uri.toString())
    }
    val imageOnly = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp),
        ) {
            Text("Edytuj profil", style = MaterialTheme.typography.titleLarge)

            if (ui.isLoading) {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                Text("Tło", style = MaterialTheme.typography.labelLarge)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { pickBackground.launch(imageOnly) },
                ) {
                    ui.backgroundUrl?.let {
                        AsyncImage(it, contentDescription = "Tło profilu", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(110.dp))
                    }
                    if (ProfilePhoto.BACKGROUND in ui.uploading) CircularProgressIndicator()
                }
                OutlinedButton(onClick = { pickBackground.launch(imageOnly) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (ui.backgroundUrl == null) "Dodaj tło" else "Zmień tło")
                }

                Text("Zdjęcie profilowe", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        UserAvatar(ui.displayName, ui.userId, ui.avatarUrl, size = 72.dp)
                        if (ProfilePhoto.AVATAR in ui.uploading) CircularProgressIndicator()
                    }
                    OutlinedButton(onClick = { pickAvatar.launch(imageOnly) }) {
                        Text(if (ui.avatarUrl == null) "Dodaj zdjęcie" else "Zmień zdjęcie")
                    }
                }

                OutlinedTextField(
                    value = ui.displayName,
                    onValueChange = viewModel::onDisplayNameChange,
                    label = { Text("Nazwa widoczna dla innych") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("Profil prywatny", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Inni zobaczą tylko Twoją nazwę i zdjęcia. Statystyki, aktywności i trasy będą ukryte.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = ui.isPrivate, onCheckedChange = viewModel::onPrivateChange)
                }
            }

            ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Spacer(Modifier.height(4.dp))
            Button(onClick = viewModel::save, enabled = ui.canSave, modifier = Modifier.fillMaxWidth()) {
                Text(if (ui.isSaving) "Zapisywanie..." else "Zapisz")
            }
        }
    }
}
