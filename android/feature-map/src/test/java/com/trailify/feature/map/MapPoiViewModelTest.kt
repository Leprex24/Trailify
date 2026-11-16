package com.trailify.feature.map

import com.trailify.core.domain.model.AppException
import com.trailify.core.domain.model.BoundingBox
import com.trailify.core.domain.model.LatLng
import com.trailify.core.domain.model.Poi
import com.trailify.core.domain.model.PoiAdmin
import com.trailify.core.domain.model.PoiCollectionEntry
import com.trailify.core.domain.model.PoiDraft
import com.trailify.core.domain.repository.PoiFocusController
import com.trailify.core.domain.repository.PoiRepository
import com.trailify.core.domain.repository.UserLocationSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
class MapPoiViewModelTest {

    private val gate = Poi(1, "Brama Krakowska", null, 51.2477, 22.5675, null)
    private val far = Poi(2, "Zamek", null, 51.2500, 22.5800, null)
    private val here = LatLng(51.2477, 22.5675)
    private val viewport = BoundingBox(51.20, 22.50, 51.30, 22.60)

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(pois: FakePois, location: FakeLocation = FakeLocation(), focus: FakeFocus = FakeFocus()) =
        MapPoiViewModel(pois, location, focus)

    @Test
    fun kamerasciagaPunktyWidocznegoObszaru() {
        val pois = FakePois(listOf(gate, far))
        val vm = viewModel(pois)
        vm.onCameraIdle(viewport)
        assertEquals(listOf(1L, 2L), vm.ui.value.pois.map { it.id })
        assertEquals(1, pois.boxes.size)
    }

    @Test
    fun drugiRuchWTymSamymObszarzeNiePobieraPonownie() {
        val pois = FakePois(listOf(gate))
        val vm = viewModel(pois)
        vm.onCameraIdle(viewport)
        vm.onCameraIdle(BoundingBox(51.21, 22.51, 51.29, 22.59))
        assertEquals(1, pois.boxes.size)
    }

    @Test
    fun przesunieciePozaObszarPobieraNowy() {
        val pois = FakePois(listOf(gate))
        val vm = viewModel(pois)
        vm.onCameraIdle(viewport)
        vm.onCameraIdle(BoundingBox(51.60, 22.50, 51.70, 22.60))
        assertEquals(2, pois.boxes.size)
    }

    @Test
    fun zbytOddalonaMapaNiePobieraPunktowIOznaczaOddalenie() {
        val pois = FakePois(listOf(gate))
        val vm = viewModel(pois)
        vm.onCameraIdle(BoundingBox(50.0, 20.0, 52.0, 24.0))
        assertTrue(vm.ui.value.zoomedOut)
        assertTrue(pois.boxes.isEmpty())
        vm.onCameraIdle(viewport)
        assertFalse(vm.ui.value.zoomedOut)
    }

    @Test
    fun wylaczonaWarstwaNiePobieraPunktowZWidokuAleWlaczenieJePobiera() {
        val pois = FakePois(listOf(gate))
        val vm = viewModel(pois)
        vm.toggleEnabled()
        vm.onCameraIdle(viewport)
        assertTrue(pois.boxes.isEmpty())
        vm.toggleEnabled()
        assertTrue(vm.ui.value.enabled)
        assertEquals(1, pois.boxes.size)
    }

    @Test
    fun wylaczenieWarstwyOdznaczaWybranyPunkt() {
        val vm = viewModel(FakePois(listOf(gate)))
        vm.onCameraIdle(viewport)
        vm.select(1)
        assertEquals("Brama Krakowska", vm.ui.value.selected?.name)
        vm.toggleEnabled()
        assertNull(vm.ui.value.selectedId)
    }

    @Test
    fun podejscieDoPunktuZbieraGoAutomatycznie() {
        val pois = FakePois(listOf(gate, far))
        val location = FakeLocation()
        val vm = viewModel(pois, location)
        vm.onCameraIdle(viewport)
        location.position.value = here
        assertEquals(listOf(1L), pois.collected)
        assertTrue(1L in vm.ui.value.collectedIds)
        assertEquals("Zebrano punkt: Brama Krakowska", vm.ui.value.message)
    }

    @Test
    fun zbieranieDzialaTezPrzyWylaczonejWarstwie() {
        val pois = FakePois(listOf(gate))
        val location = FakeLocation()
        val vm = viewModel(pois, location)
        vm.toggleEnabled()
        location.position.value = here // okolica użytkownika pobiera się niezależnie od widoku mapy
        assertEquals(listOf(1L), pois.collected)
    }

    @Test
    fun zebranyWczesniejPunktNieJestZbieranyPonownie() {
        val pois = FakePois(listOf(gate), alreadyCollected = setOf(1L))
        val location = FakeLocation()
        val vm = viewModel(pois, location)
        vm.onCameraIdle(viewport)
        location.position.value = here
        assertTrue(pois.collected.isEmpty())
        assertTrue(1L in vm.ui.value.collectedIds)
    }

    @Test
    fun odpowiedzAlreadyCollectedOznaczaPunktJakoZebranyBezKomunikatu() {
        val pois = FakePois(listOf(gate), collectError = AppException("ALREADY_COLLECTED", "już"))
        val location = FakeLocation()
        val vm = viewModel(pois, location)
        vm.onCameraIdle(viewport)
        location.position.value = here
        assertTrue(1L in vm.ui.value.collectedIds)
        assertNull(vm.ui.value.message)
    }

