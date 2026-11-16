package com.trailify.feature.routes

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.trailify.core.domain.model.ActivityType
import com.trailify.core.domain.model.RouteSummary
import com.trailify.core.domain.route.NEAR_RADII_KM
import com.trailify.core.domain.route.RouteLength
import com.trailify.core.domain.route.RouteListFilters
import com.trailify.core.domain.route.RouteListSort
import com.trailify.core.domain.route.RouteVisibility
import com.trailify.core.domain.route.filterAndSort
import com.trailify.core.ui.ErrorBox
import com.trailify.core.ui.LoadingBox
import com.trailify.core.ui.ScreenTitle
import com.trailify.core.ui.UiState
import com.trailify.core.ui.formatDistance
import com.trailify.core.ui.formatDuration
import com.trailify.core.ui.label
import com.trailify.core.ui.swipeBetweenTabs
import org.koin.compose.viewmodel.koinViewModel
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val PL = Locale.forLanguageTag("pl-PL")
private val DATE_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy", PL).withZone(ZoneId.systemDefault())
private val STAR_COLOR = Color(0xFFF9A825)

/**
 * Zakładka Trasy: "Własne" (moje trasy publiczne i prywatne) i "Zapisane" (cudze trasy oznaczone gwiazdką).
 * Obie listy można przeszukiwać, filtrować i sortować.
 */
