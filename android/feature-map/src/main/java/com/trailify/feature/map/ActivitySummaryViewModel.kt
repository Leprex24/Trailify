package com.trailify.feature.map

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trailify.core.domain.model.Activity
import com.trailify.core.domain.model.LatLng
import com.trailify.core.domain.model.RecordingState
import com.trailify.core.domain.model.RecordingStatus
import com.trailify.core.domain.recording.GapRules
import com.trailify.core.domain.recording.ManualGapResult
import com.trailify.core.domain.recording.toActivityDraft
import com.trailify.core.domain.repository.ActivityRepository
import com.trailify.core.domain.repository.RecordingController
import com.trailify.core.ui.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Poprawianie fragmentu trasy odtworzonego po utracie GPS.
 *
 * @property previewIndex propozycja pokazana na mapie (indeks w propozycjach luki)
 * @property manual true w trybie ręcznego zaznaczania punktów na mapie
 * @property waypoints punkty zaznaczone ręcznie, po kolei od początku do końca luki
 * @property busy trwa wyznaczanie trasy przez zaznaczone punkty
 */
data class GapEditorState(
    val gapId: Int,
    val previewIndex: Int? = null,
    val manual: Boolean = false,
    val waypoints: List<LatLng> = emptyList(),
    val busy: Boolean = false,
    val error: String? = null,
)

data class SummaryUiState(
    /** Zakończone nagranie, które podsumowujemy (zostaje na ekranie także po zapisie). */
    val recording: RecordingState? = null,
    val saving: Boolean = false,
    val error: String? = null,
    /** Aktywność zapisana na serwerze. */
    val saved: Activity? = null,
    /** Nagranie odrzucone: ekran powinien się zamknąć. */
    val discarded: Boolean = false,
    /** Otwarty edytor luki GPS albo null. */
    val editor: GapEditorState? = null,
) {
    /** Czy nagranie ma dość punktów GPS, żeby zapisać trasę. */
    val canSave: Boolean get() = recording?.toActivityDraft() != null
}

/**
 * Podsumowanie po zakończeniu nagrania: pokazuje statystyki i pozwala zapisać aktywność na serwerze
 * albo ją odrzucić. Do czasu zapisu nagranie leży w pamięci telefonu, więc brak internetu niczego nie kasuje:
 * wystarczy spróbować ponownie.
 */
class ActivitySummaryViewModel(
    private val controller: RecordingController,
    private val activities: ActivityRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(SummaryUiState())
    val ui: StateFlow<SummaryUiState> = _ui.asStateFlow()

    init {
        // Nagranie bierzemy ze stanu na bieżąco: po restarcie aplikacji jest przywracane z dysku chwilę po starcie.
        viewModelScope.launch {
            controller.state.collect { state ->
                if (state.status == RecordingStatus.FINISHED) {
                    _ui.update { if (it.saved != null || it.discarded) it else it.copy(recording = state) }
                }
            }
        }
    }

    fun save() {
        val current = _ui.value
        if (current.saving || current.saved != null) return
        val draft = current.recording?.toActivityDraft() ?: return
        viewModelScope.launch {
            _ui.update { it.copy(saving = true, error = null) }
            try {
                val activity = activities.save(draft)
                // serwer ma już aktywność, lokalnej kopii nie potrzebujemy
                controller.discard()
                _ui.update { it.copy(saving = false, saved = activity) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("ActivitySummary", "Zapis aktywności nie powiódł się", e)
                _ui.update { it.copy(saving = false, error = e.toUserMessage()) }
            }
        }
    }

    fun discard() {
        controller.discard()
        _ui.update { it.copy(discarded = true, recording = null, editor = null) }
    }

    // --- poprawianie luk GPS (tylko przed zapisem) ---

    /** Otwiera edytor luki; od razu pokazuje na mapie aktualnie wybraną propozycję. */
    fun editGap(gapId: Int) {
        val current = _ui.value
        if (current.saved != null || current.saving) return
        val info = current.recording?.gaps?.firstOrNull { it.id == gapId } ?: return
        _ui.update { it.copy(editor = GapEditorState(gapId = gapId, previewIndex = info.selected)) }
    }

    fun closeEditor() {
        _ui.update { it.copy(editor = null) }
    }

    fun previewCandidate(index: Int) {
        _ui.update { state -> state.copy(editor = state.editor?.copy(previewIndex = index, error = null)) }
    }

    /** Zatwierdza podglądaną propozycję: wstawia ją w nagranie. */
    fun applyPreview() {
        val editor = _ui.value.editor ?: return
        val index = editor.previewIndex ?: return
        controller.selectGapCandidate(editor.gapId, index)
    }

    fun startManual() {
        _ui.update { state -> state.copy(editor = state.editor?.copy(manual = true, previewIndex = null, waypoints = emptyList(), error = null)) }
    }

    fun cancelManual() {
        _ui.update { state ->
            val info = state.recording?.gaps?.firstOrNull { it.id == state.editor?.gapId }
            state.copy(editor = state.editor?.copy(manual = false, waypoints = emptyList(), previewIndex = info?.selected, error = null))
        }
    }

    /** Dotknięcie mapy w trybie ręcznym dodaje punkt (do [GapRules.MAX_MANUAL_POINTS]). */
    fun addWaypoint(point: LatLng) {
        _ui.update { state ->
            val editor = state.editor
            if (editor == null || !editor.manual || editor.busy || editor.waypoints.size >= GapRules.MAX_MANUAL_POINTS) {
                state
            } else {
                state.copy(editor = editor.copy(waypoints = editor.waypoints + point, error = null))
            }
        }
    }

    fun undoWaypoint() {
        _ui.update { state ->
            val editor = state.editor
            if (editor == null || editor.busy) state else state.copy(editor = editor.copy(waypoints = editor.waypoints.dropLast(1), error = null))
        }
    }

    /** Wyznacza trasę po drogach przez zaznaczone punkty i, jeśli jest realna, wstawia ją w nagranie. */
    fun confirmManual() {
        val editor = _ui.value.editor ?: return
        if (!editor.manual || editor.busy || editor.waypoints.isEmpty()) return
        viewModelScope.launch {
            _ui.update { state -> state.copy(editor = state.editor?.copy(busy = true, error = null)) }
            val result = controller.setManualGapRoute(editor.gapId, editor.waypoints)
            _ui.update { state ->
                val current = state.editor
                when {
                    current == null -> state
                    result == ManualGapResult.APPLIED ->
                        state.copy(editor = current.copy(manual = false, waypoints = emptyList(), previewIndex = null, busy = false))
                    else -> state.copy(editor = current.copy(busy = false, error = result.message()))
                }
            }
        }
    }

    private fun ManualGapResult.message(): String = when (this) {
        ManualGapResult.APPLIED -> ""
        ManualGapResult.NOT_AVAILABLE -> "Tego fragmentu nie można już zmienić."
        ManualGapResult.BAD_POINT_COUNT -> "Zaznacz od 1 do ${GapRules.MAX_MANUAL_POINTS} punktów."
        ManualGapResult.NO_ROUTE -> "Nie udało się poprowadzić trasy przez te punkty. Sprawdź połączenie z internetem albo zmień punkty."
        ManualGapResult.TOO_FAST -> "Ta trasa jest za długa jak na czas bez GPS i Twoje tempo. Zaznacz punkty bliżej drogi, którą szedłeś."
    }
}
