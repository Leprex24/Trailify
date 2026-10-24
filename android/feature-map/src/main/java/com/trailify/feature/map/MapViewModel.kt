package com.trailify.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trailify.core.domain.model.BoundingBox
import com.trailify.core.domain.model.LatLng
import com.trailify.core.domain.model.MapLayerType
import com.trailify.core.domain.repository.MapLayerRepository
import com.trailify.core.domain.repository.RecordingController
import com.trailify.core.domain.repository.UserLocationSource
import com.trailify.core.domain.model.ActivityType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class MapUiState(
    val enabled: Set<MapLayerType> = emptySet(),
    val lines: Map<MapLayerType, List<List<LatLng>>> = emptyMap(),
    val loading: Boolean = false,
    val hidden: Set<MapLayerType> = emptySet(),
    val errors: Map<MapLayerType, String> = emptyMap(),
    val retrying: Set<MapLayerType> = emptySet(),
) {
    val error: String? get() = errors.values.firstOrNull()
    val isRetrying: Boolean get() = retrying.isNotEmpty()
}

/**
 * Pilnuje, które warstwy są włączone, i ładuje brakujące kafelki dla widocznego fragmentu mapy.
 *
 * Zasady:
 *  - każda warstwa ma minimalne przybliżenie ([minZoom]); poniżej niego jest ukryta i nic się nie pobiera,
 *  - powyżej warstwa jest widoczna, a brakujące kafelki dociągają się w tle,
 *  - pobrane kafelki są pamiętane, więc powrót w to samo miejsce jest natychmiastowy.
 */
