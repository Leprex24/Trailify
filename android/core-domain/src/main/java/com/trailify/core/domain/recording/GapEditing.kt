package com.trailify.core.domain.recording

import com.trailify.core.domain.model.TrackPoint

/** Wynik ręcznego wyznaczenia przebiegu trasy w luce GPS. */
enum class ManualGapResult {
    APPLIED,

    /** Nie da się teraz poprawiać: nagranie nie jest zakończone albo luki już nie ma. */
    NOT_AVAILABLE,

    /** Trzeba od 1 do [GapRules.MAX_MANUAL_POINTS] punktów. */
    BAD_POINT_COUNT,

    /** Nie udało się poprowadzić trasy po drogach i ścieżkach przez zaznaczone punkty. */
    NO_ROUTE,

    /** Wyznaczona trasa wymagałaby nierealnego tempa w czasie trwania luki. */
    TOO_FAST,
}

/** Wynik [GapReconstructor.manual]: punkty odtworzone są wypełnione tylko przy [ManualGapResult.APPLIED]. */
class ManualRoute(val result: ManualGapResult, val points: List<TrackPoint> = emptyList())

/**
 * Luka w nagraniu razem ze wszystkimi propozycjami przebiegu, żeby użytkownik mógł w podsumowaniu zmienić wybór.
 *
 * @property selected indeks wybranej propozycji w [candidates]; null, gdy trasa została wyznaczona ręcznie
 * @property manualPoints punkty trasy wyznaczonej ręcznie (tylko gdy [selected] to null)
 * @property priorSpeedMps tempo sprzed luki, wg którego sprawdzamy realność ręcznie wyznaczonej trasy
 */
data class GapInfo(
    val id: Int,
    val gap: GpsGap,
    val candidates: List<GapCandidate>,
    val selected: Int?,
    val manualPoints: List<TrackPoint> = emptyList(),
    val priorSpeedMps: Double,
) {
    val isManual: Boolean get() = selected == null

    /** Punkty odtworzone, które są teraz wstawione w nagranie. */
    val currentPoints: List<TrackPoint> get() = selected?.let { candidates.getOrNull(it)?.points } ?: manualPoints
}
