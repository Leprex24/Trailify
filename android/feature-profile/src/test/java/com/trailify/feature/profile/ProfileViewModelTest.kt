package com.trailify.feature.profile

import com.trailify.core.domain.model.Activity
import com.trailify.core.domain.model.ActivityDraft
import com.trailify.core.domain.model.ActivitySummary
import com.trailify.core.domain.model.ActivityType
import com.trailify.core.domain.model.AppException
import com.trailify.core.domain.model.BoundingBox
import com.trailify.core.domain.model.LatLng
import com.trailify.core.domain.model.Page
import com.trailify.core.domain.model.Poi
import com.trailify.core.domain.model.PoiAdmin
import com.trailify.core.domain.model.PoiCollectionEntry
import com.trailify.core.domain.model.PoiDraft
import com.trailify.core.domain.model.Profile
import com.trailify.core.domain.model.ProfileStats
import com.trailify.core.domain.model.Role
import com.trailify.core.domain.repository.ActivityRepository
import com.trailify.core.domain.repository.PoiRepository
import com.trailify.core.domain.repository.ProfileRepository
import com.trailify.core.domain.stats.StatMetric
import com.trailify.core.ui.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    private val profile = Profile("me", "me@example.com", "Ja", null, Role.ADMIN, Instant.EPOCH, ProfileStats(1500.0, 1, 0, 1))
    private val gate = Poi(1, "Brama Krakowska", null, 51.2477, 22.5675, null)
    private val walk = ActivitySummary(7, ActivityType.WALKING, null, Instant.EPOCH, null, 1500.0, 900, 10.0, 2000)

    // "dziś" = 15 października 2026; spacer 10 października i rower we wrześniu
    private val today = LocalDate.of(2026, 10, 15)
    private val octoberWalk = walk.copy(id = 1, startTime = Instant.parse("2026-10-10T08:00:00Z"), distance = 3000.0, stepCount = 4000, durationSeconds = 1800)
    private val octoberRun = walk.copy(id = 2, startTime = Instant.parse("2026-10-10T17:00:00Z"), distance = 5000.0, stepCount = 6000, durationSeconds = 1500)
    private val septemberRide = walk.copy(id = 3, startTime = Instant.parse("2026-09-20T10:00:00Z"), distance = 20000.0, stepCount = null, durationSeconds = 3600)

    private fun viewModel(activities: Activities) =
        ProfileViewModel(Profiles(), Pois(), activities, ZoneOffset.UTC) { today }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun wczytujeProfilKolekcjeIHistorie() {
        val vm = viewModel(Activities(listOf(walk)))
        vm.load()
        val state = vm.state.value
        assertEquals(Role.ADMIN, (state.profile as UiState.Success).data.role)
        assertEquals(listOf("Brama Krakowska"), (state.collection as UiState.Success).data.map { it.poi.name })
        assertEquals(listOf(7L), (state.activities as UiState.Success).data.map { it.id })
    }

    @Test
    fun bladHistoriiNieUkrywaProfilu() {
        val vm = viewModel(Activities(emptyList(), fail = true))
        vm.load()
        assertTrue(vm.state.value.profile is UiState.Success)
        assertEquals("Brak połączenia z internetem", (vm.state.value.activities as UiState.Error).message)
    }

    @Test
    fun statystykiMiesiacaGrupujaAktywnosciPoDniach() {
        val vm = viewModel(Activities(listOf(octoberWalk, octoberRun, septemberRide)))
        vm.load()
        assertEquals(YearMonth.of(2026, 10), vm.state.value.month)
        val month = (vm.state.value.monthStats as UiState.Success).data
        assertEquals(31, month.days.size)
        assertEquals(2, month.activityCount)
        assertEquals(1, month.activeDays)
        assertEquals(8000.0, month.totalDistanceMeters, 0.0)
        assertEquals(10000, month.totalSteps)
        assertEquals(listOf(1L, 2L), month.day(LocalDate.of(2026, 10, 10))!!.activities.map { it.id })
    }

    @Test
    fun poprzedniMiesiacWczytujeInneAktywnosci() {
        val activities = Activities(listOf(octoberWalk, septemberRide))
        val vm = viewModel(activities)
        vm.load()
        vm.previousMonth()
        assertEquals(YearMonth.of(2026, 9), vm.state.value.month)
        val month = (vm.state.value.monthStats as UiState.Success).data
        assertEquals(listOf(3L), month.days.flatMap { it.activities }.map { it.id })
        // zapytanie o wrzesień: od 1 września 00:00 do 1 października 00:00 (UTC)
        assertEquals(Instant.parse("2026-09-01T00:00:00Z") to Instant.parse("2026-10-01T00:00:00Z"), activities.ranges.last())
    }

    @Test
    fun wPrzyszlyMiesiacNieMoznaPrzejsc() {
        val vm = viewModel(Activities(emptyList()))
        vm.load()
        vm.nextMonth()
        assertEquals(YearMonth.of(2026, 10), vm.state.value.month)
        vm.previousMonth()
        vm.nextMonth()
        assertEquals(YearMonth.of(2026, 10), vm.state.value.month)
    }

    @Test
    fun dotkniecieDniaWybieraGoAPonowneOdznacza() {
        val vm = viewModel(Activities(listOf(octoberWalk)))
        vm.load()
        val day = LocalDate.of(2026, 10, 10)
        vm.selectDay(day)
        assertEquals(day, vm.state.value.selectedDay)
        vm.selectDay(day)
        assertNull(vm.state.value.selectedDay)
    }

    @Test
    fun zmianaMiesiacaOdznaczaDzien() {
        val vm = viewModel(Activities(listOf(octoberWalk)))
        vm.load()
        vm.selectDay(LocalDate.of(2026, 10, 10))
        vm.previousMonth()
        assertNull(vm.state.value.selectedDay)
    }

    @Test
    fun zmianaMetrykiZostawiaMiesiac() {
        val vm = viewModel(Activities(listOf(octoberWalk)))
        vm.load()
        vm.selectMetric(StatMetric.STEPS)
        assertEquals(StatMetric.STEPS, vm.state.value.metric)
        assertEquals(YearMonth.of(2026, 10), vm.state.value.month)
    }

    @Test
    fun bladStatystykNieUkrywaProfilu() {
        val vm = viewModel(Activities(emptyList(), fail = true))
        vm.load()
        assertTrue(vm.state.value.profile is UiState.Success)
        assertTrue(vm.state.value.monthStats is UiState.Error)
    }

    @Test
    fun tempoWMinutachNaKilometr() {
        assertEquals("5:30 /km", formatPace(5.5))
        assertEquals("10:00 /km", formatPace(10.0))
    }

    private inner class Profiles : ProfileRepository {
        override suspend fun getMe() = profile
        override suspend fun updateMe(displayName: String?, avatarUrl: String?, backgroundUrl: String?, isPrivate: Boolean?) = profile
        override suspend fun deleteMe() = Unit
    }

    private inner class Pois : PoiRepository {
        override suspend fun inBounds(box: BoundingBox) = listOf(gate)
        override suspend fun propose(draft: PoiDraft): PoiAdmin = error("nieużywane")
        override suspend fun collect(poiId: Long, position: LatLng): PoiCollectionEntry = error("nieużywane")
        override suspend fun myCollection() = listOf(PoiCollectionEntry(gate, Instant.EPOCH))
    }

    /** Filtruje po [from] (włącznie) i [to] (wyłącznie) tak jak serwer i zapamiętuje wszystkie zapytania. */
    private class Activities(private val items: List<ActivitySummary>, private val fail: Boolean = false) : ActivityRepository {
        val ranges = mutableListOf<Pair<Instant?, Instant?>>()

        override suspend fun getMine(page: Int, size: Int, from: Instant?, to: Instant?): Page<ActivitySummary> {
            if (fail) throw AppException("NETWORK_ERROR", "offline")
            ranges += from to to
            val inRange = items.filter { (from == null || !it.startTime.isBefore(from)) && (to == null || it.startTime.isBefore(to)) }
            return Page(inRange, 0, size, inRange.size.toLong(), 1)
        }

        override suspend fun save(draft: ActivityDraft): Activity = error("nieużywane")
        override suspend fun get(id: Long): Activity = error("nieużywane")
        override suspend fun delete(id: Long) = error("nieużywane")
        override suspend fun setShared(id: Long, shared: Boolean) = error("nieużywane")
    }
}
