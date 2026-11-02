package com.trailify.core.domain.recording

import com.trailify.core.domain.model.ActivityType
import com.trailify.core.domain.model.TrackPoint

/** Typowe prędkości (m/s), gdy przed luką nie ma dość zmierzonych danych, żeby ocenić tempo użytkownika. */
fun defaultSpeedMps(type: ActivityType): Double = when (type) {
    ActivityType.WALKING -> 1.4
    ActivityType.RUNNING -> 2.8
    ActivityType.CYCLING -> 4.5
}

/**
 * Wiedza o użytkowniku tuż przed luką, na której opiera się ocena możliwych przebiegów trasy.
 *
 * @property priorSpeedMps tempo (m/s) z ostatnich minut przed utratą GPS, tylko ze zmierzonych odcinków w ruchu
 * @property entryHeadingDegrees kierunek ruchu tuż przed luką (0 = północ, 90 = wschód), null gdy nieznany
 * @property stepsInGap kroki z krokomierza w czasie luki (czujnik działa też bez GPS), null gdy niedostępne
 * @property strideMeters długość kroku użytkownika wyliczona z dobrych odcinków GPS, null gdy za mało danych
 */
data class GapContext(
    val priorSpeedMps: Double,
    val entryHeadingDegrees: Double? = null,
    val stepsInGap: Int? = null,
    val strideMeters: Double? = null,
) {
    companion object {
        /**
         * Buduje kontekst z punktów zapisanych do końca luki. [points] mogą zawierać też punkty już odtworzone
         * (np. linię prostą wstawioną chwilę wcześniej): do oceny tempa i kierunku liczą się tylko zmierzone.
         */
        fun build(
            points: List<TrackPoint>,
            gap: GpsGap,
            type: ActivityType,
            stepsInGap: Int? = null,
            strideMeters: Double? = null,
            profileSpeedMps: Double? = null,
        ): GapContext {
            val index = points.lastIndexOf(gap.from)
            val before = if (index >= 0) points.subList(0, index + 1) else emptyList()
            return GapContext(
                priorSpeedMps = priorSpeed(before, type, profileSpeedMps),
                entryHeadingDegrees = entryHeading(before),
                stepsInGap = stepsInGap,
                strideMeters = strideMeters,
            )
        }

        private fun secondsBetween(a: TrackPoint, b: TrackPoint): Double =
            (b.recordedAt.toEpochMilli() - a.recordedAt.toEpochMilli()) / 1000.0

        /**
         * Tempo przed luką: średnia prędkość z ostatnich [GapRules.PRIOR_WINDOW_SECONDS] sekund zmierzonych
         * odcinków (dystans podzielony przez ich czas), połączona z tempem z profilu użytkownika [profileSpeed].
         * Im mniej świeżych danych, tym większą wagę ma profil; bez świeżych danych tempo wynika z samego profilu,
         * a bez profilu z typowej prędkości dla aktywności. Odcinki z dużą przerwą w czasie (postój albo
         * wcześniejsza luka) pomijamy.
         */
        private fun priorSpeed(before: List<TrackPoint>, type: ActivityType, profileSpeed: Double?): Double {
            val end = before.lastOrNull() ?: return profileSpeed ?: defaultSpeedMps(type)
            val windowStart = end.recordedAt.minusSeconds(GapRules.PRIOR_WINDOW_SECONDS)
            var meters = 0.0
            var seconds = 0.0
            for (i in before.size - 1 downTo 1) {
                val p = before[i]
                val q = before[i - 1]
                if (p.recordedAt.isBefore(windowStart)) break
                if (p.isReconstructed || q.isReconstructed) continue
                val dt = secondsBetween(q, p)
                if (dt <= 0 || dt > GapRules.MAX_PAIR_SECONDS) continue
                meters += distanceMeters(q.position(), p.position())
                seconds += dt
            }
            val fallback = profileSpeed ?: defaultSpeedMps(type)
            if (seconds < GapRules.MIN_PRIOR_SECONDS) return fallback
            val recent = meters / seconds
            val speed = if (profileSpeed == null) {
                recent
            } else {
                val weight = seconds / (seconds + GapRules.PROFILE_PSEUDO_SECONDS)
                weight * recent + (1 - weight) * profileSpeed
            }
            return speed.coerceIn(GapRules.MIN_PRIOR_SPEED_MPS, GapRules.maxSpeedMps(type))
        }

        /** Kierunek ruchu z ostatnich kilkunastu metrów zmierzonej trasy przed luką. */
        private fun entryHeading(before: List<TrackPoint>): Double? {
            val end = before.lastOrNull() ?: return null
            var next = end
            for (i in before.size - 2 downTo 0) {
                val p = before[i]
                if (p.isReconstructed || secondsBetween(p, next) > GapRules.MAX_PAIR_SECONDS) return null
                if (distanceMeters(p.position(), end.position()) >= GapRules.HEADING_MIN_METERS) {
                    return bearingDegrees(p.position(), end.position())
                }
                next = p
            }
            return null
        }
    }
}

/**
 * Wylicza długość kroku użytkownika z odcinków, na których GPS działał: dystans podzielony przez kroki.
 * Dzięki temu kroki w czasie luki można przeliczyć na dystans. Nie zapamiętuje nic między nagraniami.
 */
class StrideEstimator {
    private var meters = 0.0
    private var steps = 0

    /** Dodaje odcinek zmierzony przez GPS i krokomierz; odcinki zbyt krótkie lub o nierealnym kroku są pomijane. */
    fun update(distanceMeters: Double, stepCount: Int) {
        if (stepCount < MIN_STEPS || distanceMeters < MIN_METERS) return
        val stride = distanceMeters / stepCount
        if (stride < MIN_STRIDE || stride > MAX_STRIDE) return
        meters += distanceMeters
        steps += stepCount
        // starsze odcinki ważą coraz mniej, bo długość kroku zmienia się z tempem
        if (steps > MAX_STEPS) {
            meters /= 2
            steps /= 2
        }
    }

    /** Średnia długość kroku w metrach; null, dopóki nie zebrano dość kroków. */
    val strideMeters: Double? get() = if (steps >= ENOUGH_STEPS) meters / steps else null

    private companion object {
        const val MIN_STEPS = 3
        const val MIN_METERS = 3.0
        const val MIN_STRIDE = 0.3
        const val MAX_STRIDE = 2.5
        const val ENOUGH_STEPS = 40
        const val MAX_STEPS = 3000
    }
}
