package com.trailify.feature.routes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trailify.core.domain.model.ActivityType
import com.trailify.core.domain.model.LatLng
import com.trailify.core.domain.model.Page
import com.trailify.core.domain.model.RouteSummary
import com.trailify.core.domain.repository.RouteRepository
import com.trailify.core.domain.repository.SavedRouteRepository
import com.trailify.core.domain.repository.UserLocationSource
import com.trailify.core.domain.route.RouteLength
import com.trailify.core.domain.route.RouteListFilters
import com.trailify.core.domain.route.RouteListSort
import com.trailify.core.domain.route.RouteVisibility
import com.trailify.core.ui.UiState
import com.trailify.core.ui.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class RoutesTab(val title: String) {
    MINE("Własne"),
    SAVED("Zapisane"),
}

data class RoutesUi(
    val tab: RoutesTab = RoutesTab.MINE,
    val mine: UiState<List<RouteSummary>> = UiState.Loading,
    val saved: UiState<List<RouteSummary>> = UiState.Loading,
    /** Wyszukiwanie, filtry i sortowanie są wspólne dla obu zakładek. */
    val filters: RouteListFilters = RouteListFilters(),
    val location: LatLng? = null,
)

/**
 * Ekran Trasy: własne trasy (publiczne i prywatne) oraz trasy zapisane gwiazdką.
 * Listy pobieramy w całości (kilka stron po 100), a wyszukiwanie, filtry i sortowanie liczymy na telefonie.
 */
class RoutesViewModel(
    private val routes: RouteRepository,
    private val saved: SavedRouteRepository,
    private val locationSource: UserLocationSource,
) : ViewModel() {

    private val _ui = MutableStateFlow(RoutesUi())
    val ui: StateFlow<RoutesUi> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            locationSource.location.collect { location -> _ui.update { it.copy(location = location) } }
        }
    }

    /** Pobiera obie listy od nowa (przy wejściu na ekran). Przy błędzie odświeżania zostają poprzednie dane. */
    fun load() {
        viewModelScope.launch {
            val result = fetchAll { page -> routes.getMine(page, PAGE_SIZE) }
            _ui.update { it.copy(mine = merge(it.mine, result)) }
        }
        viewModelScope.launch {
            val result = fetchAll { page -> saved.getSaved(page, PAGE_SIZE) }
            _ui.update { it.copy(saved = merge(it.saved, result)) }
        }
    }

    /** Odznacza trasę na liście zapisanych: znika od razu, a przy błędzie lista wraca z serwera. */
    fun unsave(routeId: Long) {
        _ui.update { state ->
            val current = state.saved
            if (current is UiState.Success) state.copy(saved = UiState.Success(current.data.filter { it.id != routeId })) else state
        }
        viewModelScope.launch {
            try {
                saved.setSaved(routeId, false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val result = fetchAll { page -> saved.getSaved(page, PAGE_SIZE) }
                _ui.update { it.copy(saved = merge(it.saved, result)) }
            }
        }
    }

    fun setLocationWatching(watching: Boolean) = locationSource.setWatching(watching)

    fun selectTab(tab: RoutesTab) = _ui.update { it.copy(tab = tab) }

    fun onTextChange(text: String) = updateFilters { it.copy(text = text.take(MAX_QUERY_LENGTH)) }

    fun toggleActivityType(type: ActivityType) = updateFilters {
        it.copy(activityTypes = if (type in it.activityTypes) it.activityTypes - type else it.activityTypes + type)
    }

    fun onSortChange(sort: RouteListSort) = updateFilters { it.copy(sort = sort) }

    fun onSheetFiltersApply(length: RouteLength, visibility: RouteVisibility, nearKm: Int?, tourist: Boolean, scenic: Boolean) =
        updateFilters {
            it.copy(length = length, visibility = visibility, nearKm = nearKm, touristOnly = tourist, scenicOnly = scenic)
        }

    /** Czyści tekst i filtry, zostawia wybrane sortowanie. */
    fun clearFilters() = updateFilters { RouteListFilters(sort = it.sort) }

    private fun updateFilters(change: (RouteListFilters) -> RouteListFilters) {
        _ui.update { it.copy(filters = change(it.filters)) }
    }

    private fun merge(old: UiState<List<RouteSummary>>, result: Result<List<RouteSummary>>): UiState<List<RouteSummary>> =
        result.fold(
            onSuccess = { UiState.Success(it) },
            onFailure = { if (old is UiState.Success) old else UiState.Error(it.toUserMessage()) },
        )

    private suspend fun fetchAll(fetch: suspend (Int) -> Page<RouteSummary>): Result<List<RouteSummary>> = try {
        val all = ArrayList<RouteSummary>()
        var page = 0
        while (page < MAX_PAGES) {
            val result = fetch(page)
            all += result.items
            page++
            if (page >= result.totalPages) break
        }
        Result.success(all)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private companion object {
        const val PAGE_SIZE = 100
        const val MAX_PAGES = 5
        const val MAX_QUERY_LENGTH = 100
    }
}
