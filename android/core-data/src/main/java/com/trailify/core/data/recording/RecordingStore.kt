package com.trailify.core.data.recording

import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.trailify.core.domain.model.ActivityType
import com.trailify.core.domain.model.LatLng
import com.trailify.core.domain.model.RecordingError
import com.trailify.core.domain.model.RecordingState
import com.trailify.core.domain.model.RecordingStatus
import com.trailify.core.domain.model.TrackPoint
import com.trailify.core.domain.recording.CandidateKind
import com.trailify.core.domain.recording.FixFilter
import com.trailify.core.domain.recording.GapContext
import com.trailify.core.domain.recording.GapInfo
import com.trailify.core.domain.recording.GapReconstructor
import com.trailify.core.domain.recording.GapRules
import com.trailify.core.domain.recording.GpsGap
import com.trailify.core.domain.recording.ManualGapResult
import com.trailify.core.domain.recording.PaceProfile
import com.trailify.core.domain.recording.StrideEstimator
import com.trailify.core.domain.recording.TrackRecorder
import com.trailify.core.domain.repository.RecordingController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors

/**
 * Jedno miejsce, w którym żyje stan nagrywania. Dzielą je ekrany (przez [RecordingController])
 * i usługa [RecordingService] (która dostarcza punkty GPS i odmierza czas).
 *
 * Każda zmiana jest też zapisywana w lokalnej bazie ([RecordingDatabase]), a przy starcie aplikacji
 * nagranie jest z niej przywracane. Zapisy idą przez jeden wątek w tle, więc kolejność jest zachowana,
 * a główny wątek nie czeka na dysk.
 *
 * Stan zmienia się tylko na głównym wątku (lokalizacja i licznik też są odbierane na nim),
 * więc nie potrzeba blokad.
 *
 * Gdy po dłuższej utracie GPS wraca sygnał, luka jest od razu wypełniana linią prostą (oznaczoną jako
 * odtworzona), a w tle oceniane są możliwe przebiegi trasy (po drogach, także z objazdami), dopasowane do
 * tempa użytkownika i kroków z krokomierza. Najbardziej prawdopodobny zastępuje linię prostą ([fillGap]).
 */
