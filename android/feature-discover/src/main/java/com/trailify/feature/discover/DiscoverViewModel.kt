package com.trailify.feature.discover

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trailify.core.domain.model.ActivityType
import com.trailify.core.domain.model.LatLng
import com.trailify.core.domain.model.RouteQuery
import com.trailify.core.domain.model.RouteSort
import com.trailify.core.domain.model.RouteSummary
import com.trailify.core.domain.repository.RouteRepository
import com.trailify.core.domain.repository.UserLocationSource
import com.trailify.core.ui.MapFocus
import com.trailify.core.ui.UiState
import com.trailify.core.ui.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

data class DiscoverUiState(
    val filters: DiscoverFilters = DiscoverFilters(),
    /** Wszystkie trasy pasujące do filtrów: punkty na mapie, a przy szukaniu też wyniki w rzędzie kafelków. */
    val routes: UiState<List<RouteSummary>> = UiState.Loading,
    /** Kafelki widoczne, gdy użytkownik niczego nie szuka (najnowsze, najlepsze, losowe). */
    val featured: List<FeaturedRoute> = emptyList(),
    /** Trasa zaznaczona na mapie (karta na dole mapy). */
    val selectedRouteId: Long? = null,
    /** Ostatnia prośba "Zobacz na mapie" (mapa przesuwa kamerę do startu trasy). */
    val mapFocus: MapFocus? = null,
) {
    /** Trasy, które mają punkt na mapie. Kafelki spoza pierwszej strony wyników też muszą tu być. */
    val mapRoutes: List<RouteSummary>
        get() {
            val found = (routes as? UiState.Success)?.data.orEmpty()
            return if (filters.isActive) found else (found + featured.map { it.route }).distinctBy { it.id }
        }
}

/**
 * Odkrywaj: u góry kafelki z trasami, pod nimi mapa ze wszystkimi trasami pasującymi do filtrów.
 * Bez szukania kafelki to mieszanka najnowszych, najwyżej ocenianych, polecanych i losowych tras (z okolicy użytkownika,
 * jeśli znamy jego pozycję), a przy szukaniu i filtrach te same miejsca zajmują wyniki.
 * Każda zmiana filtra pobiera wyniki od nowa. Wpisywany tekst czeka chwilę ([SEARCH_DEBOUNCE_MS]),
 * żeby nie wysyłać zapytania po każdej literze.
 *
 * Ten sam ViewModel jest WZORCEM dla pozostałych ekranów: dostaje interfejsy repozytoriów
 * (nie wie, czy to atrapa, czy prawdziwe API) i wystawia stan jako StateFlow.
 */
