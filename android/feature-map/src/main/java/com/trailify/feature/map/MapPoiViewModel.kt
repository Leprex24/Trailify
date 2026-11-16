package com.trailify.feature.map

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trailify.core.domain.model.AppException
import com.trailify.core.domain.model.BoundingBox
import com.trailify.core.domain.model.LatLng
import com.trailify.core.domain.model.Poi
import com.trailify.core.domain.poi.poisToCollect
import com.trailify.core.domain.recording.distanceMeters
import com.trailify.core.domain.repository.PoiRepository
import com.trailify.core.domain.repository.UserLocationSource
import com.trailify.core.ui.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MapPoiUiState(
    /** Warstwa "Ciekawe punkty" włączona w menu Warstwy. Wyłączenie chowa punkty, ale zbieranie działa dalej. */
    val enabled: Boolean = true,
    val pois: List<Poi> = emptyList(),
    val collectedIds: Set<Long> = emptySet(),
    val selectedId: Long? = null,
    /** Mapa jest zbyt oddalona, żeby pobierać punkty. */
    val zoomedOut: Boolean = false,
    /** Komunikat, np. "Zebrano punkt: Brama Krakowska". */
    val message: String? = null,
) {
    val selected: Poi? get() = pois.firstOrNull { it.id == selectedId }
}

/**
 * Ciekawe punkty na mapie START: pobiera punkty widocznego obszaru i okolicy użytkownika, pamięta zebrane
 * i zbiera punkt automatycznie, gdy użytkownik podejdzie na 50 m (serwer jeszcze raz sprawdza odległość).
 * ViewModel żyje razem z zakładką START, więc zbieranie działa też w trakcie nagrywania na innych zakładkach.
 */
class MapPoiViewModel(
    private val pois: PoiRepository,
    private val location: UserLocationSource,
) : ViewModel() {

    private val _ui = MutableStateFlow(MapPoiUiState())
    val ui: StateFlow<MapPoiUiState> = _ui.asStateFlow()

    private var viewport: BoundingBox? = null
    private var loadedViewportBox: BoundingBox? = null
    private var viewportJob: Job? = null

    /** Środek ostatnio pobranej okolicy użytkownika (po oddaleniu się pobieramy nową). */
    private var userAreaCenter: LatLng? = null
    private var lastPosition: LatLng? = null
    private var collectionLoaded = false

    /** Punkty, które właśnie zbieramy (żeby nie wysłać dwóch żądań naraz). */
    private val collecting = mutableSetOf<Long>()

    init {
        loadCollection()
        viewModelScope.launch {
            location.location.collect { position -> if (position != null) onPosition(position) }
        }
    }

    /** Mapa przestała się ruszać: pobieramy punkty widocznego obszaru, jeśli ich jeszcze nie mamy. */
    fun onCameraIdle(box: BoundingBox) {
        viewport = box
        if (!collectionLoaded) loadCollection()
        refreshViewport()
    }

    /** Mapa znów na ekranie: pobieramy punkty od nowa (np. administrator zatwierdził nowy punkt). */
    fun refresh() {
        loadedViewportBox = null
        if (!collectionLoaded) loadCollection()
        refreshViewport()
    }

    fun toggleEnabled() {
        val enabled = !_ui.value.enabled
        _ui.update { it.copy(enabled = enabled, selectedId = if (enabled) it.selectedId else null) }
        if (enabled) refreshViewport()
    }

    fun select(id: Long?) = _ui.update { it.copy(selectedId = id) }

    fun dismissMessage() = _ui.update { it.copy(message = null) }

    /** Ręczne zebranie (przycisk na karcie), np. gdy automatyczne nie zadziałało przez chwilowy brak sieci. */
    fun collect(poiId: Long) {
        val poi = _ui.value.pois.firstOrNull { it.id == poiId } ?: return
        val position = lastPosition ?: location.location.value ?: return
        collectOne(poi, position, quiet = false)
    }

    private fun refreshViewport() {
        val box = viewport ?: return
        if (!_ui.value.enabled) return
        val loadBox = poiLoadBox(box)
        _ui.update { it.copy(zoomedOut = loadBox == null) }
        if (loadBox == null) return
        if (loadedViewportBox?.contains(box) == true) return
        viewportJob?.cancel()
        viewportJob = viewModelScope.launch {
            if (fetch(loadBox)) loadedViewportBox = loadBox
        }
    }

    private fun onPosition(position: LatLng) {
        lastPosition = position
        val center = userAreaCenter
        if (center == null || distanceMeters(center, position) > RELOAD_DISTANCE_METERS) {
            userAreaCenter = position
            viewModelScope.launch { fetch(boxAround(position)) }
        } else {
            collectNearby()
        }
    }

    /** Pobiera punkty obszaru, łączy je ze znanymi i zbiera te w zasięgu. Zwraca true, gdy się udało. */
    private suspend fun fetch(box: BoundingBox): Boolean = try {
        val found = pois.inBounds(box)
        _ui.update { it.copy(pois = mergePois(it.pois, found, box)) }
        collectNearby()
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Pobieranie punktów nie powiodło się", e)
        false
    }

    private fun loadCollection() {
        viewModelScope.launch {
            try {
                val ids = pois.myCollection().map { it.poi.id }.toSet()
                collectionLoaded = true
                _ui.update { it.copy(collectedIds = it.collectedIds + ids) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Pobieranie zebranych punktów nie powiodło się", e)
            }
        }
    }

    private fun collectNearby() {
        val position = lastPosition ?: return
        val state = _ui.value
        poisToCollect(position, state.pois, state.collectedIds + collecting).forEach { collectOne(it, position, quiet = true) }
    }

    /** [quiet] = zbieranie automatyczne: "za daleko" nie jest błędem, tylko pomijamy punkt. */
    private fun collectOne(poi: Poi, position: LatLng, quiet: Boolean) {
        if (!collecting.add(poi.id)) return
        viewModelScope.launch {
            try {
                pois.collect(poi.id, position)
                _ui.update { it.copy(collectedIds = it.collectedIds + poi.id, message = "Zebrano punkt: ${poi.name}") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                when ((e as? AppException)?.code) {
                    CODE_ALREADY_COLLECTED -> _ui.update { it.copy(collectedIds = it.collectedIds + poi.id) }
                    CODE_POI_TOO_FAR -> if (!quiet) _ui.update { it.copy(message = e.toUserMessage()) }
                    else -> {
                        Log.w(TAG, "Zebranie punktu nie powiodło się", e)
                        if (!quiet) _ui.update { it.copy(message = e.toUserMessage()) }
                    }
                }
            } finally {
                collecting.remove(poi.id)
            }
        }
    }

    internal companion object {
        const val TAG = "MapPoi"
        const val CODE_POI_TOO_FAR = "POI_TOO_FAR"
        const val CODE_ALREADY_COLLECTED = "ALREADY_COLLECTED"

        /** Pół boku okolicy użytkownika w stopniach (ok. 17 km na szerokość, 11 km na długość w Polsce). */
        const val AREA_HALF_DEGREES = 0.15
        const val RELOAD_DISTANCE_METERS = 5_000.0

        fun boxAround(center: LatLng) = BoundingBox(
            minLat = center.latitude - AREA_HALF_DEGREES,
            minLng = center.longitude - AREA_HALF_DEGREES,
            maxLat = center.latitude + AREA_HALF_DEGREES,
            maxLng = center.longitude + AREA_HALF_DEGREES,
        )
    }
}
