package com.trailify.feature.map

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trailify.core.domain.model.Activity
import com.trailify.core.domain.model.RecordingState
import com.trailify.core.domain.model.RecordingStatus
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

data class SummaryUiState(
    /** Zakończone nagranie, które podsumowujemy (zostaje na ekranie także po zapisie). */
    val recording: RecordingState? = null,
    val saving: Boolean = false,
    val error: String? = null,
    /** Aktywność zapisana na serwerze. */
    val saved: Activity? = null,
    /** Nagranie odrzucone: ekran powinien się zamknąć. */
    val discarded: Boolean = false,
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
        _ui.update { it.copy(discarded = true, recording = null) }
    }
}
