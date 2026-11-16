package com.trailify.feature.profile

import com.trailify.core.domain.model.AppException
import com.trailify.core.domain.model.PhotoRef
import com.trailify.core.domain.model.Profile
import com.trailify.core.domain.model.ProfileStats
import com.trailify.core.domain.model.Role
import com.trailify.core.domain.repository.PhotoUploader
import com.trailify.core.domain.repository.ProfileRepository
import kotlinx.coroutines.CompletableDeferred
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
class EditProfileViewModelTest {

    private val stats = ProfileStats(0.0, 0, 0, 0)
    private var stored = Profile("me", "me@example.com", "Ja", null, Role.USER, Instant.EPOCH, stats)

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun startWczytujeAktualnyProfil() {
        stored = stored.copy(displayName = "Kacper", isPrivate = true, backgroundUrl = "https://x/bg.jpg")
        val vm = EditProfileViewModel(Profiles(), Uploader())
        vm.start()
        val ui = vm.ui.value
        assertEquals("Kacper", ui.displayName)
        assertTrue(ui.isPrivate)
        assertEquals("https://x/bg.jpg", ui.backgroundUrl)
        assertFalse(ui.isLoading)
    }

    @Test
    fun zapisWysylaWszystkieZmiany() {
        val profiles = Profiles()
        val vm = EditProfileViewModel(profiles, Uploader())
        vm.start()
        vm.onDisplayNameChange("  Nowa nazwa ")
        vm.onPrivateChange(true)
        vm.save()
        assertEquals(Update("Nowa nazwa", null, null, true), profiles.updates.single())
        assertTrue(vm.ui.value.saved)
    }

    @Test
    fun wyslaneZdjecieTrafiaDoProfiluDopieroPoZapisie() {
        val profiles = Profiles()
        val vm = EditProfileViewModel(profiles, Uploader())
        vm.start()
        vm.onPhotoPicked(ProfilePhoto.AVATAR, "content://zdjecie")
        assertEquals("https://storage/content://zdjecie", vm.ui.value.avatarUrl)
        assertTrue(profiles.updates.isEmpty())
        vm.save()
        assertEquals("https://storage/content://zdjecie", profiles.updates.single().avatarUrl)
    }

    @Test
    fun podczasWysylaniaZdjeciaNieMoznaZapisac() {
        val uploader = Uploader(gate = CompletableDeferred())
        val vm = EditProfileViewModel(Profiles(), uploader)
        vm.start()
        vm.onPhotoPicked(ProfilePhoto.BACKGROUND, "content://tlo")
        assertTrue(ProfilePhoto.BACKGROUND in vm.ui.value.uploading)
        assertFalse(vm.ui.value.canSave)
        uploader.gate!!.complete(Unit)
        assertTrue(vm.ui.value.uploading.isEmpty())
        assertTrue(vm.ui.value.canSave)
    }

    @Test
    fun bladWysylaniaZdjeciaPokazujeKomunikatIZostawiaStareZdjecie() {
        stored = stored.copy(avatarUrl = "https://x/stare.jpg")
        val vm = EditProfileViewModel(Profiles(), Uploader(fail = true))
        vm.start()
        vm.onPhotoPicked(ProfilePhoto.AVATAR, "content://zdjecie")
        assertNotNull(vm.ui.value.error)
        assertEquals("https://x/stare.jpg", vm.ui.value.avatarUrl)
        assertTrue(vm.ui.value.canSave)
    }

    @Test
    fun pustaNazwaNieJestZapisywana() {
        val profiles = Profiles()
        val vm = EditProfileViewModel(profiles, Uploader())
        vm.start()
        vm.onDisplayNameChange("   ")
        vm.save()
        assertNotNull(vm.ui.value.error)
        assertTrue(profiles.updates.isEmpty())
        assertFalse(vm.ui.value.saved)
    }

    @Test
    fun bladZapisuPokazujeKomunikat() {
        val vm = EditProfileViewModel(Profiles(fail = true), Uploader())
        vm.start()
        vm.save()
        assertNotNull(vm.ui.value.error)
        assertFalse(vm.ui.value.saved)
        assertFalse(vm.ui.value.isSaving)
    }

    @Test
    fun consumeSavedCzyscizNaprawionyZapis() {
        val vm = EditProfileViewModel(Profiles(), Uploader())
        vm.start()
        vm.save()
        assertTrue(vm.ui.value.saved)
        vm.consumeSaved()
        assertFalse(vm.ui.value.saved)
    }

    @Test
    fun ponowneStartCzysciPoprzedniaEdycje() {
        val vm = EditProfileViewModel(Profiles(), Uploader())
        vm.start()
        vm.onDisplayNameChange("Zmieniona")
        vm.start()
        assertEquals("Ja", vm.ui.value.displayName)
        assertNull(vm.ui.value.error)
    }

    private data class Update(val displayName: String?, val avatarUrl: String?, val backgroundUrl: String?, val isPrivate: Boolean?)

    private inner class Profiles(private val fail: Boolean = false) : ProfileRepository {
        val updates = mutableListOf<Update>()

        override suspend fun getMe() = stored

        override suspend fun updateMe(displayName: String?, avatarUrl: String?, backgroundUrl: String?, isPrivate: Boolean?): Profile {
            if (fail) throw AppException("NETWORK_ERROR", "offline")
            updates += Update(displayName, avatarUrl, backgroundUrl, isPrivate)
            return stored
        }

        override suspend fun deleteMe() = Unit
    }

    /** [gate] wstrzymuje wysyłanie do czasu complete(), żeby test mógł sprawdzić stan "w trakcie". */
    private class Uploader(val gate: CompletableDeferred<Unit>? = null, private val fail: Boolean = false) : PhotoUploader {
        override suspend fun upload(imageUri: String): PhotoRef {
            gate?.await()
            if (fail) throw AppException("NETWORK_ERROR", "offline")
            return PhotoRef("key/$imageUri", "https://storage/$imageUri")
        }
    }
}
