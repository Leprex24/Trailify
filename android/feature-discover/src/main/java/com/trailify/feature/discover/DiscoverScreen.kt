package com.trailify.feature.discover

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trailify.core.domain.model.ActivityType
import com.trailify.core.domain.model.RouteSort
import com.trailify.core.domain.model.RouteSummary
import com.trailify.core.ui.ErrorBox
import com.trailify.core.ui.LoadingBox
import com.trailify.core.ui.ScreenTitle
import com.trailify.core.ui.UiState
import com.trailify.core.ui.label
import org.koin.compose.viewmodel.koinViewModel
import java.util.Locale

/**
 * Zakładka Odkrywaj: wyszukiwarka, rząd kafelków z trasami i pod nim mapa ze wszystkimi trasami.
 * Uchwyt między kafelkami a mapą (przeciągnięcie albo dotknięcie) powiększa mapę kosztem kafelków.
 */
@Composable
fun DiscoverScreen(
    onRouteClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DiscoverViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var mapExpanded by rememberSaveable { mutableStateOf(false) }

    // po powrocie na zakładkę (np. po publikacji albo ocenie) odświeżamy wyniki
    LifecycleStartEffect(Unit) {
        viewModel.load()
        onStopOrDispose { }
    }

    Column(modifier = modifier.fillMaxSize()) {
        ScreenTitle("Odkrywaj")
        SearchRow(
            text = state.filters.text,
            filterCount = state.filters.filterCount,
            activityTypes = state.filters.activityTypes,
            onTextChange = viewModel::onTextChange,
            onToggleType = viewModel::toggleActivityType,
            onOpenFilters = { showFilters = true },
        )

        when (val routes = state.routes) {
            is UiState.Loading -> LoadingBox()
            is UiState.Error -> ErrorBox(message = routes.message, onRetry = viewModel::load)
            is UiState.Success -> {
                val resultsMode = state.filters.isActive
                val tiles = if (resultsMode) {
                    routes.data.map { Tile(it, isNew = false) }
                } else {
                    state.featured.map { Tile(it.route, isNew = it.reason == FeaturedReason.NEWEST) }
                }
                AnimatedVisibility(
                    visible = !mapExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    TilesSection(
                        tiles = tiles,
                        resultsMode = resultsMode,
                        resultsCount = routes.data.size,
                        onOpen = onRouteClick,
                        onShowOnMap = viewModel::showOnMap,
                        onClear = viewModel::clearAll,
                    )
                }
                ResizeHandle(expanded = mapExpanded, onChange = { mapExpanded = it })
                MapSection(
                    state = state,
                    onSelect = viewModel::onRouteSelected,
                    onOpen = onRouteClick,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
        }
    }

    if (showFilters) {
        FiltersSheet(
            filters = state.filters,
            onApply = { filters ->
                viewModel.onFiltersApply(filters)
                showFilters = false
            },
            onDismiss = { showFilters = false },
        )
    }
}

/** Trasa w rzędzie kafelków; [isNew] pokazuje plakietkę "Nowość". */
private data class Tile(val route: RouteSummary, val isNew: Boolean)

/**
 * Wyszukiwarka na całą szerokość (niskie pole) i pod nią rząd: "Filtruj" oraz szybkie filtry
 * z rodzajem aktywności, żeby przy "Filtruj" nie było pustego miejsca.
 */
@Composable
private fun SearchRow(
    text: String,
    filterCount: Int,
    activityTypes: Set<ActivityType>,
    onTextChange: (String) -> Unit,
    onToggleType: (ActivityType) -> Unit,
    onOpenFilters: () -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        SearchField(
            text = text,
            onTextChange = onTextChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            BadgedBox(badge = { if (filterCount > 0) Badge { Text("$filterCount") } }) {
                FilterChip(selected = filterCount > 0, onClick = onOpenFilters, label = { Text("Filtruj") })
            }
            ActivityType.entries.forEach { type ->
                FilterChip(
                    selected = type in activityTypes,
                    onClick = { onToggleType(type) },
                    label = { Text(type.label()) },
                )
            }
        }
    }
}

/** Niskie pole szukania (44 dp, standardowe pole tekstowe ma 56 dp i podpowiedź zawijała się do drugiej linii). */
@Composable
private fun SearchField(text: String, onTextChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val focus = LocalFocusManager.current
    BasicTextField(
        value = text,
        onValueChange = onTextChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
        modifier = modifier,
        decorationBox = { innerField ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 12.dp),
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Box(modifier = Modifier.weight(1f).padding(horizontal = 8.dp), contentAlignment = Alignment.CenterStart) {
                    if (text.isEmpty()) {
                        Text(
                            "Szukaj trasy",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    innerField()
                }
                if (text.isNotEmpty()) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Wyczyść",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp).clickable(role = Role.Button) { onTextChange("") },
                    )
                }
            }
        },
    )
}