class DiscoverViewModel(
    private val routes: RouteRepository,
    private val location: UserLocationSource,
    /** Ziarno losowania kafelków: to samo przez życie ekranu, więc kafelki nie zmieniają się po każdym powrocie. */
    private val seed: Long = Random.nextLong(),
) : ViewModel() {

    private val _state = MutableStateFlow(DiscoverUiState())
    val state: StateFlow<DiscoverUiState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var focusToken = 0

    init {
        load()
    }

    /** Pobiera trasy dla aktualnych filtrów. Wywoływane też po powrocie na ekran (np. po publikacji). */
    fun load() = reload(debounce = false)

    fun onTextChange(text: String) {
        updateFilters { it.copy(text = text) }
        reload(debounce = true)
    }

    /** Zatwierdzenie arkusza "Filtruj": ustawienia z arkusza zastępują stare, wpisany tekst zostaje. */
    fun toggleActivityType(type: ActivityType) = applyFilters {
        it.copy(activityTypes = if (type in it.activityTypes) it.activityTypes - type else it.activityTypes + type)
    }

    fun onFiltersApply(filters: DiscoverFilters) = applyFilters { filters.copy(text = it.text) }

    /** Czyści wszystko: tekst, filtry i sortowanie. */
    fun clearAll() = applyFilters { DiscoverFilters() }

    fun onRouteSelected(routeId: Long?) = _state.update { it.copy(selectedRouteId = routeId) }

    /** "Zobacz na mapie": zaznacza trasę i przesuwa mapę do jej startu. */
    fun showOnMap(route: RouteSummary) {
        val start = route.startPoint ?: return
        _state.update { it.copy(selectedRouteId = route.id, mapFocus = MapFocus(start, ++focusToken)) }
    }

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
            val filters = _state.value.filters
            // przy odświeżaniu zostawiamy stare wyniki na ekranie, kółko tylko przy pierwszym ładowaniu
            if (_state.value.routes !is UiState.Success) _state.update { it.copy(routes = UiState.Loading) }
            val result = try {
                UiState.Success(routes.search(filters.toQuery()).items)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Wyszukiwanie tras nie powiodło się", e)
                UiState.Error(e.toUserMessage())
            }
            _state.update { it.copy(routes = result) }
            if (result is UiState.Success && !filters.isActive) {
                val featured = loadFeatured(pool = result.data)
                _state.update { it.copy(featured = featured) }
            }
            // zaznaczenie na mapie znika, gdy trasa wypadła z mapy
            _state.update { s ->
                s.copy(selectedRouteId = s.selectedRouteId?.takeIf { id -> s.mapRoutes.any { it.id == id } })
            }
        }
    }

    /**
     * Najnowsze, najwyżej oceniane i polecane trasy z okolicy ([FEATURED_RADIUS_KM] od użytkownika) plus losowe z [pool].
     * Gdy nie znamy pozycji albo w okolicy nic nie ma, bierzemy trasy z całej bazy. Pozycji tu nie włączamy
     * (to robią mapa START i Społeczność), więc ekran nie pyta o zgodę ani nie zużywa baterii.
     */
    private suspend fun loadFeatured(pool: List<RouteSummary>): List<FeaturedRoute> {
        val center = location.location.value
        var sources = fetchFeaturedSources(center)
        if (center != null && sources.isEmpty) sources = fetchFeaturedSources(null)
        return pickFeatured(sources.newest, sources.bestRated, sources.recommended, pool, Random(seed))
    }

    private class FeaturedSources(
        val newest: List<RouteSummary>,
        val bestRated: List<RouteSummary>,
        val recommended: List<RouteSummary>,
    ) {
        val isEmpty: Boolean get() = newest.isEmpty() && bestRated.isEmpty() && recommended.isEmpty()
    }

    private suspend fun fetchFeaturedSources(center: LatLng?): FeaturedSources = coroutineScope {
        val newest = async { searchOrEmpty(featuredQuery(center, RouteSort.NEWEST)) }
        val best = async { searchOrEmpty(featuredQuery(center, RouteSort.RATING)) }
        val recommended = async { searchOrEmpty(featuredQuery(center, RouteSort.RECOMMENDED)) }
        FeaturedSources(newest.await(), best.await(), recommended.await())
    }

    private fun featuredQuery(center: LatLng?, sort: RouteSort) = RouteQuery(
        center = center,
        radiusKm = FEATURED_RADIUS_KM.takeIf { center != null },
        sort = sort,
        size = FEATURED_FETCH_SIZE,
    )

    /** Kafelki są dodatkiem: gdy zapytanie się nie uda, mapa i losowe trasy nadal działają. */
    private suspend fun searchOrEmpty(query: RouteQuery): List<RouteSummary> = try {
        routes.search(query).items
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Pobranie kafelków nie powiodło się", e)
        emptyList()
    }

    internal companion object {
        const val TAG = "Discover"
        const val SEARCH_DEBOUNCE_MS = 400L
        const val FEATURED_RADIUS_KM = 50.0

        /** Zapas ponad liczbę kafelków w grupie, bo część tras może się powtórzyć między grupami. */
        const val FEATURED_FETCH_SIZE = 8
    }
}
