package com.trailify.core.domain.recording

import com.trailify.core.domain.model.ActivityType
import com.trailify.core.domain.model.LatLng
import com.trailify.core.domain.model.TrackPoint
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/** Skąd wzięła się propozycja przebiegu trasy w luce. */
enum class CandidateKind {
    /** Linia prosta między końcami luki (bez dróg i ścieżek). */
    STRAIGHT,

    /** Najkrótsza trasa po drogach i ścieżkach. */
    SHORTEST_ROUTE,

    /** Trasa przez dodatkowy punkt pośredni: użytkownik przeszedł więcej, niż wynika z najkrótszej drogi. */
    DETOUR,
}

/**
 * Jedna z możliwych wersji przebiegu trasy w luce.
 *
 * @property points punkty odtworzone (bez zmierzonych końców luki), z czasem rozłożonym wzdłuż trasy
 * @property score ocena (im wyższa, tym bardziej prawdopodobna; skala logarytmiczna)
 * @property probability względna wiarygodność wśród propozycji (suma po wszystkich = 1)
 */
data class GapCandidate(
    val kind: CandidateKind,
    val points: List<TrackPoint>,
    val lengthMeters: Double,
    val score: Double,
    val probability: Double,
)

/**
 * Wagi składników oceny. Ustawione ręcznie na rozsądne wartości; w etapie 4 dobieramy je na nagraniach
 * z wyciętymi sztucznymi lukami, tak żeby błąd odtworzenia był jak najmniejszy.
 *
 * @property lengthSigma tolerancja długości względem oczekiwanej (0,25 = ok. 25%)
 * @property directionWeight kara za zawracanie względem dotychczasowego kierunku (u-turn = ta wartość)
 * @property offRoadPenalty kara za linię prostą: ludzie zwykle idą drogami i ścieżkami
 * @property detourPenalty mała kara za objazd, żeby przy remisie wygrywała prostsza wersja
 * @property stepsShare jaką część oczekiwanego dystansu wyznaczają kroki (reszta to tempo), gdy są dostępne
 * @property roadFactor ile razy trasa po drogach jest zwykle dłuższa od linii prostej między jej punktami
 */
data class GapWeights(
    val lengthSigma: Double = 0.25,
    val directionWeight: Double = 1.0,
    val offRoadPenalty: Double = 0.7,
    val detourPenalty: Double = 0.2,
    val stepsShare: Double = 0.6,
    val roadFactor: Double = 1.25,
)

// --- ocena ---

/**
 * Ile metrów użytkownik najpewniej przeszedł w luce: z tempa sprzed luki i, jeśli są kroki i znana długość kroku,
 * także z krokomierza (nie dotyczy roweru). Nie mniej niż linia prosta i nie więcej niż pozwala największa prędkość.
 */
internal fun expectedMeters(gap: GpsGap, type: ActivityType, context: GapContext, weights: GapWeights): Double {
    val byPace = context.priorSpeedMps * gap.seconds
    val steps = context.stepsInGap
    val stride = context.strideMeters
    val estimate = if (type != ActivityType.CYCLING && steps != null && stride != null) {
        weights.stepsShare * steps * stride + (1 - weights.stepsShare) * byPace
    } else {
        byPace
    }
    val ceiling = GapRules.maxSpeedMps(type) * gap.seconds
    return estimate.coerceIn(gap.straightMeters, maxOf(gap.straightMeters, ceiling))
}

/** Ocena jednej propozycji: zgodność długości z tempem, kierunek początkowy, brak dróg, złożoność. */
internal fun scoreCandidate(
    kind: CandidateKind,
    lengthMeters: Double,
    entryBearingDegrees: Double,
    expectedMeters: Double,
    context: GapContext,
    weights: GapWeights,
): Double {
    val ratio = ln(lengthMeters / expectedMeters)
    var score = -(ratio * ratio) / (2 * weights.lengthSigma * weights.lengthSigma)

    val heading = context.entryHeadingDegrees
    if (heading != null) {
        val turn = angleDifferenceDegrees(heading, entryBearingDegrees) / 180.0 // 0 = prosto, 1 = zawrócenie
        score -= weights.directionWeight * turn * turn
    }
    if (kind == CandidateKind.STRAIGHT) score -= weights.offRoadPenalty
    if (kind == CandidateKind.DETOUR) score -= weights.detourPenalty
    return score
}

/** Zamienia oceny (skala logarytmiczna) na prawdopodobieństwa sumujące się do 1. */
internal fun softmax(scores: List<Double>): List<Double> {
    if (scores.isEmpty()) return emptyList()
    val top = scores.max()
    val weights = scores.map { exp(it - top) }
    val sum = weights.sum()
    return weights.map { it / sum }
}

// --- geometria ---

private const val METERS_PER_DEGREE = 111_194.9

