package com.trailify.core.domain.repository

import com.trailify.core.domain.model.ActivityType
import com.trailify.core.domain.model.RecordingState
import kotlinx.coroutines.flow.StateFlow

/**
 * Sterowanie nagrywaniem aktywności (W10-W15). Samo nagrywanie działa w usłudze pierwszoplanowej,
 * więc trwa przy zablokowanym ekranie i po zamknięciu aplikacji z listy ostatnich.
 * Ekrany tylko wydają polecenia i obserwują [state].
 */
interface RecordingController {
    val state: StateFlow<RecordingState>

    /** Uprawnienie do lokalizacji musi być przyznane przed wywołaniem. */
    fun start(type: ActivityType)
    fun pause()
    fun resume()

    /** Kończy nagrywanie. Dane zostają w [state] (status FINISHED) do czasu [discard]. */
    fun stop()

    /** Usuwa dane nagrania i wraca do stanu początkowego. */
    fun discard()
}
