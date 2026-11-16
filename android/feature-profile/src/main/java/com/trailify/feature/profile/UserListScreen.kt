package com.trailify.feature.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trailify.core.domain.model.UserSummary
import com.trailify.core.ui.BackButton
import com.trailify.core.ui.LoadingBox
import com.trailify.core.ui.UserAvatar
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Lista obserwujących albo obserwowanych. Dotknięcie osoby otwiera jej profil. */
@Composable
fun UserListScreen(
    userId: String,
    kind: UserListKind,
    onBack: () -> Unit,
    onOpenUser: (userId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UserListViewModel = koinViewModel(key = "users-$userId-${kind.route}") { parametersOf(userId, kind) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var toRemove by remember { mutableStateOf<UserSummary?>(null) }
    LaunchedEffect(userId, kind) { viewModel.load() }

    toRemove?.let { user ->
        AlertDialog(
            onDismissRequest = { toRemove = null },
            title = { Text("Usunąć z obserwujących?") },
            text = { Text("${user.displayName} przestanie Cię obserwować i nie zobaczy Twoich nowych wpisów.") },
            confirmButton = {
                TextButton(onClick = { viewModel.removeFollower(user); toRemove = null }) { Text("Usuń") }
            },
            dismissButton = { TextButton(onClick = { toRemove = null }) { Text("Anuluj") } },
        )
    }

    Column(modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
            BackButton(onClick = onBack)
            Text(kind.title, style = MaterialTheme.typography.titleLarge)
        }
        state.message?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
        }
        when {
            state.isLoading -> LoadingBox(Modifier.fillMaxSize())
            state.hidden -> Hint("Ten profil jest prywatny, więc lista jest ukryta.")
            state.error != null && state.items.isEmpty() -> RetryLine(state.error.orEmpty(), viewModel::load)
            state.items.isEmpty() -> Hint(
                if (kind == UserListKind.FOLLOWERS) "Nikt jeszcze nie obserwuje tego profilu." else "Ten profil nikogo jeszcze nie obserwuje.",
            )
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(state.items, key = { it.id }) { user ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth().clickable { onOpenUser(user.id) }.padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        UserAvatar(user.displayName, user.id, user.avatarUrl, size = 44.dp)
                        Text(
                            user.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (viewModel.canRemove) TextButton(onClick = { toRemove = user }) { Text("Usuń") }
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
                if (state.hasMore || state.isLoadingMore || state.error != null) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            TextButton(onClick = viewModel::loadMore, enabled = !state.isLoadingMore) {
                                Text(if (state.isLoadingMore) "Wczytywanie..." else "Pokaż więcej")
                            }
                        }
                    }
                }
            }
        }
    }
}