/** Kierunek z punktu [a] do [b] w stopniach (0 = północ, 90 = wschód). */
internal fun bearingDegrees(a: LatLng, b: LatLng): Double {
    val lat1 = Math.toRadians(a.latitude)
    val lat2 = Math.toRadians(b.latitude)
    val dLng = Math.toRadians(b.longitude - a.longitude)
    val y = sin(dLng) * cos(lat2)
    val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLng)
    return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
}

/** Różnica między dwoma kierunkami, 0 do 180 stopni. */
internal fun angleDifferenceDegrees(a: Double, b: Double): Double {
    val d = ((a - b) % 360.0 + 360.0) % 360.0
    return if (d > 180.0) 360.0 - d else d
}

/** Punkt przesunięty od [origin] o podaną liczbę metrów na wschód i na północ. */
internal fun offsetMeters(origin: LatLng, eastMeters: Double, northMeters: Double): LatLng = LatLng(
    latitude = origin.latitude + northMeters / METERS_PER_DEGREE,
    longitude = origin.longitude + eastMeters / (METERS_PER_DEGREE * cos(Math.toRadians(origin.latitude))),
)

/** Punkt leżący na łamanej w danej części jej długości (0 = początek, 1 = koniec). */
internal fun pointAtFraction(path: List<LatLng>, fraction: Double): LatLng {
    val total = pathLengthMeters(path)
    if (path.size < 2 || total <= 0.0) return path.first()
    val target = total * fraction
    var walked = 0.0
    for (i in 1 until path.size) {
        val segment = distanceMeters(path[i - 1], path[i])
        if (walked + segment >= target && segment > 0.0) {
            val f = (target - walked) / segment
            val a = path[i - 1]
            val b = path[i]
            return LatLng(a.latitude + (b.latitude - a.latitude) * f, a.longitude + (b.longitude - a.longitude) * f)
        }
        walked += segment
    }
    return path.last()
}

/** Kierunek, w którym trasa wychodzi z jej początku: do pierwszego wierzchołka oddalonego o co najmniej kilkanaście metrów. */
internal fun entryBearingOf(path: List<LatLng>): Double {
    val start = path.first()
    val target = path.drop(1).firstOrNull { distanceMeters(start, it) >= GapRules.HEADING_MIN_METERS } ?: path.last()
    return bearingDegrees(start, target)
}

/**
 * Punkty pośrednie, przez które można poprowadzić trasę, żeby była dłuższa niż najkrótsza (użytkownik przeszedł
 * więcej, niż wynika z odległości między końcami luki). Cztery warianty: z lewej i z prawej strony linii A-B oraz
 * za końcem B i przed początkiem A (wyjście dalej i powrót). Długość "w linii prostej" objazdu przez punkt
 * pośredni dobieramy tak, żeby po przeliczeniu na drogi wyszedł [expectedMeters]. Pusta lista, gdy objazd niepotrzebny.
 */
internal fun detourViaPoints(from: LatLng, to: LatLng, expectedMeters: Double, roadFactor: Double): List<LatLng> {
    val midLat = (from.latitude + to.latitude) / 2
    val dx = (to.longitude - from.longitude) * METERS_PER_DEGREE * cos(Math.toRadians(midLat))
    val dy = (to.latitude - from.latitude) * METERS_PER_DEGREE
    val length = hypot(dx, dy)
    if (length < 1.0) return emptyList()
    val ux = dx / length // kierunek A -> B (wschód, północ)
    val uy = dy / length

    val sum = expectedMeters / roadFactor // |A-X| + |X-B| w linii prostej
    if (sum <= length) return emptyList()
    val half = sum / 2
    val height = minOf(sqrt(half * half - (length / 2) * (length / 2)), GapRules.MAX_DETOUR_METERS)
    val extension = minOf((sum - length) / 2, GapRules.MAX_DETOUR_METERS)

    val middle = LatLng(midLat, (from.longitude + to.longitude) / 2)
    return listOf(
        offsetMeters(middle, -uy * height, ux * height),
        offsetMeters(middle, uy * height, -ux * height),
        offsetMeters(to, ux * extension, uy * extension),
        offsetMeters(from, -ux * extension, -uy * extension),
    )
}

/** Wygląda jak ta sama trasa: podobna długość i środek w tym samym miejscu. */
internal fun sameRoute(
    lengthA: Double,
    pathA: List<LatLng>,
    lengthB: Double,
    pathB: List<LatLng>,
): Boolean {
    if (abs(lengthA - lengthB) > 0.02 * maxOf(lengthA, lengthB)) return false
    return distanceMeters(pointAtFraction(pathA, 0.5), pointAtFraction(pathB, 0.5)) < SAME_ROUTE_METERS
}

private const val SAME_ROUTE_METERS = 20.0
