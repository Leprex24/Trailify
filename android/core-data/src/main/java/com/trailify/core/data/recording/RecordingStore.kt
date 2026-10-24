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
import com.trailify.core.domain.model.RecordingError
import com.trailify.core.domain.model.RecordingState
import com.trailify.core.domain.model.RecordingStatus
import com.trailify.core.domain.model.TrackPoint
import com.trailify.core.domain.recording.TrackRecorder
import com.trailify.core.domain.repository.RecordingController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
 */
class RecordingStore(private val context: Context) : RecordingController {

    private val _state = MutableStateFlow(RecordingState())
    override val state: StateFlow<RecordingState> = _state.asStateFlow()

    private val database = RecordingDatabase(context)
    private val disk = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    private var recorder = TrackRecorder()
    private var elapsedMs = 0L
    private var lastTickMs = 0L
    private var ticksSincePersist = 0

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
        )
        if (wasActive) persistSession()
    }

    override fun start(type: ActivityType) {
        if (_state.value.isActive) return
        recorder = TrackRecorder()
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
        )
        disk.execute { database.begin(clientId, type.name, startedAt.toEpochMilli()) }
        if (!ensureServiceRunning()) {
            _state.value = RecordingState(type = type, error = RecordingError.START_FAILED)
            disk.execute { database.clear() }
        }
    }

    override fun pause() {
        if (_state.value.status != RecordingStatus.RECORDING) return
        tick()
        recorder.breakSegment()
        _state.update { it.copy(status = RecordingStatus.PAUSED) }
        persistSession()
    }

    override fun resume() {
        if (_state.value.status != RecordingStatus.PAUSED) return
        lastTickMs = SystemClock.elapsedRealtime()
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
        _state.update { it.copy(status = RecordingStatus.FINISHED, finishedAt = Instant.now(), restored = false) }
        persistSession()
    }

    override fun discard() {
        recorder = TrackRecorder()
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
        val point = TrackPoint(
            latitude = location.latitude,
            longitude = location.longitude,
            altitude = if (location.hasAltitude()) location.altitude else null,
            speed = if (location.hasSpeed()) location.speed.toDouble() else null,
            recordedAt = Instant.ofEpochMilli(location.time),
        )
        val accuracy = if (location.hasAccuracy()) location.accuracy else null
        if (recorder.add(point, accuracy)) {
            _state.update { it.copy(points = recorder.snapshot(), distanceMeters = recorder.distanceMeters) }
            disk.execute { database.appendPoint(point) }
        }
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
    }
}
