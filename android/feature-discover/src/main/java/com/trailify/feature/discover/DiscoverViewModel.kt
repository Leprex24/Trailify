package com.trailify.feature.discover

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trailify.core.domain.model.ActivityType
import com.trailify.core.domain.model.RouteSort
import com.trailify.core.domain.model.RouteSummary
import com.trailify.core.domain.repository.RouteRepository
import com.trailify.core.ui.UiState
import com.trailify.core.ui.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DiscoverView { LIST, MAP }

data class DiscoverUiState(
    val filters: DiscoverFilters = DiscoverFilters(),
    val view: DiscoverView = DiscoverView.LIST,
    val routes: UiState<List<RouteSummary>> = UiState.Loading,
    /** Trasa zaznaczona na mapie (karta na dole mapy). */
    val selectedRouteId: Long? = null,
)

/**
 * Odkrywaj (W2-W4): wyszukiwanie opublikowanych tras z filtrami i sortowaniem, widok listy i mapy.
 * Każda zmiana filtra pobiera wyniki od nowa. Wpisywany tekst czeka chwilę ([SEARCH_DEBOUNCE_MS]),
 * żeby nie wysyłać zapytania po każdej literze.
 *
 * Ten sam ViewModel jest WZORCEM dla pozostałych ekranów: dostaje interfejs repozytorium
 * (nie wie, czy to atrapa, czy prawdziwe API) i wystawia stan jako StateFlow.
 */
class DiscoverViewModel(private val routes: RouteRepository) : ViewModel() {

    private val _state = MutableStateFlow(DiscoverUiState())
    val state: StateFlow<DiscoverUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    /** Pobiera trasy dla aktualnych filtrów. Wywoływane też po powrocie na ekran (np. po publikacji). */
    fun load() = reload(debounce = false)

    fun onTextChange(text: String) {
        updateFilters { it.copy(text = text) }
        reload(debounce = true)
    }

    fun toggleActivityType(type: ActivityType) = applyFilters {
        it.copy(activityTypes = if (type in it.activityTypes) it.activityTypes - type else it.activityTypes + type)
    }

    fun onSortChange(sort: RouteSort) = applyFilters { it.copy(sort = sort) }

    /** Zatwierdzenie arkusza "Filtry". */
    fun onSheetFiltersApply(distance: DistanceRange, minRating: Double?, touristOnly: Boolean, scenicOnly: Boolean) =
        applyFilters {
            it.copy(distance = distance, minRating = minRating, touristOnly = touristOnly, scenicOnly = scenicOnly)
        }

    /** Czyści filtry, zostawia wpisany tekst i sortowanie. */
    fun clearFilters() = applyFilters { DiscoverFilters(text = it.text, sort = it.sort) }

    fun onViewChange(view: DiscoverView) = _state.update { it.copy(view = view, selectedRouteId = null) }

    fun onRouteSelected(routeId: Long?) = _state.update { it.copy(selectedRouteId = routeId) }

    private fun applyFilters(change: (DiscoverFilters) -> DiscoverFilters) {
        updateFilters(change)
        reload(debounce = false)
    }

    private fun updateFilters(change: (DiscoverFilters) -> DiscoverFilters) =
        _state.update { it.copy(filters = change(it.filters)) }

    private fun reload(debounce: Boolean) {
        loadJob?.cancel() // starsze zapytanie nie może nadpisać wyników nowszego
        loadJob = viewModelScope.launch {
            if (debounce) delay(SEARCH_DEBOUNCE_MS)
            // przy odświeżaniu zostawiamy stare wyniki na ekranie, kółko tylko przy pierwszym ładowaniu
            if (_state.value.routes !is UiState.Success) _state.update { it.copy(routes = UiState.Loading) }
            val result = try {
                UiState.Success(routes.search(_state.value.filters.toQuery()).items)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Wyszukiwanie tras nie powiodło się", e)
                UiState.Error(e.toUserMessage())
            }
            _state.update { s ->
                // zaznaczenie na mapie znika, gdy trasa wypadła z wyników
                val selected = s.selectedRouteId?.takeIf { id ->
                    (result as? UiState.Success)?.data?.any { it.id == id } == true
                }
                s.copy(routes = result, selectedRouteId = selected)
            }
        }
    }

    internal companion object {
        const val TAG = "Discover"
        const val SEARCH_DEBOUNCE_MS = 400L
    }
}
