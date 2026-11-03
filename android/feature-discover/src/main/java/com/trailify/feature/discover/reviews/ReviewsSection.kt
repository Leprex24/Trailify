package com.trailify.feature.discover.reviews

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.trailify.core.domain.model.Review
import com.trailify.core.ui.PHOTO_TILE_SIZE
import com.trailify.core.ui.PhotoTile
import com.trailify.core.ui.UiState
import com.trailify.feature.discover.STAR_COLOR
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DATE_FORMAT = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("pl-PL"))
    .withZone(ZoneId.systemDefault())

/** Sekcja "Opinie" w szczegółach trasy: przycisk/wskazówka oceny i lista recenzji. */
@Composable
internal fun ReviewsSection(state: ReviewsUiState, viewModel: ReviewsViewModel, modifier: Modifier = Modifier) {
    var toDelete by remember { mutableStateOf<Review?>(null) }

    Column(modifier = modifier) {
        Text("Opinie (${state.totalCount})", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))

        when (state.eligibility) {
            ReviewEligibility.CAN_REVIEW -> Button(onClick = viewModel::openForm, modifier = Modifier.fillMaxWidth()) {
                Text("Oceń trasę")
            }
            ReviewEligibility.NOT_COMPLETED -> Hint("Przejdź tę trasę (przycisk powyżej), a potem wróć tu, żeby ją ocenić.")
            ReviewEligibility.OWN_ROUTE -> Hint("To Twoja trasa, więc nie możesz jej ocenić.")
            ReviewEligibility.ALREADY_REVIEWED -> Hint("Ta trasa ma już Twoją ocenę.")
            ReviewEligibility.LOGGED_OUT -> Hint("Zaloguj się, żeby ocenić trasę.")
            ReviewEligibility.CHECKING -> Unit
        }
        state.message?.let {
            Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
        }

        when (val reviews = state.reviews) {
            is UiState.Loading -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is UiState.Error -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(reviews.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                TextButton(onClick = viewModel::load) { Text("Ponów") }
            }
            is UiState.Success -> if (reviews.data.isEmpty()) {
                Text(
                    "Nikt jeszcze nie ocenił tej trasy.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
            } else {
                reviews.data.forEach { review ->
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    ReviewItem(
                        review = review,
                        isMine = review.user.id == state.currentUserId,
                        isDeleting = state.deletingId == review.id,
                        onDelete = { toDelete = review },
                    )
                }
            }
        }
    }

    toDelete?.let { review ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Usunąć opinię?") },
            text = { Text("Twoja ocena i zdjęcia zostaną usunięte z tej trasy.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteReview(review.id)
                    toDelete = null
                }) { Text("Usuń", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Anuluj") } },
        )
    }

    state.form?.let { form -> AddReviewSheet(form, viewModel) }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ReviewItem(review: Review, isMine: Boolean, isDeleting: Boolean, onDelete: () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Text(review.user.displayName.take(1).uppercase(), style = MaterialTheme.typography.titleMedium)
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(review.user.displayName + if (isMine) " (Ty)" else "", style = MaterialTheme.typography.titleSmall)
                Text(DATE_FORMAT.format(review.createdAt), style = MaterialTheme.typography.bodySmall)
            }
            if (isMine) {
                if (isDeleting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    TextButton(onClick = onDelete) { Text("Usuń") }
                }
            }
        }
        Stars(review.rating, modifier = Modifier.padding(top = 6.dp))
        review.comment?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp)) }
        if (review.photos.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                items(review.photos, key = { it.id }) { photo ->
                    AsyncImage(
                        model = photo.url,
                        contentDescription = "Zdjęcie z opinii",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(PHOTO_TILE_SIZE).clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                }
            }
        }
    }
}

@Composable
private fun Stars(rating: Int, modifier: Modifier = Modifier) {
    Row(modifier = modifier) {
        (1..5).forEach { i ->
            Icon(
                Icons.Default.Star,
                contentDescription = null,
                tint = if (i <= rating) STAR_COLOR else MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddReviewSheet(form: ReviewForm, viewModel: ReviewsViewModel) {
    val pickPhotos = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(REVIEW_PHOTOS_MAX),
    ) { uris -> viewModel.onPhotosPicked(uris.map { it.toString() }) }

    ModalBottomSheet(
        onDismissRequest = { if (!form.isSending) viewModel.closeForm() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Text("Oceń trasę", style = MaterialTheme.typography.titleLarge)

            Row(modifier = Modifier.padding(top = 12.dp)) {
                (1..5).forEach { i ->
                    IconButton(onClick = { viewModel.onRatingChange(i) }) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = "Ocena $i",
                            tint = if (i <= form.rating) STAR_COLOR else MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }
            }
            form.ratingError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

            OutlinedTextField(
                value = form.comment,
                onValueChange = viewModel::onCommentChange,
                label = { Text("Komentarz (opcjonalnie)") },
                isError = form.commentError != null,
                supportingText = { Text(form.commentError ?: "${form.comment.trim().length} / $REVIEW_COMMENT_MAX") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                minLines = 3,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )

            Text(
                "Zdjęcia (${form.photos.size} / $REVIEW_PHOTOS_MAX)",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(form.photos, key = { it.localUri }) { photo ->
                    PhotoTile(
                        photo = photo,
                        onRemove = { viewModel.removePhoto(photo.localUri) },
                        onRetry = { viewModel.retryPhoto(photo.localUri) },
                    )
                }
                if (form.canAddPhotos) {
                    item {
                        OutlinedCard(
                            onClick = { pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
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
                }
            }
            form.info?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }
            form.generalError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
            }

            Button(
                onClick = viewModel::submit,
                enabled = !form.isSending,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            ) {
                if (form.isSending) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Wyślij ocenę")
                }
            }
        }
    }
}
