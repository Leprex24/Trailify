package com.trailify.feature.community.poi

import com.trailify.core.domain.model.AdminUser
import com.trailify.core.domain.model.AppException
import com.trailify.core.domain.model.BoundingBox
import com.trailify.core.domain.model.LatLng
import com.trailify.core.domain.model.Page
import com.trailify.core.domain.model.PhotoRef
import com.trailify.core.domain.model.Poi
import com.trailify.core.domain.model.PoiAdmin
import com.trailify.core.domain.model.PoiCollectionEntry
import com.trailify.core.domain.model.PoiDraft
import com.trailify.core.domain.model.PoiStatus
import com.trailify.core.domain.repository.AdminRepository
import com.trailify.core.domain.repository.PhotoUploader
import com.trailify.core.domain.repository.PoiRepository
import com.trailify.core.domain.repository.UserLocationSource
import com.trailify.core.ui.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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

@OptIn(ExperimentalCoroutinesApi::class)
class CommunityViewModelTest {

    private val gate = Poi(1, "Brama Krakowska", "Opis", 51.2477, 22.5675, null)
    private val castle = Poi(2, "Zamek", "Opis", 51.2492, 22.5815, null)
    private val pois = FakePois(listOf(gate, castle))
    private val location = FakeLocation()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun bezLokalizacjiPokazujePunktyIniczegoNieZbiera() {
        val vm = CommunityViewModel(pois, location)
        assertEquals(2, (vm.ui.value.pois as UiState.Success).data.size)
        assertTrue(pois.collectCalls.isEmpty())
    }

    @Test
    fun podejsciePod50MetrowZbieraPunktAutomatycznie() {
        val vm = CommunityViewModel(pois, location)
        location.location.value = LatLng(51.24772, 22.56752) // kilka metrów od bramy
        assertEquals(listOf(1L), pois.collectCalls)
        assertTrue(1L in vm.ui.value.collectedIds)
        assertEquals("Zebrano punkt: Brama Krakowska", vm.ui.value.message)
    }

    @Test
    fun zebranyPunktNieJestWysylanyDrugiRaz() {
        val vm = CommunityViewModel(pois, location)
        location.location.value = LatLng(51.24772, 22.56752)
        location.location.value = LatLng(51.24773, 22.56753)
        assertEquals(listOf(1L), pois.collectCalls)
        assertTrue(1L in vm.ui.value.collectedIds)
    }

    @Test
    fun zaDalekoAutomatyczneZbieranieNicNiePokazuje() {
        pois.failWith = AppException("POI_TOO_FAR", "daleko")
        val vm = CommunityViewModel(pois, location)
        location.location.value = LatLng(51.24772, 22.56752)
        assertNull(vm.ui.value.message)
    }

    @Test
    fun walidacjaFormularzaPunktu() {
        assertEquals("Podaj nazwę punktu", validatePoiName(" "))
        assertNull(validatePoiName("Brama"))
        assertEquals("Opisz krótko, co jest ciekawego w tym miejscu", validatePoiDescription(""))
        assertTrue(validatePoiDescription("a".repeat(POI_DESCRIPTION_MAX + 1))!!.contains("$POI_DESCRIPTION_MAX"))
    }

    @Test
    fun formularzWysylaZgloszenieZPozycjiUzytkownika() {
        location.location.value = LatLng(51.0, 22.0)
        val admin = FakeAdmin()
        val vm = PoiFormViewModel(asAdmin = false, pois, admin, InstantUploader(), location)
        vm.onNameChange(" Stary młyn ")
        vm.onDescriptionChange("Zabytkowy młyn")
        vm.submit()
        assertEquals(PoiDraft("Stary młyn", "Zabytkowy młyn", 51.0, 22.0, null), pois.proposed.single())
        assertTrue(vm.ui.value.done)
        assertTrue(admin.created.isEmpty())
    }

