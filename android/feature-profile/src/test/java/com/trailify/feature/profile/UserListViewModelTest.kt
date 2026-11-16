package com.trailify.feature.profile

import com.trailify.core.domain.model.UserSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UserListViewModelTest {

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun people(count: Int) = (1..count).map { UserSummary("u$it", "Osoba $it") }

    @Test
    fun pierwszaStronaPokazujeOsobyIInformujeOKolejnych() {
        val vm = UserListViewModel("anna", UserListKind.FOLLOWERS, FakeSocialRepository(users = people(UserListViewModel.PAGE_SIZE + 5)))
        vm.load()
        val state = vm.state.value
        assertEquals(UserListViewModel.PAGE_SIZE, state.items.size)
        assertTrue(state.hasMore)
        assertFalse(state.isLoading)
    }

    @Test
    fun loadMoreDokladaKolejnaStroneIKonczyNaOstatniej() {
        val social = FakeSocialRepository(users = people(UserListViewModel.PAGE_SIZE + 5))
        val vm = UserListViewModel("anna", UserListKind.FOLLOWING, social)
        vm.load()
        vm.loadMore()
        assertEquals(UserListViewModel.PAGE_SIZE + 5, vm.state.value.items.size)
        assertFalse(vm.state.value.hasMore)
        assertEquals(listOf(0, 1), social.requestedPages)
        vm.loadMore() // nic więcej do pobrania
        assertEquals(listOf(0, 1), social.requestedPages)
    }

    @Test
    fun odswiezenieZaczynaOdPierwszejStrony() {
        val social = FakeSocialRepository(users = people(3))
        val vm = UserListViewModel("anna", UserListKind.FOLLOWERS, social)
        vm.load()
        vm.load()
        assertEquals(listOf(0, 0), social.requestedPages)
        assertEquals(3, vm.state.value.items.size)
    }

    @Test
    fun pustaListaNieJestBledem() {
        val vm = UserListViewModel("anna", UserListKind.FOLLOWERS, FakeSocialRepository())
        vm.load()
        assertTrue(vm.state.value.items.isEmpty())
        assertNull(vm.state.value.error)
        assertFalse(vm.state.value.hidden)
    }

    @Test
    fun prywatnyProfilUkrywaListeBezKomunikatuOBledzie() {
        val vm = UserListViewModel("anna", UserListKind.FOLLOWERS, FakeSocialRepository(profile = publicProfile(isPrivate = true)))
        vm.load()
        assertTrue(vm.state.value.hidden)
        assertNull(vm.state.value.error)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun rodzajListyZAdresuMaBezpieczneWartoscDomyslna() {
        assertEquals(UserListKind.FOLLOWING, UserListKind.fromRoute("following"))
        assertEquals(UserListKind.FOLLOWERS, UserListKind.fromRoute("followers"))
        assertEquals(UserListKind.FOLLOWERS, UserListKind.fromRoute("cokolwiek"))
        assertEquals(UserListKind.FOLLOWERS, UserListKind.fromRoute(null))
    }

    @Test
    fun usuwanieObserwujacegoDziala_tylkoNaWlasnejListeObserwujacych() {
        assertTrue(UserListViewModel("ja", UserListKind.FOLLOWERS, FakeSocialRepository(), meId = "ja").canRemove)
        assertFalse(UserListViewModel("ja", UserListKind.FOLLOWING, FakeSocialRepository(), meId = "ja").canRemove)
        assertFalse(UserListViewModel("anna", UserListKind.FOLLOWERS, FakeSocialRepository(), meId = "ja").canRemove)
        assertFalse(UserListViewModel("ja", UserListKind.FOLLOWERS, FakeSocialRepository()).canRemove)
    }

    @Test
    fun usuniecieObserwujacegoWolaSerwerIZdejmujeOsobeZListy() {
        val social = FakeSocialRepository(users = people(3))
        val vm = UserListViewModel("ja", UserListKind.FOLLOWERS, social, meId = "ja")
        vm.load()
        vm.removeFollower(vm.state.value.items[1])
        assertEquals(listOf("u2"), social.removedFollowers)
        assertEquals(listOf("u1", "u3"), vm.state.value.items.map { it.id })
    }

    @Test
    fun bladUsunieciaZostawiaOsobeNaLiscieIPokazujeKomunikat() {
        val social = FakeSocialRepository(users = people(2), failFollow = true)
        val vm = UserListViewModel("ja", UserListKind.FOLLOWERS, social, meId = "ja")
        vm.load()
        vm.removeFollower(vm.state.value.items[0])
        assertEquals(2, vm.state.value.items.size)
        assertTrue(vm.state.value.message != null)
        vm.dismissMessage()
        assertNull(vm.state.value.message)
    }

    @Test
    fun cudzaListaNieUsuwaNikogo() {
        val social = FakeSocialRepository(users = people(2))
        val vm = UserListViewModel("anna", UserListKind.FOLLOWERS, social, meId = "ja")
        vm.load()
        vm.removeFollower(vm.state.value.items[0])
        assertTrue(social.removedFollowers.isEmpty())
        assertEquals(2, vm.state.value.items.size)
    }
}
