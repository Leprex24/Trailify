package com.trailify.feature.discover

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trailify.core.domain.model.ActivityType
import com.trailify.core.domain.model.RouteSort
import com.trailify.core.domain.model.RouteSummary
import com.trailify.core.ui.EmptyBox
import com.trailify.core.ui.ErrorBox
import com.trailify.core.ui.LoadingBox
import com.trailify.core.ui.UiState
import com.trailify.core.ui.label
import org.koin.compose.viewmodel.koinViewModel
import java.util.Locale

/** Zakładka Odkrywaj (W2-W4): wyszukiwanie, filtry, sortowanie, lista i mapa opublikowanych tras. */
@Composable
fun DiscoverScreen(
    onRouteClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DiscoverViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showFilters by rememberSaveable { mutableStateOf(false) }

    // po powrocie na zakładkę (np. po publikacji albo ocenie) odświeżamy wyniki
    LifecycleStartEffect(Unit) {
        viewModel.load()
        onStopOrDispose { }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = "Odkrywaj",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
        )
        SearchField(
            text = state.filters.text,
            onTextChange = viewModel::onTextChange,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        FilterBar(
            filters = state.filters,
            onToggleType = viewModel::toggleActivityType,
            onSortChange = viewModel::onSortChange,
            onOpenFilters = { showFilters = true },
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            DiscoverView.entries.forEachIndexed { index, view ->
                SegmentedButton(
                    selected = state.view == view,
                    onClick = { viewModel.onViewChange(view) },
                    shape = SegmentedButtonDefaults.itemShape(index, DiscoverView.entries.size),
                ) { Text(if (view == DiscoverView.LIST) "Lista" else "Mapa") }
            }
        }

        when (val routes = state.routes) {
            is UiState.Loading -> LoadingBox()
            is UiState.Error -> ErrorBox(message = routes.message, onRetry = viewModel::load)
            is UiState.Success -> when {
                routes.data.isEmpty() -> EmptyResults(
                    hasFilters = state.filters != DiscoverFilters(sort = state.filters.sort),
                    onClear = {
                        viewModel.onTextChange("")
                        viewModel.clearFilters()
                    },
                )
                state.view == DiscoverView.LIST -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Text(
                            "Znaleziono: ${routes.data.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(routes.data, key = { it.id }) { route ->
                        DiscoverRouteCard(route = route, onClick = { onRouteClick(route.id) })
                    }
                }
                else -> MapResults(
                    routes = routes.data,
                    selectedRouteId = state.selectedRouteId,
                    onSelect = viewModel::onRouteSelected,
                    onOpen = onRouteClick,
                )
            }
        }
    }

    if (showFilters) {
        FiltersSheet(
            filters = state.filters,
            onApply = { distance, rating, tourist, scenic ->
                viewModel.onSheetFiltersApply(distance, rating, tourist, scenic)
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
    filters: DiscoverFilters,
    onToggleType: (ActivityType) -> Unit,
    onSortChange: (RouteSort) -> Unit,
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
                label = { Text("Sortuj: ${filters.sort.label()} ▾") },
            )
            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                DISCOVER_SORTS.forEach { sort ->
                    DropdownMenuItem(
                        text = { Text(sort.label()) },
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
private fun EmptyResults(hasFilters: Boolean, onClear: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EmptyBox("Brak tras spełniających kryteria", modifier = Modifier.height(80.dp))
        if (hasFilters) OutlinedButton(onClick = onClear) { Text("Wyczyść wyszukiwanie i filtry") }
    }
}

@Composable
private fun MapResults(
    routes: List<RouteSummary>,
    selectedRouteId: Long?,
    onSelect: (Long?) -> Unit,
    onOpen: (Long) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        RoutesMap(routes = routes, selectedRouteId = selectedRouteId, onSelect = onSelect, modifier = Modifier.fillMaxSize())
        val selected = routes.firstOrNull { it.id == selectedRouteId }
        if (selected != null) {
            DiscoverRouteCard(
                route = selected,
                onClick = { onOpen(selected.id) },
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
            )
        } else {
            Text(
                "Dotknij punktu, żeby zobaczyć trasę",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(8.dp)
                    .padding(horizontal = 8.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FiltersSheet(
    filters: DiscoverFilters,
    onApply: (DistanceRange, Double?, Boolean, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    // zmiany w arkuszu działają dopiero po "Pokaż wyniki"
    var distance by remember { mutableStateOf(filters.distance) }
    var minRating by remember { mutableStateOf(filters.minRating) }
    var tourist by remember { mutableStateOf(filters.touristOnly) }
    var scenic by remember { mutableStateOf(filters.scenicOnly) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text("Filtry", style = MaterialTheme.typography.titleLarge)

            SheetSection("Długość trasy")
            ChipsRow {
                DistanceRange.entries.forEach { range ->
                    FilterChip(selected = distance == range, onClick = { distance = range }, label = { Text(range.label) })
                }
            }

            SheetSection("Minimalna ocena")
            ChipsRow {
                MIN_RATING_OPTIONS.forEach { rating ->
                    FilterChip(
                        selected = minRating == rating,
                        onClick = { minRating = rating },
                        label = { Text(rating?.let { "★ ${String.format(Locale.forLanguageTag("pl-PL"), "%.1f", it)}+" } ?: "Dowolna") },
                    )
                }
            }

            SheetSection("Charakter trasy")
            SwitchLine("Tylko turystyczne", tourist) { tourist = it }
            SwitchLine("Tylko widokowe", scenic) { scenic = it }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    distance = DistanceRange.ANY
                    minRating = null
                    tourist = false
                    scenic = false
                }) { Text("Wyczyść") }
                Spacer(Modifier.weight(1f))
                Button(onClick = { onApply(distance, minRating, tourist, scenic) }) { Text("Pokaż wyniki") }
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
