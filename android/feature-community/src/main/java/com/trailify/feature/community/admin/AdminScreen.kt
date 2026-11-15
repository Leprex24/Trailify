package com.trailify.feature.community.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role as SemanticsRole
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.trailify.core.domain.model.AdminUser
import com.trailify.core.domain.model.PoiAdmin
import com.trailify.core.domain.model.PoiStatus
import com.trailify.core.domain.model.Role
import com.trailify.core.domain.model.UserStatus
import com.trailify.core.ui.EmptyBox
import com.trailify.core.ui.ErrorBox
import com.trailify.core.ui.LoadingBox
import com.trailify.core.ui.UiState
import com.trailify.feature.community.poi.REJECT_REASON_MAX
import org.koin.compose.viewmodel.koinViewModel
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DATE_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.forLanguageTag("pl-PL")).withZone(ZoneId.systemDefault())

/** Panel administratora (W29-W34). Serwer i tak wpuszcza tylko rolę ADMIN; przycisk w Profilu widać tylko adminom. */
@Composable
fun AdminScreen(
    onAddPoi: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AdminViewModel = koinViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    // po powrocie z formularza "Dodaj punkt" lista ma pokazać nowy punkt
    LifecycleStartEffect(Unit) {
        viewModel.loadPois()
        onStopOrDispose { }
    }

    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TextButton(onClick = onBack, modifier = Modifier.padding(8.dp)) { Text("← Wróć") }
            Text("Panel administratora", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 16.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                AdminTab.entries.forEachIndexed { index, tab ->
                    SegmentedButton(
                        selected = ui.tab == tab,
                        onClick = { viewModel.onTabChange(tab) },
                        shape = SegmentedButtonDefaults.itemShape(index, AdminTab.entries.size),
                    ) { Text(if (tab == AdminTab.POIS) "Ciekawe punkty" else "Użytkownicy") }
                }
            }
            ui.message?.let {
                Text(
                    it,
                    color = if (ui.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            when (ui.tab) {
                AdminTab.POIS -> PoisTab(ui, viewModel)
                AdminTab.USERS -> UsersTab(ui, viewModel)
            }
        }
        if (ui.tab == AdminTab.POIS) {
            ExtendedFloatingActionButton(
                onClick = onAddPoi,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Dodaj punkt") },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }
}

private fun PoiStatus?.label() = when (this) {
    PoiStatus.PENDING -> "Do zatwierdzenia"
    PoiStatus.APPROVED -> "Zatwierdzone"
    PoiStatus.REJECTED -> "Odrzucone"
    null -> "Wszystkie"
}

@Composable
private fun PoisTab(ui: AdminUiState, viewModel: AdminViewModel) {
    var rejecting by remember { mutableStateOf<PoiAdmin?>(null) }
    var deleting by remember { mutableStateOf<PoiAdmin?>(null) }
    var editing by remember { mutableStateOf<PoiAdmin?>(null) }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
    ) {
        listOf(PoiStatus.PENDING, PoiStatus.APPROVED, PoiStatus.REJECTED, null).forEach { status ->
            FilterChip(
                selected = ui.poiStatus == status,
                onClick = { viewModel.onPoiStatusChange(status) },
                label = { Text(status.label()) },
            )
        }
    }
    when (val pois = ui.pois) {
        is UiState.Loading -> LoadingBox()
        is UiState.Error -> ErrorBox(pois.message, onRetry = viewModel::loadPois)
        is UiState.Success -> if (pois.data.isEmpty()) {
            EmptyBox(if (ui.poiStatus == PoiStatus.PENDING) "Brak zgłoszeń do sprawdzenia" else "Brak punktów")
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(pois.data, key = { it.poi.id }) { item ->
                    val busy = ui.busyId == item.poi.id.toString()
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                item.poi.photoUrl?.let {
                                    AsyncImage(
                                        it,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                    )
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(item.poi.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "${item.status.label()} · ${item.submittedBy?.displayName ?: "konto usunięte"} · " +
                                            DATE_FORMAT.format(item.createdAt),
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                            item.poi.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp)) }
                            Text(
                                String.format(Locale.ROOT, "%.5f, %.5f", item.poi.latitude, item.poi.longitude),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                            item.rejectionReason?.let {
                                Text("Powód odrzucenia: $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                            ) {
                                if (item.status != PoiStatus.APPROVED) {
                                    Button(onClick = { viewModel.approve(item) }, enabled = !busy) { Text("Zatwierdź") }
                                }
                                if (item.status == PoiStatus.PENDING) {
                                    OutlinedButton(onClick = { rejecting = item }, enabled = !busy) { Text("Odrzuć") }
                                }
                                TextButton(onClick = { editing = item }, enabled = !busy) { Text("Edytuj") }
                                TextButton(onClick = { deleting = item }, enabled = !busy) {
                                    Text("Usuń", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    rejecting?.let { poi ->
        var reason by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { rejecting = null },
            title = { Text("Odrzucić „${poi.poi.name}”?") },
            text = {
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it.take(REJECT_REASON_MAX) },
                    label = { Text("Powód (opcjonalnie)") },
                    minLines = 2,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.reject(poi, reason)
                    rejecting = null
                }) { Text("Odrzuć") }
            },
            dismissButton = { TextButton(onClick = { rejecting = null }) { Text("Anuluj") } },
        )
    }
    deleting?.let { poi ->
        ConfirmDialog(
            title = "Usunąć „${poi.poi.name}”?",
            text = "Punkt zniknie z mapy i z kolekcji użytkowników, którzy go zebrali.",
            confirm = "Usuń",
            onConfirm = { viewModel.deletePoi(poi) },
            onDismiss = { deleting = null },
        )
    }
    editing?.let { poi ->
        var name by remember { mutableStateOf(poi.poi.name) }
        var description by remember { mutableStateOf(poi.poi.description.orEmpty()) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Edytuj punkt") },
            text = {
                Column {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nazwa") }, singleLine = true)
                    OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Opis") }, minLines = 3)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.updatePoi(poi, name, description)
                        editing = null
                    },
                    enabled = name.isNotBlank() && description.isNotBlank(),
                ) { Text("Zapisz") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Anuluj") } },
        )
    }
}

@Composable
private fun UsersTab(ui: AdminUiState, viewModel: AdminViewModel) {
    var blocking by remember { mutableStateOf<AdminUser?>(null) }
    var deleting by remember { mutableStateOf<AdminUser?>(null) }

    OutlinedTextField(
        value = ui.userQuery,
        onValueChange = viewModel::onUserQueryChange,
        placeholder = { Text("Szukaj po e-mailu lub nazwie") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    )
    when (val users = ui.users) {
        is UiState.Loading -> LoadingBox()
        is UiState.Error -> ErrorBox(users.message, onRetry = { viewModel.loadUsers() })
        is UiState.Success -> if (users.data.isEmpty()) {
            EmptyBox("Nie znaleziono użytkowników")
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(users.data, key = { it.profile.id }) { user ->
                    val busy = ui.busyId == user.profile.id
                    val isAdmin = user.profile.role == Role.ADMIN
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(user.profile.displayName, style = MaterialTheme.typography.titleMedium)
                                    Text(user.profile.email, style = MaterialTheme.typography.bodySmall)
                                }
                                if (isAdmin) AssistChip(onClick = {}, label = { Text("Admin") })
                                if (user.status == UserStatus.BLOCKED) {
                                    AssistChip(onClick = {}, label = { Text("Zablokowany", color = MaterialTheme.colorScheme.error) })
                                }
                            }
                            Text(
                                "Trasy: ${user.profile.stats.routeCount} · Aktywności: ${user.profile.stats.activityCount} · " +
                                    "od ${DATE_FORMAT.format(user.profile.createdAt)}",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                            // administratorów nie blokujemy ani nie usuwamy (serwer też na to nie pozwoli)
                            if (!isAdmin) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                                    if (user.status == UserStatus.BLOCKED) {
                                        OutlinedButton(onClick = { viewModel.unblock(user) }, enabled = !busy) { Text("Odblokuj") }
                                    } else {
                                        OutlinedButton(onClick = { blocking = user }, enabled = !busy) { Text("Zablokuj") }
                                    }
                                    TextButton(onClick = { deleting = user }, enabled = !busy) {
                                        Text("Usuń konto", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    blocking?.let { user ->
        var deleteContent by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { blocking = null },
            title = { Text("Zablokować ${user.profile.displayName}?") },
            text = {
                Column {
                    Text("Użytkownik nie będzie mógł korzystać z aplikacji, dopóki go nie odblokujesz.")
                    // cały wiersz zaznacza pole, nie tylko sam kwadracik
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 8.dp)
                            .toggleable(value = deleteContent, role = SemanticsRole.Checkbox, onValueChange = { deleteContent = it }),
                    ) {
                        Checkbox(checked = deleteContent, onCheckedChange = null)
                        Text("Usuń też jego trasy, opinie i zgłoszenia punktów")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.block(user, deleteContent)
                    blocking = null
                }) { Text("Zablokuj", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { blocking = null }) { Text("Anuluj") } },
        )
    }
    deleting?.let { user ->
        ConfirmDialog(
            title = "Usunąć konto ${user.profile.displayName}?",
            text = "Usuniemy konto i wszystkie dane użytkownika. Tego nie da się cofnąć.",
            confirm = "Usuń na zawsze",
            onConfirm = { viewModel.deleteUser(user) },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) { Text(confirm, color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}
