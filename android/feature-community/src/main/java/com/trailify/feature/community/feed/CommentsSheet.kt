package com.trailify.feature.community.feed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trailify.core.domain.model.Comment
import com.trailify.core.domain.model.FeedItem
import com.trailify.core.ui.UserAvatar
import com.trailify.core.ui.formatTimeAgo
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import java.time.Instant

/**
 * Komentarze wpisu w oknie od dołu. "Odpowiedz" oznacza osobę: jej nazwa pojawia się na początku komentarza
 * jako @oznaczenie, a serwer wie, komu odpowiadamy. [onCount] przekazuje aktualną liczbę komentarzy na kartę.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CommentsSheet(
    item: FeedItem,
    onCount: (Int) -> Unit,
    onOpenUser: (String) -> Unit,
    onDismiss: () -> Unit,
    viewModel: CommentsViewModel = koinViewModel(key = "comments-${item.id}") { parametersOf(item.id, item.author.id) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.total, state.isLoading) { if (!state.isLoading) onCount(state.total) }
    val now = remember { Instant.now() }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.imePadding().padding(horizontal = 16.dp)) {
            Text("Komentarze", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
            when {
                state.isLoading -> Box(Modifier.fillMaxWidth().heightIn(min = 120.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                state.error != null -> Column(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                    Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = viewModel::load) { Text("Spróbuj ponownie") }
                }
                state.comments.isEmpty() -> Text(
                    "Nikt jeszcze nie skomentował. Napisz pierwszy komentarz.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.heightIn(max = 360.dp),
                ) {
                    items(state.comments, key = { it.id }) { comment ->
                        CommentRow(
                            comment = comment,
                            now = now,
                            canReply = comment.author.id != viewModel.meId,
                            canDelete = canDeleteComment(comment, viewModel.meId, viewModel.eventAuthorId),
                            onReply = { viewModel.startReply(comment.author) },
                            onDelete = { viewModel.delete(comment) },
                            onOpenUser = onOpenUser,
                        )
                    }
                }
            }

            state.replyTo?.let { target ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Text(
                        "Odpowiedź dla @${target.displayName}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = viewModel::cancelReply) { Text("Anuluj") }
                }
            }
            state.message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)) {
                OutlinedTextField(
                    value = state.draft,
                    onValueChange = viewModel::onDraftChange,
                    placeholder = { Text("Dodaj komentarz") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    maxLines = 4,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = viewModel::send,
                    enabled = state.draft.isNotBlank() && !state.isSending,
                    modifier = Modifier.padding(start = 8.dp),
                ) { Text(if (state.isSending) "..." else "Wyślij") }
            }
        }
    }
}

@Composable
private fun CommentRow(
    comment: Comment,
    now: Instant,
    canReply: Boolean,
    canDelete: Boolean,
    onReply: () -> Unit,
    onDelete: () -> Unit,
    onOpenUser: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        UserAvatar(
            comment.author.displayName,
            comment.author.id,
            comment.author.avatarUrl,
            size = 32.dp,
            modifier = Modifier.clickable { onOpenUser(comment.author.id) },
        )
        Column(Modifier.weight(1f)) {
            Row {
                Text(comment.author.displayName, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(
                    "  ·  " + formatTimeAgo(now, comment.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(commentText(comment, MaterialTheme.colorScheme.primary), style = MaterialTheme.typography.bodyMedium)
            Row {
                if (canReply) TextButton(onClick = onReply) { Text("Odpowiedz", style = MaterialTheme.typography.labelMedium) }
                if (canDelete) TextButton(onClick = onDelete) { Text("Usuń", style = MaterialTheme.typography.labelMedium) }
            }
        }
    }
}

/** Treść komentarza; przy odpowiedzi na początku wyróżnione @oznaczenie osoby. */
private fun commentText(comment: Comment, mentionColor: androidx.compose.ui.graphics.Color) = buildAnnotatedString {
    comment.replyTo?.let {
        withStyle(SpanStyle(color = mentionColor, fontWeight = FontWeight.Bold)) { append("@${it.displayName} ") }
    }
    append(comment.body)
}