class RecordingStore(
    private val context: Context,
    private val reconstructor: GapReconstructor,
    private val paceStore: PaceProfileStore,
) : RecordingController {

    private val _state = MutableStateFlow(RecordingState())
    override val state: StateFlow<RecordingState> = _state.asStateFlow()

    private val database = RecordingDatabase(context)
    private val disk = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var recorder = TrackRecorder()
    private var elapsedMs = 0L
    private var lastTickMs = 0L
    private var ticksSincePersist = 0

    // do rekonstrukcji luk: kroki przy ostatnim zapisanym punkcie i długość kroku z odcinków z dobrym GPS
    private var stepsAtLastPoint: Int? = null
    private var stride = StrideEstimator()

    // pomija stare i mało dokładne odczyty zaraz po starcie, wznowieniu i powrocie sygnału GPS
    private val fixFilter = FixFilter()

    // tempo użytkownika z dotychczasowych tras (pamiętane między nagraniami), uczy się też w trakcie nagrania
    private var pace = PaceProfile()

    // luki w sygnale GPS z propozycjami przebiegu (tylko w pamięci; po restarcie aplikacji zostają same punkty)
    private val gapInfos = ArrayList<GapInfo>()
    private var nextGapId = 1

    init {
        // Odczyt z dysku w tle; wynik trafia na główny wątek.
        disk.execute {
            val saved = try {
                database.load()
            } catch (e: Exception) {
                Log.e(TAG, "Nie udało się odczytać zapisanego nagrania", e)
                null
            }
            if (saved != null) main.post { restore(saved) }
        }
    }

    /**
     * Przywraca nagranie po restarcie aplikacji. Nagranie, które trwało, wraca jako wstrzymane:
     * użytkownik sam decyduje, czy wznowić, czy zakończyć (nie wiemy, co działo się w międzyczasie).
     */
    private fun restore(saved: SavedRecording) {
        if (_state.value.status != RecordingStatus.IDLE) return // użytkownik zdążył już coś zacząć
        val wasActive = saved.status == "RECORDING" || saved.status == "PAUSED"
        val status = if (wasActive) RecordingStatus.PAUSED else RecordingStatus.FINISHED
        recorder = TrackRecorder().also { it.restore(saved.points, saved.distanceMeters) }
        pace = paceStore.load(runCatching { ActivityType.valueOf(saved.type) }.getOrDefault(ActivityType.WALKING))
        gapInfos.clear()
        elapsedMs = saved.elapsedMs
        _state.value = RecordingState(
            status = status,
            type = runCatching { ActivityType.valueOf(saved.type) }.getOrDefault(ActivityType.WALKING),
            points = saved.points,
            distanceMeters = saved.distanceMeters,
            elapsedSeconds = saved.elapsedMs / 1000,
            steps = saved.steps,
            startedAt = Instant.ofEpochMilli(saved.startedAtMs),
            finishedAt = if (status == RecordingStatus.FINISHED) {
                Instant.ofEpochMilli(saved.finishedAtMs ?: saved.startedAtMs)
            } else {
                null
            },
            clientId = saved.clientId,
            restored = wasActive,
            routeId = saved.routeId,
        )
        if (wasActive) persistSession()
    }

    override fun start(type: ActivityType, routeId: Long?) {
        if (_state.value.isActive) return
        recorder = TrackRecorder()
        stride = StrideEstimator()
        pace = paceStore.load(type)
        fixFilter.reset()
        stepsAtLastPoint = null
        gapInfos.clear()
        nextGapId = 1
        elapsedMs = 0
        ticksSincePersist = 0
        lastTickMs = SystemClock.elapsedRealtime()
        val clientId = UUID.randomUUID().toString()
        val startedAt = Instant.now()
        _state.value = RecordingState(
            status = RecordingStatus.RECORDING,
            type = type,
            startedAt = startedAt,
            clientId = clientId,
            routeId = routeId,
        )
        disk.execute { database.begin(clientId, type.name, startedAt.toEpochMilli(), routeId) }
        if (!ensureServiceRunning()) {
            _state.value = RecordingState(type = type, error = RecordingError.START_FAILED)
            disk.execute { database.clear() }
        }
    }

    override fun pause() {
        if (_state.value.status != RecordingStatus.RECORDING) return
        tick()
        recorder.breakSegment()
        paceStore.save(_state.value.type, pace)
        _state.update { it.copy(status = RecordingStatus.PAUSED) }
        persistSession()
    }

    override fun resume() {
        if (_state.value.status != RecordingStatus.PAUSED) return
        lastTickMs = SystemClock.elapsedRealtime()
        fixFilter.reset()
        _state.update { it.copy(status = RecordingStatus.RECORDING, restored = false) }
        // Po przywróceniu nagrania po restarcie aplikacji usługa nie działa, więc trzeba ją uruchomić.
        if (!ensureServiceRunning()) {
            _state.update { it.copy(status = RecordingStatus.PAUSED, error = RecordingError.START_FAILED) }
        }
        persistSession()
    }

    override fun stop() {
        if (!_state.value.isActive) return
        tick()
        paceStore.save(_state.value.type, pace)
        _state.update { it.copy(status = RecordingStatus.FINISHED, finishedAt = Instant.now(), restored = false) }
        persistSession()
    }

    override fun discard() {
        recorder = TrackRecorder()
        stride = StrideEstimator()
        stepsAtLastPoint = null
        gapInfos.clear()
        nextGapId = 1
        elapsedMs = 0
        _state.value = RecordingState()
        disk.execute { database.clear() }
    }

    /** Wołane co sekundę przez usługę: dolicza upłynięty czas (tylko gdy trwa nagrywanie). */
    fun tick() {
        if (_state.value.status != RecordingStatus.RECORDING) return
        val now = SystemClock.elapsedRealtime()
        elapsedMs += now - lastTickMs
        lastTickMs = now
        _state.update { it.copy(elapsedSeconds = elapsedMs / 1000) }
        // Czas, dystans i kroki zapisujemy co 10 s (punkty GPS idą od razu).
        if (++ticksSincePersist >= PERSIST_EVERY_TICKS) persistSession()
    }

    /** Krokomierz działa: od teraz pokazujemy kroki (zaczynamy od 0, po pauzie zostaje poprzednia suma). */
    fun enableSteps() {
        _state.update { it.copy(steps = it.steps ?: 0) }
    }

    /** Nowe kroki z czujnika (wołane przez usługę). */
    fun onSteps(count: Int) {
        if (_state.value.status != RecordingStatus.RECORDING) return
        _state.update { it.copy(steps = (it.steps ?: 0) + count) }
    }

    /** Nowa pozycja z GPS (wołane przez usługę). */
    fun onLocation(location: Location) {
        if (_state.value.status != RecordingStatus.RECORDING) return
        val accuracy = if (location.hasAccuracy()) location.accuracy else null
        val ageMs = (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000
        if (!fixFilter.accept(SystemClock.elapsedRealtime(), ageMs, accuracy)) {
            Log.d(TAG, "Pominięto odczyt GPS (rozgrzewka): wiek ${ageMs} ms, dokładność $accuracy m")
            return
        }
        val point = TrackPoint(
            latitude = location.latitude,
            longitude = location.longitude,
            altitude = if (location.hasAltitude()) location.altitude else null,
            speed = if (location.hasSpeed()) location.speed.toDouble() else null,
            recordedAt = Instant.ofEpochMilli(location.time),
        )
        val stepsNow = _state.value.steps
        val stepsBefore = stepsAtLastPoint
        val distanceBefore = recorder.distanceMeters
        if (recorder.add(point, accuracy)) {
            val stepDistance = recorder.distanceMeters - distanceBefore
            _state.update { it.copy(points = recorder.snapshot(), distanceMeters = recorder.distanceMeters) }
            disk.execute { database.appendPoint(point) }
            stepsAtLastPoint = stepsNow

            val stepsSince = if (stepsNow != null && stepsBefore != null && stepsNow >= stepsBefore) {
                stepsNow - stepsBefore
            } else {
                null
            }
            val gap = recorder.takeGap()
            if (gap != null) {
                Log.i(TAG, "Wykryto lukę GPS: ${gap.seconds} s, ${gap.straightMeters.toInt()} m w linii prostej")
            }
            if (gap == null) {
                // zwykły odcinek z działającym GPS (bez postoju w środku): uczymy się długości kroku
                val points = _state.value.points
                val seconds = if (points.size >= 2) {
                    (point.recordedAt.toEpochMilli() - points[points.size - 2].recordedAt.toEpochMilli()) / 1000.0
                } else {
                    Double.MAX_VALUE
                }
                if (seconds <= GapRules.MAX_PAIR_SECONDS) {
                    pace.update(_state.value.type, stepDistance, seconds)
                    if (stepsSince != null) stride.update(stepDistance, stepsSince)
                }
            } else if (reconstructor.isReconstructable(gap, _state.value.type)) {
                fillGap(gap, stepsSince)
            }
        }
    }

    /**
     * Wypełnia lukę po utracie GPS. Najpierw od razu linią prostą (żeby na mapie nie było dziury i
     * dystans był sensowny), potem oceniane są możliwe przebiegi i najbardziej prawdopodobny zastępuje linię
     * prostą. Wszystkie propozycje zostają w [GapInfo], żeby użytkownik mógł je zmienić w podsumowaniu.
     */
    private fun fillGap(gap: GpsGap, stepsInGap: Int?) {
        val rec = recorder
        val type = _state.value.type
        applyGap(rec, gap, reconstructor.straightLine(gap))
        val context = GapContext.build(rec.snapshot(), gap, type, stepsInGap, stride.strideMeters, pace.speedMps)
        scope.launch {
            val ranked = withTimeoutOrNull(ROUTING_TIMEOUT_MS) { reconstructor.rank(gap, type, context) }
                ?: listOf(reconstructor.straightCandidate(gap))
            // w międzyczasie nagranie mogło zostać odrzucone albo zastąpione nowym
            if (rec !== recorder) return@launch
            val best = ranked.firstOrNull() ?: return@launch
            // linia prosta już jest wstawiona
            if (best.kind != CandidateKind.STRAIGHT) applyGap(rec, gap, best.points)
            gapInfos.add(GapInfo(nextGapId++, gap, ranked, selected = 0, priorSpeedMps = context.priorSpeedMps))
            publishGaps()
        }
    }

    /** Wstawia punkty odtworzone w lukę (w pamięci, w bazie i w stanie dla ekranów). Zwraca false, gdy luki już nie ma. */
    private fun applyGap(rec: TrackRecorder, gap: GpsGap, inner: List<TrackPoint>): Boolean {
        if (!rec.replaceGap(gap, inner)) return false
        _state.update { it.copy(points = rec.snapshot(), distanceMeters = rec.distanceMeters) }
        val fromMs = gap.from.recordedAt.toEpochMilli()
        val toMs = gap.to.recordedAt.toEpochMilli()
        disk.execute { database.replaceReconstructed(fromMs, toMs, inner) }
        persistSession()
        return true
    }

    private fun publishGaps() {
        val snapshot = gapInfos.toList()
        _state.update { it.copy(gaps = snapshot) }
    }

    override fun selectGapCandidate(gapId: Int, index: Int) {
        if (_state.value.status != RecordingStatus.FINISHED) return
        val position = gapInfos.indexOfFirst { it.id == gapId }
        if (position < 0) return
        val info = gapInfos[position]
        val candidate = info.candidates.getOrNull(index) ?: return
        if (!applyGap(recorder, info.gap, candidate.points)) return
        gapInfos[position] = info.copy(selected = index, manualPoints = emptyList())
        publishGaps()
    }

    override suspend fun setManualGapRoute(gapId: Int, waypoints: List<LatLng>): ManualGapResult {
        val state = _state.value
        if (state.status != RecordingStatus.FINISHED) return ManualGapResult.NOT_AVAILABLE
        val rec = recorder
        val info = gapInfos.firstOrNull { it.id == gapId } ?: return ManualGapResult.NOT_AVAILABLE
        val manual = reconstructor.manual(info.gap, state.type, waypoints, info.priorSpeedMps)
        if (manual.result != ManualGapResult.APPLIED) return manual.result
        // w czasie zapytania nagranie mogło zostać odrzucone
        val position = gapInfos.indexOfFirst { it.id == gapId }
        if (rec !== recorder || position < 0 || !applyGap(rec, info.gap, manual.points)) return ManualGapResult.NOT_AVAILABLE
        gapInfos[position] = gapInfos[position].copy(selected = null, manualPoints = manual.points)
        publishGaps()
        return ManualGapResult.APPLIED
    }

    /** Błąd, po którym nie da się nagrywać dalej (np. cofnięta zgoda na lokalizację). */
    fun onError(error: RecordingError) {
        val hadPoints = _state.value.points.isNotEmpty()
        _state.update {
            it.copy(
                // bez punktów nie ma czego zapisywać, z punktami zostawiamy to, co już mamy
                status = if (hadPoints) RecordingStatus.FINISHED else RecordingStatus.IDLE,
                finishedAt = Instant.now(),
                error = error,
            )
        }
        if (hadPoints) persistSession() else disk.execute { database.clear() }
    }

    /** Zapisuje w bazie bieżące wartości nagrania (status, czas, dystans, kroki). */
    private fun persistSession() {
        ticksSincePersist = 0
        val s = _state.value
        val elapsed = elapsedMs
        disk.execute {
            database.updateSession(
                status = s.status.name,
                elapsedMs = elapsed,
                distanceMeters = s.distanceMeters,
                steps = s.steps,
                finishedAtMs = s.finishedAt?.toEpochMilli(),
            )
        }
    }

    private fun ensureServiceRunning(): Boolean = try {
        ContextCompat.startForegroundService(context, Intent(context, RecordingService::class.java))
        true
    } catch (e: Exception) {
        Log.e(TAG, "Nie udało się uruchomić usługi nagrywania", e)
        false
    }

    private companion object {
        const val TAG = "RecordingStore"
        const val PERSIST_EVERY_TICKS = 10
        const val ROUTING_TIMEOUT_MS = 25_000L
    }
}
