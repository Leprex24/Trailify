package com.trailify.core.domain.model

import java.time.Instant

enum class RecordingStatus {
    /** Nic się nie nagrywa. */
    IDLE,
    RECORDING,
    PAUSED,

    /** Nagrywanie zakończone, dane czekają na zapis lub odrzucenie. */
    FINISHED,
}

enum class RecordingError {
    /** Brak zgody na dokładną lokalizację. */
    LOCATION_PERMISSION,

    /** Nie udało się uruchomić nagrywania (np. system nie pozwolił uruchomić usługi). */
    START_FAILED,
}

/** Stan bieżącego nagrywania, widoczny dla ekranów. */
data class RecordingState(
    val status: RecordingStatus = RecordingStatus.IDLE,
    val type: ActivityType = ActivityType.WALKING,
    val points: List<TrackPoint> = emptyList(),
    val distanceMeters: Double = 0.0,
    val elapsedSeconds: Long = 0,
    /** Liczba kroków z krokomierza; null, gdy niedostępny (brak czujnika albo zgody). */
    val steps: Int? = null,
    val startedAt: Instant? = null,
    val finishedAt: Instant? = null,
    val error: RecordingError? = null,
    /** Identyfikator nagrania (UUID z telefonu), chroni przed duplikatem przy wysyłaniu na serwer. */
    val clientId: String? = null,
    /** true, gdy nagranie przywrócono z pamięci telefonu po nieoczekiwanym zamknięciu aplikacji. */
    val restored: Boolean = false,
    /** Trasa opublikowana przez społeczność, którą użytkownik przechodzi (null = nagranie bez trasy). */
    val routeId: Long? = null,
) {
    /** true, gdy złapaliśmy już pierwszy sygnał GPS. */
    val hasFix: Boolean get() = points.isNotEmpty()

    val isActive: Boolean get() = status == RecordingStatus.RECORDING || status == RecordingStatus.PAUSED
}
