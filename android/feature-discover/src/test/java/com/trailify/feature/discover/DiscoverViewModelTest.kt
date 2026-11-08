package com.trailify.feature.discover

import com.trailify.core.domain.model.ActivityType
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

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun startPobieraPolecaneTrasy() = runTest(dispatcher) {
        val vm = DiscoverViewModel(routes)
        advanceUntilIdle()
        assertEquals(RouteSort.RECOMMENDED, routes.queries.single().sort)
        assertEquals(2, (vm.state.value.routes as UiState.Success).data.size)
    }

    @Test
    fun wpisywanieTekstuWysylaJednoZapytanie() = runTest(dispatcher) {
        val vm = DiscoverViewModel(routes)
        advanceUntilIdle()
        vm.onTextChange("Z")
        vm.onTextChange("Za")
        vm.onTextChange("Zalew")
        advanceTimeBy(DiscoverViewModel.SEARCH_DEBOUNCE_MS - 1)
        assertEquals(1, routes.queries.size) // jeszcze czekamy, aż użytkownik skończy pisać
        advanceUntilIdle()
        assertEquals(2, routes.queries.size)
        assertEquals("Zalew", routes.queries.last().text)
    }

    @Test
    fun filtryTrafiajaDoZapytania() = runTest(dispatcher) {
        val vm = DiscoverViewModel(routes)
        vm.toggleActivityType(ActivityType.CYCLING)
        vm.onSortChange(RouteSort.RATING)
        vm.onSheetFiltersApply(DistanceRange.SHORT, minRating = 4.0, touristOnly = true, scenicOnly = false)
        advanceUntilIdle()
        val query = routes.queries.last()
        assertEquals(setOf(ActivityType.CYCLING), query.activityTypes)
        assertEquals(RouteSort.RATING, query.sort)
        assertEquals(5_000.0, query.maxDistance!!, 0.0)
        assertEquals(4.0, query.minRating!!, 0.0)
        assertEquals(true, query.isTourist)
    }

    @Test
    fun czyszczenieFiltrowZostawiaTekstISortowanie() = runTest(dispatcher) {
        val vm = DiscoverViewModel(routes)
        vm.onTextChange("Park")
        vm.onSortChange(RouteSort.NEWEST)
        vm.toggleActivityType(ActivityType.RUNNING)
        vm.clearFilters()
        advanceUntilIdle()
        val filters = vm.state.value.filters
        assertEquals("Park", filters.text)
        assertEquals(RouteSort.NEWEST, filters.sort)
        assertTrue(filters.activityTypes.isEmpty())
    }

    @Test
    fun zaznaczenieZnikaGdyTrasaWypadlaZWynikow() = runTest(dispatcher) {
        val vm = DiscoverViewModel(routes)
        advanceUntilIdle()
        vm.onRouteSelected(2L)
        routes.result = listOf(summary(1))
        vm.load()
        advanceUntilIdle()
        assertNull(vm.state.value.selectedRouteId)
    }
}