/** Rząd kafelków: bez szukania wybór tras do odkrycia, przy szukaniu i filtrach wyniki. */
@Composable
private fun TilesSection(
    tiles: List<Tile>,
    resultsMode: Boolean,
    resultsCount: Int,
    onOpen: (Long) -> Unit,
    onShowOnMap: (RouteSummary) -> Unit,
    onClear: () -> Unit,
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
        ) {
            Text(
                if (resultsMode) "Wyniki: $resultsCount" else "Trasy do odkrycia",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            if (resultsMode) TextButton(onClick = onClear) { Text("Wyczyść") }
        }
        if (tiles.isEmpty()) {
            Text(
                if (resultsMode) "Brak tras spełniających kryteria" else "Nikt jeszcze nie udostępnił żadnej trasy.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
            )
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(tiles, key = { it.route.id }) { tile ->
                    RouteTile(
                        route = tile.route,
                        isNew = tile.isNew,
                        onClick = { onOpen(tile.route.id) },
                        onShowOnMap = { onShowOnMap(tile.route) },
                    )
                }
            }
        }
    }
}

/**
 * Uchwyt między kafelkami a mapą: przeciągnięcie w górę powiększa mapę, w dół przywraca kafelki,
 * dotknięcie przełącza. Mapa w przewijanej stronie kłóciłaby się z przewijaniem, dlatego uchwyt.
 */
@Composable
private fun ResizeHandle(expanded: Boolean, onChange: (Boolean) -> Unit) {
    var drag by remember { mutableFloatStateOf(0f) }
    val label = if (expanded) "Pokaż kafelki z trasami" else "Powiększ mapę"
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(HANDLE_AREA_HEIGHT)
            .clickable(onClickLabel = label) { onChange(!expanded) }
            .pointerInput(expanded) {
                detectVerticalDragGestures(
                    onDragStart = { drag = 0f },
                    onDragEnd = {
                        if (drag < -DRAG_THRESHOLD_PX) onChange(true) else if (drag > DRAG_THRESHOLD_PX) onChange(false)
                    },
                    onVerticalDrag = { _, delta -> drag += delta },
                )
            },
    ) {
        Box(
            Modifier
                .width(40.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
    }
}

@Composable
private fun MapSection(
    state: DiscoverUiState,
    onSelect: (Long?) -> Unit,
    onOpen: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mapRoutes = remember(state.routes, state.featured, state.filters.isActive) { state.mapRoutes }
    Box(modifier = modifier) {
        RoutesMap(
            routes = mapRoutes,
            selectedRouteId = state.selectedRouteId,
            focus = state.mapFocus,
            onSelect = onSelect,
            modifier = Modifier.fillMaxSize(),
        )
        val selected = mapRoutes.firstOrNull { it.id == state.selectedRouteId }
        if (selected != null) {
            MapRouteCard(
                route = selected,
                onClick = { onOpen(selected.id) },
                modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FiltersSheet(
    filters: DiscoverFilters,
    onApply: (DiscoverFilters) -> Unit,
    onDismiss: () -> Unit,
) {
    // zmiany w arkuszu działają dopiero po "Pokaż wyniki"
    var sort by remember { mutableStateOf(filters.sort) }
    var types by remember { mutableStateOf(filters.activityTypes) }
    var distance by remember { mutableStateOf(filters.distance) }
    var minRating by remember { mutableStateOf(filters.minRating) }
    var tourist by remember { mutableStateOf(filters.touristOnly) }
    var scenic by remember { mutableStateOf(filters.scenicOnly) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Text("Filtruj", style = MaterialTheme.typography.titleLarge)

            SheetSection("Sortowanie")
            ChipsRow {
                DISCOVER_SORTS.forEach { option ->
                    FilterChip(selected = sort == option, onClick = { sort = option }, label = { Text(option.label()) })
                }
            }

            SheetSection("Rodzaj aktywności")
            ChipsRow {
                ActivityType.entries.forEach { type ->
                    FilterChip(
                        selected = type in types,
                        onClick = { types = if (type in types) types - type else types + type },
                        label = { Text(type.label()) },
                    )
                }
            }

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
                    sort = RouteSort.RECOMMENDED
                    types = emptySet()
                    distance = DistanceRange.ANY
                    minRating = null
                    tourist = false
                    scenic = false
                }) { Text("Wyczyść") }
                Spacer(Modifier.weight(1f))
                Button(onClick = {
                    onApply(
                        filters.copy(
                            sort = sort,
                            activityTypes = types,
                            distance = distance,
                            minRating = minRating,
                            touristOnly = tourist,
                            scenicOnly = scenic,
                        ),
                    )
                }) { Text("Pokaż wyniki") }
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

private val HANDLE_AREA_HEIGHT = 32.dp
private const val DRAG_THRESHOLD_PX = 40f
