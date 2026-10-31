package com.trailify.feature.community.publish

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trailify.core.domain.model.ActivityType
import com.trailify.core.ui.label
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Formularz publikacji trasy z zapisanej aktywności (W16-W19). */
@Composable
fun PublishRouteScreen(
    activityId: Long,
    activityType: ActivityType,
    onPublished: (routeId: Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PublishRouteViewModel = koinViewModel { parametersOf(activityId, activityType) },
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    LaunchedEffect(ui.publishedRouteId) { ui.publishedRouteId?.let(onPublished) }

    val pickPhotos = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(ROUTE_PHOTOS_MAX),
    ) { uris -> viewModel.onPhotosPicked(uris.map { it.toString() }) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        TextButton(onClick = onBack) { Text("← Wróć") }
        Text("Opublikuj trasę", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Trasa powstanie z zapisanej aktywności. Inni użytkownicy będą mogli ją przejść i ocenić.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = ui.name,
            onValueChange = viewModel::onNameChange,
            label = { Text("Nazwa trasy") },
            isError = ui.nameError != null,
            supportingText = { ui.nameError?.let { Text(it) } },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                imeAction = ImeAction.Next,
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = ui.description,
            onValueChange = viewModel::onDescriptionChange,
            label = { Text("Opis (opcjonalnie)") },
            isError = ui.descriptionError != null,
            supportingText = {
                Text(ui.descriptionError ?: "${ui.description.trim().length} / $ROUTE_DESCRIPTION_MAX")
            },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )

        SectionTitle("Zalecany typ aktywności")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActivityType.entries.forEach { type ->
                FilterChip(
                    selected = ui.activityType == type,
                    onClick = { viewModel.onActivityTypeChange(type) },
                    label = { Text(type.label()) },
                )
            }
        }

        SectionTitle("Charakter trasy")
        SwitchRow("Turystyczna", "Prowadzi przez atrakcje lub szlaki", ui.isTourist, viewModel::onTouristChange)
        SwitchRow("Widokowa", "Ładne widoki po drodze", ui.isScenic, viewModel::onScenicChange)

        SectionTitle("Widoczność")
        SwitchRow(
            title = "Publiczna",
            subtitle = if (ui.isPublic) "Widoczna dla wszystkich w Odkrywaj" else "Prywatna - widzisz ją tylko Ty",
            checked = ui.isPublic,
            onChange = viewModel::onPublicChange,
        )

        SectionTitle("Zdjęcia (${ui.photos.size} / $ROUTE_PHOTOS_MAX)")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ui.photos, key = { it.localUri }) { photo ->
                PhotoTile(
                    photo = photo,
                    onRemove = { viewModel.removePhoto(photo.localUri) },
                    onRetry = { viewModel.retryPhoto(photo.localUri) },
                )
            }
            if (ui.canAddPhotos) {
                item {
                    OutlinedCard(
                        onClick = {
                            pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.size(TILE_SIZE),
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
            }
        }
        ui.info?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }

        ui.generalError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 16.dp))
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = viewModel::publish,
            enabled = !ui.isPublishing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (ui.isPublishing) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text("Opublikuj")
            }
        }
    }
}

private val TILE_SIZE = 96.dp

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun PhotoTile(photo: PhotoItem, onRemove: () -> Unit, onRetry: () -> Unit) {
    val sizePx = with(LocalDensity.current) { TILE_SIZE.roundToPx() }
    val thumbnail by rememberThumbnail(photo.localUri, sizePx)
    Box(
        modifier = Modifier
            .size(TILE_SIZE)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        thumbnail?.let {
            Image(it, contentDescription = "Wybrane zdjęcie", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        when {
            photo.isUploading -> Box(Modifier.fillMaxSize().background(SCRIM), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp, color = Color.White)
            }
            photo.error != null -> Box(Modifier.fillMaxSize().background(SCRIM), contentAlignment = Alignment.Center) {
                IconButton(onClick = onRetry) {
                    Icon(Icons.Default.Refresh, contentDescription = "Wyślij zdjęcie ponownie", tint = Color.White)
                }
            }
        }
        IconButton(
            onClick = onRemove,
            modifier = Modifier.align(Alignment.TopEnd).size(32.dp),
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Usuń zdjęcie",
                tint = Color.White,
                modifier = Modifier.background(SCRIM, RoundedCornerShape(50)).padding(2.dp),
            )
        }
    }
}

private val SCRIM = Color.Black.copy(alpha = 0.45f)
