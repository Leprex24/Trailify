package com.trailify.feature.discover

import com.trailify.core.domain.model.ActivityType
import com.trailify.core.domain.model.LatLng
import com.trailify.core.domain.model.RouteSort
import com.trailify.core.ui.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoverViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val routes = FakeRouteRepository()
    private val lublin = LatLng(51.25, 22.57)

    private fun viewModel(position: LatLng? = null) = DiscoverViewModel(routes, FakeUserLocationSource(position), seed = 1L)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun startPobieraTrasyNaMapeIKafelki() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(4, routes.queries.size) // mapa i trzy zapytania o kafelki
        assertEquals(RouteSort.RECOMMENDED, routes.queries.first().sort)
        assertEquals(DiscoverFilters.PAGE_SIZE, routes.queries.first().size)
        assertEquals(
            setOf(RouteSort.NEWEST, RouteSort.RATING, RouteSort.RECOMMENDED),
            routes.queries.drop(1).map { it.sort }.toSet(),
        )
        assertEquals(2, (vm.state.value.routes as UiState.Success).data.size)
        assertEquals(2, vm.state.value.featured.size)
    }

    @Test
    fun kafelkiZOkolicyGdyZnamyPozycje() = runTest(dispatcher) {
        viewModel(lublin)
        advanceUntilIdle()
        assertNull(routes.queries.first().center) // mapa pokazuje wszystkie trasy
        routes.queries.drop(1).forEach {
            assertEquals(lublin, it.center)
            assertEquals(DiscoverViewModel.FEATURED_RADIUS_KM, it.radiusKm!!, 0.0)
        }
    }

    @Test
    fun kafelkiZCalejBazyGdyWOkoliceNicNiema() = runTest(dispatcher) {
        routes.handler = { query -> if (query.center != null) emptyList() else routes.result }
        val vm = viewModel(lublin)
        advanceUntilIdle()
        assertEquals(7, routes.queries.size) // mapa, 3 zapytania z okolicy, 3 bez okolicy
        assertNull(routes.queries.last().center)
        assertTrue(vm.state.value.featured.isNotEmpty())
    }

    @Test
    fun wpisywanieTekstuWysylaJednoZapytanie() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        val before = routes.queries.size
        vm.onTextChange("Z")
        vm.onTextChange("Za")
        vm.onTextChange("Zalew")
        advanceTimeBy(DiscoverViewModel.SEARCH_DEBOUNCE_MS - 1)
        assertEquals(before, routes.queries.size) // jeszcze czekamy, aż użytkownik skończy pisać
        advanceUntilIdle()
        assertEquals(before + 1, routes.queries.size) // przy szukaniu tylko mapa i wyniki, bez kafelków
        assertEquals("Zalew", routes.queries.last().text)
    }

    @Test
    fun filtryTrafiajaDoZapytania() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onFiltersApply(
            DiscoverFilters(
                activityTypes = setOf(ActivityType.CYCLING),
                sort = RouteSort.RATING,
                distance = DistanceRange.SHORT,
                minRating = 4.0,
                touristOnly = true,
            ),
        )
        advanceUntilIdle()
        val query = routes.queries.last()
        assertEquals(setOf(ActivityType.CYCLING), query.activityTypes)
        assertEquals(RouteSort.RATING, query.sort)
        assertEquals(5_000.0, query.maxDistance!!, 0.0)
        assertEquals(4.0, query.minRating!!, 0.0)
        assertEquals(true, query.isTourist)
    }

    @Test
    fun szybkiFiltrPrzelaczaTypAktywnosci() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.toggleActivityType(ActivityType.CYCLING)
        advanceUntilIdle()
        assertEquals(setOf(ActivityType.CYCLING), routes.queries.last().activityTypes)
        vm.toggleActivityType(ActivityType.CYCLING)
        advanceUntilIdle()
        assertTrue(routes.queries.last().activityTypes.isEmpty())
    }

    @Test
    fun zatwierdzenieArkuszaZostawiaWpisanyTekst() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onTextChange("Park")
        vm.onFiltersApply(DiscoverFilters(sort = RouteSort.NEWEST, activityTypes = setOf(ActivityType.RUNNING)))
        advanceUntilIdle()
        assertEquals("Park", vm.state.value.filters.text)
        assertEquals(RouteSort.NEWEST, vm.state.value.filters.sort)
    }

    @Test
    fun czyszczenieUsuwaTekstFiltrySortowanie() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onTextChange("Park")
        vm.onFiltersApply(DiscoverFilters(sort = RouteSort.NEWEST, activityTypes = setOf(ActivityType.RUNNING)))
        vm.clearAll()
        advanceUntilIdle()
        assertEquals(DiscoverFilters(), vm.state.value.filters)
    }

    @Test
    fun zaznaczenieZnikaGdyTrasaWypadlaZWynikow() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onRouteSelected(2L)
        routes.result = listOf(summary(1))
        vm.load()
        advanceUntilIdle()
        assertNull(vm.state.value.selectedRouteId)
    }

    @Test
    fun zobaczNaMapieZaznaczaTraseIPrzesuwaMape() = runTest(dispatcher) {
        val route = summary(1).copy(startPoint = lublin)
        routes.result = listOf(route)
        val vm = viewModel()
        advanceUntilIdle()
        vm.showOnMap(route)
        assertEquals(1L, vm.state.value.selectedRouteId)
        assertEquals(lublin, vm.state.value.mapFocus!!.position)
        val firstToken = vm.state.value.mapFocus!!.token
        vm.showOnMap(route)
        assertTrue(vm.state.value.mapFocus!!.token > firstToken) // ta sama trasa drugi raz też przesuwa mapę
    }

    @Test
    fun zobaczNaMapieBezStartuNicNieRobi() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.showOnMap(summary(1)) // brak punktu startowego
        assertNull(vm.state.value.selectedRouteId)
        assertNull(vm.state.value.mapFocus)
    }

    @Test
    fun kafelekSpozaPierwszejStronyMaPunktNaMapie() = runTest(dispatcher) {
        // najnowsza trasa nie załapała się na listę "polecane", ale ma być na mapie i dać się zaznaczyć
        val extra = summary(9).copy(startPoint = lublin)
        routes.handler = { query -> if (query.sort == RouteSort.NEWEST) listOf(extra) else routes.result }
        val vm = viewModel()
        advanceUntilIdle()
        assertTrue(vm.state.value.mapRoutes.any { it.id == 9L })
        vm.showOnMap(extra)
        advanceUntilIdle()
        vm.load()
        advanceUntilIdle()
        assertEquals(9L, vm.state.value.selectedRouteId)
    }
}