@Composable
fun RoutesScreen(
    onRouteClick: (Long) -> Unit,
    onAddRoute: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RoutesViewModel = koinViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var showFilters by rememberSaveable { mutableStateOf(false) }

    // po powrocie na zakładkę (np. po zapisaniu albo usunięciu trasy) odświeżamy listy
    LifecycleStartEffect(Unit) {
        viewModel.load()
        onStopOrDispose { }
    }

    // GPS włączamy tylko wtedy, gdy sortowanie albo filtr naprawdę potrzebują pozycji
    val needsLocation = ui.filters.sort == RouteListSort.NEAREST || ui.filters.nearKm != null
    DisposableEffect(needsLocation) {
        if (needsLocation) viewModel.setLocationWatching(true)
        onDispose { if (needsLocation) viewModel.setLocationWatching(false) }
    }

    Column(
        modifier = modifier.fillMaxSize().swipeBetweenTabs(ui.tab.ordinal, RoutesTab.entries.size) { viewModel.selectTab(RoutesTab.entries[it]) },
    ) {
        ScreenTitle("Trasy") {
            IconButton(onClick = onAddRoute) { Icon(Icons.Default.Add, contentDescription = "Zaplanuj nową trasę") }
        }
        PrimaryTabRow(selectedTabIndex = ui.tab.ordinal) {
            RoutesTab.entries.forEach { tab ->
                Tab(selected = ui.tab == tab, onClick = { viewModel.selectTab(tab) }, text = { Text(tab.title) })
            }
        }
        SearchField(
            text = ui.filters.text,
            onTextChange = viewModel::onTextChange,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        FilterBar(
            filters = ui.filters,
            onToggleType = viewModel::toggleActivityType,
            onSortChange = viewModel::onSortChange,
            onOpenFilters = { showFilters = true },
        )
        if (needsLocation && ui.location == null) {
            Text(
                "Czekam na lokalizację (sprawdź zgodę na lokalizację i GPS). Do tego czasu filtr odległości jest pomijany.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        when (val list = if (ui.tab == RoutesTab.MINE) ui.mine else ui.saved) {
            is UiState.Loading -> LoadingBox()
            is UiState.Error -> ErrorBox(message = list.message, onRetry = viewModel::load)
            is UiState.Success -> {
                // widoczność dotyczy tylko własnych tras; na liście zapisanych są same publiczne
                val filters = if (ui.tab == RoutesTab.MINE) ui.filters else ui.filters.copy(visibility = RouteVisibility.ALL)
                val shown = remember(list.data, filters, ui.location) { list.data.filterAndSort(filters, ui.location) }
                when {
                    list.data.isEmpty() -> EmptyList(ui.tab, onAddRoute)
                    shown.isEmpty() -> NoMatches(onClear = viewModel::clearFilters)
                    else -> LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item {
                            Text(
                                "Tras: ${shown.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        items(shown, key = { it.id }) { route ->
                            RouteCard(
                                route = route,
                                tab = ui.tab,
                                onClick = { onRouteClick(route.id) },
                                onUnsave = { viewModel.unsave(route.id) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showFilters) {
        FiltersSheet(
            filters = ui.filters,
            showVisibility = ui.tab == RoutesTab.MINE,
            onApply = { length, visibility, nearKm, tourist, scenic ->
                viewModel.onSheetFiltersApply(length, visibility, nearKm, tourist, scenic)
                showFilters = false
            },
            onDismiss = { showFilters = false },
        )
    }
}

@Composable
private fun SearchField(text: String, onTextChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = text,
        onValueChange = onTextChange,
        placeholder = { Text("Szukaj po nazwie trasy") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (text.isNotEmpty()) {
                IconButton(onClick = { onTextChange("") }) { Icon(Icons.Default.Close, contentDescription = "Wyczyść") }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun FilterBar(
    filters: RouteListFilters,
    onToggleType: (ActivityType) -> Unit,
    onSortChange: (RouteListSort) -> Unit,
    onOpenFilters: () -> Unit,
) {
    var sortMenu by remember { mutableStateOf(false) }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
    ) {
        Box {
            FilterChip(
                selected = true,
                onClick = { sortMenu = true },
                label = { Text("Sortuj: ${filters.sort.label} ▾") },
            )
            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                RouteListSort.entries.forEach { sort ->
                    DropdownMenuItem(
                        text = { Text(sort.label) },
                        onClick = {
                            sortMenu = false
                            onSortChange(sort)
                        },
                    )
                }
            }
        }
        BadgedBox(badge = { if (filters.sheetFilterCount > 0) Badge { Text("${filters.sheetFilterCount}") } }) {
            FilterChip(selected = filters.sheetFilterCount > 0, onClick = onOpenFilters, label = { Text("Filtry") })
        }
        ActivityType.entries.forEach { type ->
            FilterChip(
                selected = type in filters.activityTypes,
                onClick = { onToggleType(type) },
                label = { Text(type.label()) },
            )
        }
    }
}

@Composable
private fun RouteCard(route: RouteSummary, tab: RoutesTab, onClick: () -> Unit, onUnsave: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RouteThumbnail(route.thumbnailUrl)
            Column(modifier = Modifier.weight(1f)) {
                Text(route.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${route.activityType.label()} · ${formatDistance(route.totalDistance)} · ${formatDuration(route.durationSeconds)}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 2.dp),
                )
                if (tab == RoutesTab.MINE) {
                    Row(modifier = Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (route.isPublic) "Publiczna" else "Prywatna",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (route.isPublic) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            " · ${DATE_FORMAT.format(route.createdAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Text(
                        "Autor: ${route.owner.displayName} · ${ratingText(route)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            if (tab == RoutesTab.SAVED) {
                IconButton(onClick = onUnsave) {
                    Icon(Icons.Default.Star, contentDescription = "Usuń z zapisanych", tint = STAR_COLOR)
                }
            }
        }
    }
}

private fun ratingText(route: RouteSummary): String {
    val average = route.averageRating
    return if (average == null || route.reviewCount == 0) {
        "brak ocen"
    } else {
        "★ ${String.format(PL, "%.1f", average)} (${route.reviewCount})"
    }
}

/** Zdjęcie trasy z internetu albo szare pole z ikoną, gdy trasa nie ma zdjęć. */
@Composable
private fun RouteThumbnail(url: String?) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = "Zdjęcie trasy",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(Icons.Default.Place, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun EmptyList(tab: RoutesTab, onAddRoute: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (tab == RoutesTab.MINE) {
            EmptyMessage("Nie masz jeszcze własnych tras. Nagraj trasę albo zaplanuj ją na mapie.")
            Button(onClick = onAddRoute, modifier = Modifier.padding(top = 16.dp)) { Text("Zaplanuj trasę") }
        } else {
            EmptyMessage("Nie zapisałeś jeszcze żadnych tras. W zakładce Odkrywaj otwórz trasę i dotknij „Zapisz”.")
        }
    }
}

@Composable
private fun NoMatches(onClear: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EmptyMessage("Brak tras spełniających kryteria")
        OutlinedButton(onClick = onClear, modifier = Modifier.padding(top = 16.dp)) { Text("Wyczyść wyszukiwanie i filtry") }
    }
}

/** Komunikat o pustej liście; wysokość wynika z tekstu, więc nic się nie ucina. */
@Composable
private fun EmptyMessage(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FiltersSheet(
    filters: RouteListFilters,
    showVisibility: Boolean,
    onApply: (RouteLength, RouteVisibility, Int?, Boolean, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    // zmiany w arkuszu działają dopiero po "Pokaż wyniki"
    var length by remember { mutableStateOf(filters.length) }
    var visibility by remember { mutableStateOf(filters.visibility) }
    var nearKm by remember { mutableStateOf(filters.nearKm) }
    var tourist by remember { mutableStateOf(filters.touristOnly) }
    var scenic by remember { mutableStateOf(filters.scenicOnly) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text("Filtry", style = MaterialTheme.typography.titleLarge)

            SheetSection("Długość trasy")
            ChipsRow {
                RouteLength.entries.forEach { range ->
                    FilterChip(selected = length == range, onClick = { length = range }, label = { Text(range.label) })
                }
            }

            if (showVisibility) {
                SheetSection("Widoczność")
                ChipsRow {
                    RouteVisibility.entries.forEach { option ->
                        FilterChip(selected = visibility == option, onClick = { visibility = option }, label = { Text(option.label) })
                    }
                }
            }

            SheetSection("Start trasy blisko mnie")
            ChipsRow {
                NEAR_RADII_KM.forEach { km ->
                    FilterChip(
                        selected = nearKm == km,
                        onClick = { nearKm = km },
                        label = { Text(if (km == null) "Dowolnie" else "do $km km") },
                    )
                }
            }

            SheetSection("Charakter trasy")
            SwitchLine("Tylko turystyczne", tourist) { tourist = it }
            SwitchLine("Tylko widokowe", scenic) { scenic = it }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    length = RouteLength.ANY
                    visibility = RouteVisibility.ALL
                    nearKm = null
                    tourist = false
                    scenic = false
                }) { Text("Wyczyść") }
                Spacer(Modifier.weight(1f))
                Button(onClick = { onApply(length, visibility, nearKm, tourist, scenic) }) { Text("Pokaż wyniki") }
            }
        }
    }
}

@Composable
private fun SheetSection(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
}

@Composable
private fun ChipsRow(content: @Composable () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) { content() }
}

@Composable
private fun SwitchLine(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(title, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
