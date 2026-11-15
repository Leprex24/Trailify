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
import com.trailify.core.domain.model.UserSummary
import com.trailify.core.domain.repository.ActivityRepository
import com.trailify.core.domain.repository.AuthRepository
import com.trailify.core.domain.repository.PoiRepository
import com.trailify.core.domain.repository.ProfileRepository
import com.trailify.core.ui.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    private val profile = Profile("me", "me@example.com", "Ja", null, Role.ADMIN, Instant.EPOCH, ProfileStats(1500.0, 1, 0, 1))
    private val gate = Poi(1, "Brama Krakowska", null, 51.2477, 22.5675, null)
    private val walk = ActivitySummary(7, ActivityType.WALKING, null, Instant.EPOCH, null, 1500.0, 900, 10.0, 2000)

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun wczytujeProfilKolekcjeIHistorie() {
        val vm = ProfileViewModel(Profiles(), Auth(), Pois(), Activities(listOf(walk)))
        vm.load()
        val state = vm.state.value
        assertEquals(Role.ADMIN, (state.profile as UiState.Success).data.role)
        assertEquals(listOf("Brama Krakowska"), (state.collection as UiState.Success).data.map { it.poi.name })
        assertEquals(listOf(7L), (state.activities as UiState.Success).data.map { it.id })
    }

    @Test
    fun bladHistoriiNieUkrywaProfilu() {
        val vm = ProfileViewModel(Profiles(), Auth(), Pois(), Activities(emptyList(), fail = true))
        vm.load()
        assertTrue(vm.state.value.profile is UiState.Success)
        assertEquals("Brak połączenia z internetem", (vm.state.value.activities as UiState.Error).message)
    }

    @Test
    fun tempoWMinutachNaKilometr() {
        assertEquals("5:30 /km", formatPace(5.5))
        assertEquals("10:00 /km", formatPace(10.0))
    }

    private inner class Profiles : ProfileRepository {
        override suspend fun getMe() = profile
        override suspend fun updateMe(displayName: String?, avatarUrl: String?) = profile
        override suspend fun deleteMe() = Unit
    }

    private class Auth : AuthRepository {
        override val currentUser = MutableStateFlow<UserSummary?>(UserSummary("me", "Ja"))
        override suspend fun signIn(email: String, password: String) = Unit
        override suspend fun signUp(email: String, password: String, displayName: String) = Unit
        override suspend fun signOut() = Unit
    }

    private inner class Pois : PoiRepository {
        override suspend fun inBounds(box: BoundingBox) = listOf(gate)
        override suspend fun propose(draft: PoiDraft): PoiAdmin = error("nieużywane")
        override suspend fun collect(poiId: Long, position: LatLng): PoiCollectionEntry = error("nieużywane")
        override suspend fun myCollection() = listOf(PoiCollectionEntry(gate, Instant.EPOCH))
    }

    private class Activities(private val items: List<ActivitySummary>, private val fail: Boolean = false) : ActivityRepository {
        override suspend fun getMine(page: Int, size: Int): Page<ActivitySummary> {
            if (fail) throw AppException("NETWORK_ERROR", "offline")
            return Page(items, 0, size, items.size.toLong(), 1)
        }

        override suspend fun save(draft: ActivityDraft): Activity = error("nieużywane")
        override suspend fun get(id: Long): Activity = error("nieużywane")
        override suspend fun delete(id: Long) = error("nieużywane")
    }
}
