package com.trailify.feature.community.feed

import com.trailify.core.domain.model.Page
import com.trailify.core.domain.model.PublicProfile
import com.trailify.core.domain.model.UserSummary
import com.trailify.core.domain.model.ActivitySummary
import com.trailify.core.domain.model.PoiCollectionEntry
import com.trailify.core.domain.model.RouteSummary
import com.trailify.core.domain.repository.SocialRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UserSearchViewModelTest {

    private val scheduler = TestCoroutineScheduler()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher(scheduler))

    @After
    fun tearDown() = Dispatchers.resetMain()

    private class Social(val people: List<UserSummary>) : SocialRepository {
        val queries = mutableListOf<String>()

        override suspend fun search(query: String, page: Int, size: Int): Page<UserSummary> {
            queries += query
            return Page(people, page, size, people.size.toLong(), 1)
        }

        override suspend fun getProfile(userId: String): PublicProfile = error("nieużywane")
        override suspend fun follow(userId: String) = error("nieużywane")
        override suspend fun unfollow(userId: String) = error("nieużywane")
        override suspend fun followers(userId: String, page: Int, size: Int): Page<UserSummary> = error("nieużywane")
        override suspend fun following(userId: String, page: Int, size: Int): Page<UserSummary> = error("nieużywane")
        override suspend fun routes(userId: String, page: Int, size: Int): Page<RouteSummary> = error("nieużywane")
        override suspend fun poiCollection(userId: String): List<PoiCollectionEntry> = error("nieużywane")
        override suspend fun activities(userId: String, page: Int, size: Int): Page<ActivitySummary> = error("nieużywane")
        override suspend fun removeFollower(userId: String) = error("nieużywane")
    }

    @Test
    fun zaKrotkaFrazaNieWolaSerwera() {
        val social = Social(listOf(ANNA))
        val vm = UserSearchViewModel(social)
        vm.onQueryChange("a")
        scheduler.advanceTimeBy(1_000)
        scheduler.runCurrent()
        assertTrue(social.queries.isEmpty())
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun szukaPoOpoznieniuIPokazujeWyniki() {
        val social = Social(listOf(ANNA, JAN))
        val vm = UserSearchViewModel(social)
        vm.onQueryChange("an")
        scheduler.advanceTimeBy(UserSearchViewModel.DEBOUNCE_MS - 1)
        scheduler.runCurrent()
        assertTrue(social.queries.isEmpty())
        scheduler.advanceTimeBy(1)
        scheduler.runCurrent()
        assertEquals(listOf("an"), social.queries)
        assertEquals(2, vm.state.value.results.size)
        assertTrue(vm.state.value.searched)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun kolejnyZnakAnulujePoprzednieZapytanie() {
        val social = Social(listOf(ANNA))
        val vm = UserSearchViewModel(social)
        vm.onQueryChange("an")
        scheduler.advanceTimeBy(100)
        vm.onQueryChange("ann")
        scheduler.advanceTimeBy(UserSearchViewModel.DEBOUNCE_MS)
        scheduler.runCurrent()
        assertEquals(listOf("ann"), social.queries)
    }

    @Test
    fun czysciWynikiPoSkroceniuFrazy() {
        val social = Social(listOf(ANNA))
        val vm = UserSearchViewModel(social)
        vm.onQueryChange("an")
        scheduler.advanceTimeBy(UserSearchViewModel.DEBOUNCE_MS)
        scheduler.runCurrent()
        vm.onQueryChange("a")
        assertTrue(vm.state.value.results.isEmpty())
        assertFalse(vm.state.value.searched)
    }

    @Test
    fun frazaJestObcinanaDoLimitu() {
        val vm = UserSearchViewModel(Social(emptyList()))
        vm.onQueryChange("x".repeat(SEARCH_QUERY_MAX + 10))
        assertEquals(SEARCH_QUERY_MAX, vm.state.value.query.length)
    }
}
