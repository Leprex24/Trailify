package com.trailify.feature.community.admin

import com.trailify.core.domain.model.AdminUser
import com.trailify.core.domain.model.Poi
import com.trailify.core.domain.model.PoiAdmin
import com.trailify.core.domain.model.PoiStatus
import com.trailify.core.domain.model.Profile
import com.trailify.core.domain.model.ProfileStats
import com.trailify.core.domain.model.Role
import com.trailify.core.domain.model.UserStatus
import com.trailify.core.ui.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class AdminViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val admin = FakeAdmin().apply {
        poiList = mutableListOf(poi(1, PoiStatus.PENDING), poi(2, PoiStatus.APPROVED))
        userList = mutableListOf(user("u1", "Anna"), user("u2", "Marek"))
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun domyslniePokazujeKolejkeZgloszen() = runTest(dispatcher) {
        val vm = AdminViewModel(admin)
        advanceUntilIdle()
        assertEquals(listOf(1L), (vm.ui.value.pois as UiState.Success).data.map { it.poi.id })
    }

    @Test
    fun zatwierdzenieZdejmujePunktZKolejki() = runTest(dispatcher) {
        val vm = AdminViewModel(admin)
        advanceUntilIdle()
        vm.approve(admin.poiList.first())
        advanceUntilIdle()
        assertEquals("approve 1", admin.calls.single())
        assertTrue((vm.ui.value.pois as UiState.Success).data.isEmpty())
        assertEquals("Zatwierdzono: Punkt 1", vm.ui.value.message)
    }

    @Test
    fun odrzuceniePrzekazujePowodLubNull() = runTest(dispatcher) {
        val vm = AdminViewModel(admin)
        advanceUntilIdle()
        vm.reject(admin.poiList.first(), "   ")
        advanceUntilIdle()
        assertEquals("reject 1 null", admin.calls.single())
    }

    @Test
    fun edycjaWysylaTylkoZmienionePola() = runTest(dispatcher) {
        val vm = AdminViewModel(admin)
        advanceUntilIdle()
        vm.updatePoi(admin.poiList.first(), name = "Nowa nazwa", description = "Opis 1")
        advanceUntilIdle()
        assertEquals("update 1 Nowa nazwa null", admin.calls.single())
    }

    @Test
    fun wyszukiwanieUzytkownikow() = runTest(dispatcher) {
        val vm = AdminViewModel(admin)
        vm.onUserQueryChange("mar")
        advanceUntilIdle()
        assertEquals(listOf("Marek"), (vm.ui.value.users as UiState.Success).data.map { it.profile.displayName })
    }

    @Test
    fun blokadaZUsunieciemTresci() = runTest(dispatcher) {
        val vm = AdminViewModel(admin)
        advanceUntilIdle()
        vm.block(admin.userList.first(), deleteContent = true)
        advanceUntilIdle()
        assertEquals("block u1 true", admin.calls.single())
        assertEquals("Zablokowano i usunięto treści: Anna", vm.ui.value.message)
    }

    private fun poi(id: Long, status: PoiStatus) =
        PoiAdmin(Poi(id, "Punkt $id", "Opis $id", 51.0, 22.0, null), status, null, null, Instant.EPOCH)

    private fun user(id: String, name: String) = AdminUser(
        Profile(id, "$name@example.com", name, null, Role.USER, Instant.EPOCH, ProfileStats(0.0, 0, 0, 0)),
        UserStatus.ACTIVE,
    )
}
