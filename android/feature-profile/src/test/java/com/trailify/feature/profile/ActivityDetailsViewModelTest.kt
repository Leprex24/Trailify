package com.trailify.feature.profile

import com.trailify.core.domain.model.Activity
import com.trailify.core.domain.model.ActivityDraft
import com.trailify.core.domain.model.ActivitySummary
import com.trailify.core.domain.model.ActivityType
import com.trailify.core.domain.model.AppException
import com.trailify.core.domain.model.Page
import com.trailify.core.domain.repository.ActivityRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ActivityDetailsViewModelTest {

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private class Activities(var shared: Boolean = false, var failShare: Boolean = false) : ActivityRepository {
        val shareCalls = mutableListOf<Pair<Long, Boolean>>()

        override suspend fun getMine(page: Int, size: Int, from: Instant?, to: Instant?): Page<ActivitySummary> = error("nieużywane")
        override suspend fun save(draft: ActivityDraft): Activity = error("nieużywane")
        override suspend fun get(id: Long) =
            Activity(ActivitySummary(id, ActivityType.WALKING, null, Instant.EPOCH, null, 1000.0, 600, 10.0, null, isShared = shared), emptyList())

        override suspend fun delete(id: Long) = error("nieużywane")

        override suspend fun setShared(id: Long, shared: Boolean) {
            if (failShare) throw AppException("NETWORK_ERROR", "offline")
            shareCalls += id to shared
            this.shared = shared
        }
    }

    @Test
    fun poWczytaniuPrzelacznikPokazujeStanZSerwera() {
        val vm = ActivityDetailsViewModel(Activities(shared = true))
        vm.load(7)
        assertTrue(vm.shared.value)
    }

    @Test
    fun domyslnieAktywnoscJestPrywatna() {
        val vm = ActivityDetailsViewModel(Activities())
        vm.load(7)
        assertFalse(vm.shared.value)
    }

    @Test
    fun udostepnienieWolaSerwerIZostajeWlaczone() {
        val activities = Activities()
        val vm = ActivityDetailsViewModel(activities)
        vm.load(7)
        vm.setShared(7, true)
        assertEquals(listOf(7L to true), activities.shareCalls)
        assertTrue(vm.shared.value)
        assertNull(vm.shareError.value)
    }

    @Test
    fun bladSieciCofaPrzelacznikIPokazujeKomunikat() {
        val vm = ActivityDetailsViewModel(Activities(failShare = true))
        vm.load(7)
        vm.setShared(7, true)
        assertFalse(vm.shared.value)
        assertNotNull(vm.shareError.value)
        vm.dismissShareError()
        assertNull(vm.shareError.value)
    }
}
