package com.trailify.feature.community.poi

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
import com.trailify.core.ui.DEFAULT_MAP_CENTER
import com.trailify.core.ui.UiState
import com.trailify.core.ui.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CommunityView { MAP, LIST }

data class CommunityUiState(
    val pois: UiState<List<Poi>> = UiState.Loading,
    val collectedIds: Set<Long> = emptySet(),
    val position: LatLng? = null,
    val view: CommunityView = CommunityView.MAP,
    val selectedPoiId: Long? = null,
    /** Komunikat na dole ekranu, np. "Zebrano: Brama Krakowska". */
    val message: String? = null,
)

/**
 * Zakładka Społeczność (W4, W22-W24): ciekawe punkty w okolicy na mapie i liście, odległość do nich
 * i automatyczne zbieranie, gdy użytkownik znajdzie się w promieniu 50 m (funkcja nearbyPois).
 * Serwer jeszcze raz sprawdza odległość, więc zebranie "z kanapy" się nie uda.
 */
class CommunityViewModel(
    private val pois: PoiRepository,
    private val location: UserLocationSource,
) : ViewModel() {

    private val _ui = MutableStateFlow(CommunityUiState())
    val ui: StateFlow<CommunityUiState> = _ui.asStateFlow()

    /** Środek obszaru, dla którego pobraliśmy punkty (pobieramy ponownie po oddaleniu się). */
    private var loadedCenter: LatLng? = null

    /** Punkty, które właśnie zbieramy (żeby nie wysłać dwóch żądań naraz). */
    private val collecting = mutableSetOf<Long>()

    init {
        viewModelScope.launch {
            location.location.collect { position -> if (position != null) onPosition(position) }
        }
        load()
    }

    /** Ekran widoczny: włącza GPS (bez zgody na lokalizację nic się nie dzieje). */
    fun setWatching(watching: Boolean) = location.setWatching(watching)

    fun load() {
        val center = _ui.value.position ?: DEFAULT_MAP_CENTER
        loadedCenter = center
        viewModelScope.launch {
            if (_ui.value.pois !is UiState.Success) _ui.update { it.copy(pois = UiState.Loading) }
            try {
                val found = pois.inBounds(boxAround(center))
                val collected = runCatching { pois.myCollection().map { it.poi.id }.toSet() }.getOrDefault(_ui.value.collectedIds)
                _ui.update { it.copy(pois = UiState.Success(found), collectedIds = collected) }
                _ui.value.position?.let(::collectNearby)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Pobieranie punktów nie powiodło się", e)
                _ui.update { it.copy(pois = UiState.Error(e.toUserMessage())) }
            }
        }
    }

    fun onViewChange(view: CommunityView) = _ui.update { it.copy(view = view) }

    fun onPoiSelected(id: Long?) = _ui.update { it.copy(selectedPoiId = id) }

    fun dismissMessage() = _ui.update { it.copy(message = null) }

    /** Ręczne zebranie (przycisk), np. gdy automatyczne nie zadziałało przez chwilowy brak sieci. */
    fun collect(poiId: Long) {
        val poi = (_ui.value.pois as? UiState.Success)?.data?.firstOrNull { it.id == poiId } ?: return
        val position = _ui.value.position ?: return
        collectOne(poi, position, quiet = false)
    }

    private fun onPosition(position: LatLng) {
        _ui.update { it.copy(position = position) }
        val center = loadedCenter
        // pierwsza pozycja albo oddalenie od pobranego obszaru: pobieramy punkty dla nowej okolicy
        if (center == null || center == DEFAULT_MAP_CENTER || distanceMeters(center, position) > RELOAD_DISTANCE_METERS) {
            load()
        } else {
            collectNearby(position)
        }
    }

    private fun collectNearby(position: LatLng) {
        val all = (_ui.value.pois as? UiState.Success)?.data ?: return
        poisToCollect(position, all, _ui.value.collectedIds + collecting).forEach { collectOne(it, position, quiet = true) }
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
        const val TAG = "Community"
        const val CODE_POI_TOO_FAR = "POI_TOO_FAR"
        const val CODE_ALREADY_COLLECTED = "ALREADY_COLLECTED"

        /** Pół boku pobieranego obszaru w stopniach (ok. 17 km na szerokość, 11 km na długość w Polsce). */
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