    @Test
    fun automatyczneZbieranieMilczyPrzyZaDaleko() {
        val pois = FakePois(listOf(gate), collectError = AppException("POI_TOO_FAR", "daleko"))
        val location = FakeLocation()
        val vm = viewModel(pois, location)
        vm.onCameraIdle(viewport)
        location.position.value = here
        assertNull(vm.ui.value.message)
        assertFalse(1L in vm.ui.value.collectedIds)
    }

    @Test
    fun recznieZebranieZaDalekoPokazujeKomunikat() {
        val pois = FakePois(listOf(gate), collectError = AppException("POI_TOO_FAR", "daleko"))
        val location = FakeLocation()
        val vm = viewModel(pois, location)
        vm.onCameraIdle(viewport)
        location.position.value = LatLng(51.2477, 22.5700) // poza zasięgiem, więc nic się nie zbiera samo
        pois.collectError = AppException("POI_TOO_FAR", "daleko")
        vm.collect(1)
        assertNotNull(vm.ui.value.message)
    }

    @Test
    fun bladPobieraniaNieWywracaIPozwalaSprobowacPonownie() {
        val pois = FakePois(listOf(gate), failLoad = true)
        val vm = viewModel(pois)
        vm.onCameraIdle(viewport)
        assertTrue(vm.ui.value.pois.isEmpty())
        pois.failLoad = false
        vm.onCameraIdle(viewport) // poprzedni obszar nie został zapamiętany jako pobrany
        assertEquals(1, vm.ui.value.pois.size)
    }

    @Test
    fun refreshPobieraObszarOdNowa() {
        val pois = FakePois(listOf(gate))
        val vm = viewModel(pois)
        vm.onCameraIdle(viewport)
        pois.items = listOf(gate, far)
        vm.refresh()
        assertEquals(2, vm.ui.value.pois.size)
    }

    private class FakeFocus : PoiFocusController {
        private val _request = MutableStateFlow<Poi?>(null)
        override val request: StateFlow<Poi?> = _request
        override fun focus(poi: Poi) {
            _request.value = poi
        }

        override fun consume() {
            _request.value = null
        }
    }

    @Test
    fun zobaczNaMapieDokladaPunktIProsiOPrzejscieKamery() {
        val focus = FakeFocus()
        val vm = viewModel(FakePois(emptyList()), focus = focus)
        focus.focus(far)
        val state = vm.ui.value
        assertEquals(listOf(2L), state.pois.map { it.id })
        assertEquals(2L, state.focusId)
        assertTrue(state.focusPending)
        assertNull(state.selectedId) // okno się nie otwiera
        assertNull(focus.request.value) // prośba odebrana
    }

    @Test
    fun poPrzejsciuKameryPunktZostajeWyrozniony() {
        val focus = FakeFocus()
        val vm = viewModel(FakePois(emptyList()), focus = focus)
        focus.focus(far)
        vm.focusShown()
        assertFalse(vm.ui.value.focusPending)
        assertEquals(2L, vm.ui.value.focusId)
    }

    @Test
    fun wyborInnegoPunktuKasujeWyroznienie() {
        val focus = FakeFocus()
        val vm = viewModel(FakePois(listOf(gate)), focus = focus)
        focus.focus(far)
        vm.select(1)
        assertNull(vm.ui.value.focusId)
        assertEquals(1L, vm.ui.value.selectedId)
    }

    @Test
    fun zobaczNaMapieWlaczaWylaczonaWarstwePunktow() {
        val focus = FakeFocus()
        val vm = viewModel(FakePois(emptyList()), focus = focus)
        vm.toggleEnabled()
        assertFalse(vm.ui.value.enabled)
        focus.focus(far)
        assertTrue(vm.ui.value.enabled)
    }

    private class FakeLocation : UserLocationSource {
        val position = MutableStateFlow<LatLng?>(null)
        override val location: StateFlow<LatLng?> = position
        override fun setWatching(watching: Boolean) = Unit
    }

    private class FakePois(
        var items: List<Poi>,
        private val alreadyCollected: Set<Long> = emptySet(),
        var collectError: AppException? = null,
        var failLoad: Boolean = false,
    ) : PoiRepository {
        val boxes = mutableListOf<BoundingBox>()
        val collected = mutableListOf<Long>()

        override suspend fun inBounds(box: BoundingBox): List<Poi> {
            if (failLoad) throw AppException("NETWORK_ERROR", "offline")
            boxes += box
            return items.filter { it.latitude in box.minLat..box.maxLat && it.longitude in box.minLng..box.maxLng }
        }

        override suspend fun propose(draft: PoiDraft): PoiAdmin = error("nieużywane")

        override suspend fun collect(poiId: Long, position: LatLng): PoiCollectionEntry {
            collectError?.let { throw it }
            collected += poiId
            return PoiCollectionEntry(items.first { it.id == poiId }, Instant.EPOCH)
        }

        override suspend fun myCollection() = items.filter { it.id in alreadyCollected }.map { PoiCollectionEntry(it, Instant.EPOCH) }
    }
}