class MapViewModel(
    private val layers: MapLayerRepository,
    private val recording: RecordingController,
    private val location: UserLocationSource,
) : ViewModel() {

    // --- pozycja użytkownika i nagrywanie (ekran START jest też ekranem nagrywania) ---

    /** Niebieska kropka: pozycja użytkownika, null dopóki nie ma sygnału GPS. */
    val userLocation: StateFlow<LatLng?> = location.location

    val recordingState = recording.state

    /** Czy kamera podąża za pozycją. Przesunięcie mapy palcem to wyłącza, przycisk "Moja pozycja" włącza. */
    var following: Boolean = true

    /** Czy mapa już raz wycentrowała się na użytkowniku (robi to tylko przy pierwszym sygnale GPS). */
    var centeredOnUser: Boolean = false

    /** Czy już pytaliśmy o zgody (żeby nie nagabywać przy każdym wejściu na mapę). */
    var askedForLocation: Boolean = false
    var askedForNotifications: Boolean = false
    var askedForActivityRecognition: Boolean = false

    /** Ostatni widok mapy, żeby po powrocie z innej zakładki mapa nie skakała do Lublina. */
    var lastCameraTarget: LatLng? = null
    var lastCameraZoom: Double = 0.0

    fun setWatching(watching: Boolean) = location.setWatching(watching)

    fun startRecording(type: ActivityType) {
        following = true
        recording.start(type)
    }

    fun pauseRecording() = recording.pause()
    fun resumeRecording() = recording.resume()
    fun stopRecording() = recording.stop()
    fun discardRecording() = recording.discard()

    // --- warstwy mapy ---

    private val _state = MutableStateFlow(MapUiState())
    val state: StateFlow<MapUiState> = _state.asStateFlow()

    private var viewportBox: BoundingBox? = null
    private var viewportZoom: Double = 0.0
    private val caches = MapLayerType.entries.associateWith { LinkedHashMap<TileKey, List<List<LatLng>>>() }
    private val jobs = mutableMapOf<MapLayerType, Job>()
    private var activeLoads = 0

    // Serwery Overpass dopuszczają kilka zapytań naraz z jednego adresu, więc pobieramy po dwa.
    private val permits = Semaphore(MAX_PARALLEL_REQUESTS)

    fun toggleLayer(type: MapLayerType) {
        if (type in _state.value.enabled) {
            jobs.remove(type)?.cancel()
            _state.update {
                it.copy(
                    enabled = it.enabled - type,
                    lines = it.lines - type,
                    errors = it.errors - type,
                    retrying = it.retrying - type,
                    hidden = it.hidden - type,
                )
            }
        } else {
            _state.update { it.copy(enabled = it.enabled + type) }
            if (viewportBox != null && viewportZoom < type.minZoom()) {
                _state.update { it.copy(hidden = it.hidden + type) }
            } else {
                refresh(type, debounceMs = 0)
            }
        }
    }

    /** Ponawia pobieranie po błędzie. */
    fun retry() {
        _state.update { it.copy(errors = emptyMap(), retrying = emptySet()) }
        _state.value.enabled.forEach { refresh(it, debounceMs = 0) }
    }

    /** Wywoływane, gdy użytkownik skończył przesuwać lub przybliżać mapę. */
    fun onCameraIdle(box: BoundingBox, zoom: Double) {
        viewportBox = box
        viewportZoom = zoom
        val enabled = _state.value.enabled
        val hidden = enabled.filter { zoom < it.minZoom() }.toSet()
        _state.update { it.copy(hidden = hidden) }
        for (type in enabled) {
            if (type in hidden) {
                // Zbyt duże oddalenie dla tej warstwy: ukrywamy ją i przerywamy pobieranie, żeby stan był spójny.
                jobs.remove(type)?.cancel()
                _state.update { it.copy(lines = it.lines - type, retrying = it.retrying - type) }
            } else {
                refresh(type, debounceMs = DEBOUNCE_MS)
            }
        }
    }

    private fun refresh(type: MapLayerType, debounceMs: Long) {
        val box = viewportBox ?: return
        if (viewportZoom < type.minZoom()) return

        publish(type) // od razu pokaż to, co już mamy
        val cache = caches.getValue(type)
        // Gęsta warstwa ścieżek ma zawsze małe kafelki (działa tylko z bliska).
        val level = if (type == MapLayerType.FOREST_PATHS) 0 else levelFor(viewportZoom)
        val wanted = tilesFor(box.expanded(PREFETCH_FRACTION), level, MAX_TILES)
        val missing = wanted.filter { it !in cache }
        jobs[type]?.cancel()
        if (missing.isEmpty()) return

        jobs[type] = viewModelScope.launch {
            // Krótkie oczekiwanie: jeśli użytkownik od razu przesunie mapę dalej, to zapytanie zostanie anulowane.
            if (debounceMs > 0) delay(debounceMs)
            activeLoads++
            _state.update { it.copy(loading = true) }
            try {
                var pending = missing
                var attempt = 0
                while (pending.isNotEmpty()) {
                    val failed = loadTiles(type, pending)
                    if (failed.isEmpty()) break // wszystko pobrane

                    pending = failed
                    if (attempt >= RETRY_DELAYS_MS.size) {
                        _state.update {
                            it.copy(
                                errors = it.errors + (type to "Serwery map nie odpowiadają. Spróbuj ponownie za chwilę."),
                                retrying = it.retrying - type,
                            )
                        }
                        return@launch
                    }
                    // Samo ponawiamy z rosnącą przerwą: 2, 4, 8, 15 i 30 sekund.
                    _state.update { it.copy(retrying = it.retrying + type) }
                    delay(RETRY_DELAYS_MS[attempt])
                    attempt++
                }
                _state.update { it.copy(errors = it.errors - type, retrying = it.retrying - type) }
            } finally {
                activeLoads--
                _state.update { it.copy(loading = activeLoads > 0) }
            }
        }
    }

    /**
     * Pobiera kafelki (po dwa naraz) i zwraca te, których nie udało się pobrać.
     * Przy pierwszej porażce reszta kolejki jest pomijana: serwery najpewniej są chwilowo niedostępne.
     */
    private suspend fun loadTiles(type: MapLayerType, tiles: List<TileKey>): List<TileKey> = coroutineScope {
        val cache = caches.getValue(type)
        var abort = false
        tiles.map { tile ->
            async {
                permits.withPermit {
                    if (abort) {
                        tile
                    } else {
                        try {
                            cache[tile] = layers.load(type, tile.box())
                            trim(cache)
                            publish(type)
                            null
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            abort = true
                            tile
                        }
                    }
                }
            }
        }.awaitAll().filterNotNull()
    }

    /** Ustawia na mapie linie z pobranych kafelków leżących w pobliżu widocznego obszaru. */
    private fun publish(type: MapLayerType) {
        if (type !in _state.value.enabled) return
        if (viewportZoom < type.minZoom()) return
        val box = viewportBox ?: return
        val cache = caches.getValue(type)
        val area = box.expanded(0.5)
        // Pokazujemy kafelki obu poziomów, żeby przy przybliżaniu i oddalaniu nic nie znikało w trakcie ładowania.
        val lines = LEVEL_SCALE.indices.flatMap { level ->
            tilesFor(area, level, MAX_DISPLAYED_TILES).flatMap { cache[it].orEmpty() }
        }
        _state.update { it.copy(lines = it.lines + (type to lines)) }
    }

    private fun trim(cache: LinkedHashMap<TileKey, List<List<LatLng>>>) {
        while (cache.size > MAX_CACHED_TILES) {
            val oldest = cache.keys.iterator().next()
            cache.remove(oldest)
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 250L
        const val PREFETCH_FRACTION = 0.3
        const val MAX_TILES = 30
        const val MAX_DISPLAYED_TILES = 200
        const val MAX_CACHED_TILES = 60
        const val MAX_PARALLEL_REQUESTS = 2
        val RETRY_DELAYS_MS = listOf(2_000L, 4_000L, 8_000L, 15_000L, 30_000L)
    }
}
