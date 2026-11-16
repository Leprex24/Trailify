package com.trailify.feature.community.feed

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trailify.core.ui.BackButton
import com.trailify.core.ui.LoadingBox
import com.trailify.core.ui.UserAvatar
import org.koin.compose.viewmodel.koinViewModel

/** Szukanie osób po nazwie. Dotknięcie wyniku otwiera profil, gdzie można zacząć obserwować. */
@Composable
fun UserSearchScreen(
    onBack: () -> Unit,
    onOpenUser: (userId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UserSearchViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
            BackButton(onClick = onBack)
            Text("Znajdź osoby", style = MaterialTheme.typography.titleLarge)
        }
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            placeholder = { Text("Wpisz nazwę (min. $SEARCH_QUERY_MIN znaki)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).focusRequester(focus),
        )
        when {
            state.query.trim().length < SEARCH_QUERY_MIN -> Hint("Wpisz co najmniej $SEARCH_QUERY_MIN znaki nazwy użytkownika.")
            state.isLoading -> LoadingBox(Modifier.fillMaxSize())
            state.error != null && state.results.isEmpty() -> Column(Modifier.padding(16.dp)) {
                Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = viewModel::retry) { Text("Spróbuj ponownie") }
            }
            state.searched && state.results.isEmpty() -> Hint("Nie znaleziono nikogo o takiej nazwie.")
            else -> LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
                items(state.results, key = { it.id }) { user ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth().clickable { onOpenUser(user.id) }.padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        UserAvatar(user.displayName, user.id, user.avatarUrl, size = 44.dp)
                        Text(user.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(16.dp),
    )
}