    @Test
    fun administratorDodajePunktOdRazu() {
        val admin = FakeAdmin()
        val vm = PoiFormViewModel(asAdmin = true, pois, admin, InstantUploader(), location)
        vm.onNameChange("Punkt")
        vm.onDescriptionChange("Opis")
        vm.onMapTap(LatLng(51.1, 22.1)) // bez lokalizacji: miejsce wybrane na mapie
        vm.onPhotoPicked("content://1")
        vm.submit()
        assertEquals(PhotoRef("me/content://1", "https://example.com/1"), admin.created.single().photo)
        assertTrue(pois.proposed.isEmpty())
    }

    @Test
    fun formularzBezMiejscaPokazujeBlad() {
        val vm = PoiFormViewModel(asAdmin = false, pois, FakeAdmin(), InstantUploader(), location)
        vm.onNameChange("Punkt")
        vm.onDescriptionChange("Opis")
        vm.submit()
        assertEquals("Wybierz miejsce na mapie (albo włącz lokalizację)", vm.ui.value.generalError)
    }
}

internal class FakeLocation : UserLocationSource {
    override val location = MutableStateFlow<LatLng?>(null)
    override fun setWatching(watching: Boolean) = Unit
}

internal class InstantUploader : PhotoUploader {
    override suspend fun upload(imageUri: String) = PhotoRef("me/$imageUri", "https://example.com/${imageUri.substringAfterLast('/')}")
}

internal class FakePois(private val all: List<Poi>) : PoiRepository {
    val collectCalls = mutableListOf<Long>()
    val proposed = mutableListOf<PoiDraft>()
    var failWith: Exception? = null

    override suspend fun inBounds(box: BoundingBox) = all
    override suspend fun propose(draft: PoiDraft): PoiAdmin {
        proposed += draft
        return PoiAdmin(Poi(9, draft.name, draft.description, draft.latitude, draft.longitude, null), PoiStatus.PENDING, null, null, Instant.EPOCH)
    }

    override suspend fun collect(poiId: Long, position: LatLng): PoiCollectionEntry {
        failWith?.let { throw it }
        collectCalls += poiId
        return PoiCollectionEntry(all.first { it.id == poiId }, Instant.EPOCH)
    }

    override suspend fun myCollection() = emptyList<PoiCollectionEntry>()
}

internal class FakeAdmin : AdminRepository {
    val created = mutableListOf<PoiDraft>()
    val calls = mutableListOf<String>()
    var poiList = mutableListOf<PoiAdmin>()
    var userList = mutableListOf<AdminUser>()

    override suspend fun pois(status: PoiStatus?, page: Int, size: Int) =
        Page(poiList.filter { status == null || it.status == status }, 0, size, poiList.size.toLong(), 1)

    override suspend fun createPoi(draft: PoiDraft): PoiAdmin {
        created += draft
        return PoiAdmin(Poi(10, draft.name, draft.description, draft.latitude, draft.longitude, null), PoiStatus.APPROVED, null, null, Instant.EPOCH)
    }

    override suspend fun updatePoi(id: Long, name: String?, description: String?): PoiAdmin {
        calls += "update $id $name $description"
        return poiList.first { it.poi.id == id }
    }

    override suspend fun deletePoi(id: Long) {
        calls += "delete $id"
        poiList.removeAll { it.poi.id == id }
    }

    override suspend fun approvePoi(id: Long) {
        calls += "approve $id"
        poiList.replaceAll { if (it.poi.id == id) it.copy(status = PoiStatus.APPROVED) else it }
    }

    override suspend fun rejectPoi(id: Long, reason: String?) {
        calls += "reject $id $reason"
        poiList.replaceAll { if (it.poi.id == id) it.copy(status = PoiStatus.REJECTED, rejectionReason = reason) else it }
    }

    override suspend fun users(query: String?, page: Int, size: Int) =
        Page(userList.filter { query.isNullOrBlank() || it.profile.displayName.contains(query, true) }, 0, size, userList.size.toLong(), 1)

    override suspend fun blockUser(id: String, deleteContent: Boolean) {
        calls += "block $id $deleteContent"
    }

    override suspend fun unblockUser(id: String) {
        calls += "unblock $id"
    }

    override suspend fun deleteUser(id: String) {
        calls += "deleteUser $id"
    }
}
