package com.trailify.feature.profile

import com.trailify.core.domain.model.ActivitySummary
import com.trailify.core.domain.model.ActivityType
import com.trailify.core.ui.UiState
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
class OtherProfileViewModelTest {

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun profileOf(vm: OtherProfileViewModel) = (vm.state.value.profile as UiState.Success).data

    @Test
    fun publicznyProfilWczytujeTrasyIPunkty() {
        val vm = OtherProfileViewModel("anna", FakeSocialRepository(routes = listOf(routeSummary(1), routeSummary(2))))
        vm.load()
        assertEquals("Anna", profileOf(vm).displayName)
        assertEquals(listOf(1L, 2L), (vm.state.value.routes as UiState.Success).data.map { it.id })
        assertEquals(1, (vm.state.value.collection as UiState.Success).data.size)
    }

    @Test
    fun prywatnyProfilNiePobieraTrasIPunktow() {
        val vm = OtherProfileViewModel("anna", FakeSocialRepository(profile = publicProfile(isPrivate = true)))
        vm.load()
        assertNull(profileOf(vm).stats)
        // trasy i punkty zostają w stanie początkowym: nie było zapytań, które skończyłyby się 403
        assertTrue(vm.state.value.routes is UiState.Loading)
        assertTrue(vm.state.value.collection is UiState.Loading)
    }

    @Test
    fun obserwujeIZwiekszaLicznikObserwujacych() {
        val social = FakeSocialRepository(profile = publicProfile(followers = 3))
        val vm = OtherProfileViewModel("anna", social)
        vm.load()
        vm.toggleFollow()
        assertEquals(listOf("anna"), social.followCalls)
        assertTrue(profileOf(vm).isFollowing)
        assertEquals(4, profileOf(vm).stats!!.followerCount)
        assertFalse(vm.state.value.followBusy)
    }

    @Test
    fun przestajeObserwowacIZmniejszaLicznik() {
        val social = FakeSocialRepository(profile = publicProfile(isFollowing = true, followers = 3))
        val vm = OtherProfileViewModel("anna", social)
        vm.load()
        vm.toggleFollow()
        assertEquals(listOf("anna"), social.unfollowCalls)
        assertFalse(profileOf(vm).isFollowing)
        assertEquals(2, profileOf(vm).stats!!.followerCount)
    }

    @Test
    fun bladObserwowaniaZostawiaProfilIPokazujeKomunikat() {
        val vm = OtherProfileViewModel("anna", FakeSocialRepository(failFollow = true))
        vm.load()
        vm.toggleFollow()
        assertFalse(profileOf(vm).isFollowing)
        assertNotNull(vm.state.value.followError)
        assertFalse(vm.state.value.followBusy)
    }

    @Test
    fun wlasnegoProfiluNieMoznaObserwowac() {
        val social = FakeSocialRepository(profile = publicProfile(isMe = true))
        val vm = OtherProfileViewModel("anna", social)
        vm.load()
        vm.toggleFollow()
        assertTrue(social.followCalls.isEmpty())
    }

    @Test
    fun prywatnegoProfiluNieMoznaZaczacObserwowacAleMoznaPrzestac() {
        assertFalse(publicProfile(isPrivate = true).canToggleFollow())
        assertTrue(publicProfile(isPrivate = true, isFollowing = true).canToggleFollow())
        assertTrue(publicProfile().canToggleFollow())
        assertFalse(publicProfile(isMe = true).canToggleFollow())
    }

    @Test
    fun licznikObserwujacychNieSpadaPonizejZera() {
        val profile = publicProfile(isFollowing = true, followers = 0)
        assertEquals(0, profile.withFollowing(false).stats!!.followerCount)
    }

    @Test
    fun brakZmianyObserwowaniaNiczegoNieZmienia() {
        val profile = publicProfile(isFollowing = true)
        assertEquals(profile, profile.withFollowing(true))
    }

    @Test
    fun wczytujeUdostepnioneAktywnosci() {
        val shared = ActivitySummary(5, ActivityType.CYCLING, null, Instant.EPOCH, null, 8000.0, 1800, 3.7, null, isShared = true)
        val vm = OtherProfileViewModel("anna", FakeSocialRepository(activities = listOf(shared)))
        vm.load()
        assertEquals(listOf(5L), (vm.state.value.activities as UiState.Success).data.map { it.id })
    }

    @Test
    fun prywatnyProfilNiePobieraAktywnosci() {
        val vm = OtherProfileViewModel("anna", FakeSocialRepository(profile = publicProfile(isPrivate = true)))
        vm.load()
        assertTrue(vm.state.value.activities is UiState.Loading)
    }
}
