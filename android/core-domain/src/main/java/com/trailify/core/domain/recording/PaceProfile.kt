package com.trailify.core.domain.recording

import com.trailify.core.domain.model.ActivityType

/**
 * Tempo użytkownika w ruchu, wyliczone z jego dotychczasowych tras. Jest osobne dla każdego rodzaju aktywności
 * i uczy się na bieżąco: do średniej wchodzą tylko zmierzone odcinki w ruchu (bez postojów i skoków GPS),
 * a gdy zebrana wiedza przekroczy [GapRules.PROFILE_MAX_SECONDS], starsze dane tracą na wadze o połowę.
 * Dzięki temu po utracie GPS na początku trasy, gdy świeżych danych jest mało, tempo nie jest zgadywane,
 * tylko wynika z tego, jak użytkownik zwykle się porusza.
 */
class PaceProfile(meters: Double = 0.0, seconds: Double = 0.0) {

    var meters: Double = meters
        private set
    var seconds: Double = seconds
        private set

    /** Dodaje zmierzony odcinek; pomija postoje, nierealne skoki i zbyt długie przerwy między punktami. */
    fun update(type: ActivityType, distanceMeters: Double, durationSeconds: Double) {
        if (durationSeconds <= 0.0 || durationSeconds > GapRules.MAX_PAIR_SECONDS) return
        val speed = distanceMeters / durationSeconds
        if (speed < GapRules.PROFILE_MIN_MOVING_MPS || speed > GapRules.maxSpeedMps(type)) return
        meters += distanceMeters
        seconds += durationSeconds
        if (seconds > GapRules.PROFILE_MAX_SECONDS) {
            meters /= 2
            seconds /= 2
        }
    }

    /** Typowa prędkość w ruchu (m/s); null, dopóki zebrano mniej niż [GapRules.PROFILE_MIN_SECONDS] ruchu. */
    val speedMps: Double? get() = if (seconds >= GapRules.PROFILE_MIN_SECONDS) meters / seconds else null
}
