package com.trailify.core.domain.recording

import com.trailify.core.domain.model.ActivityType
import com.trailify.core.domain.model.LatLng
import com.trailify.core.domain.model.TrackPoint
import com.trailify.core.domain.repository.RoutePlanner
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.ceil

/** Progi wykrywania i odtwarzania luk w sygnale GPS (np. telefon z oszczędzaniem baterii miał zablokowany GPS). */
object GapRules {
    /** Tyle czasu bez zapisanego punktu, przy ruchu, uznajemy za utratę GPS (zwykle punkty przychodzą co 2 s). */
    const val MIN_GAP_SECONDS = 30L

    /** Luka musi też obejmować przesunięcie (inaczej to tylko postój, na którym GPS drga w miejscu). */
    const val MIN_GAP_METERS = 50.0

    /** Z jak dawnych danych (sekundy przed luką) liczymy tempo użytkownika. */
    const val PRIOR_WINDOW_SECONDS = 180L

    /** Odstęp między kolejnymi punktami, powyżej którego odcinek nie jest ruchem ciągłym (postój lub wcześniejsza luka). */
    const val MAX_PAIR_SECONDS = 15.0

    /** Najmniej zmierzonego czasu ruchu, żeby ufać tempu sprzed luki (inaczej bierzemy typowe tempo). */
    const val MIN_PRIOR_SECONDS = 20.0
    const val MIN_PRIOR_SPEED_MPS = 0.3

    /** Tak daleko wstecz od końca zmierzonej trasy szukamy kierunku ruchu. */
    const val HEADING_MIN_METERS = 15.0

    /** Gdy oczekiwany dystans jest tyle razy większy od linii prostej, sprawdzamy też trasy z objazdem. */
    const val DETOUR_TRIGGER = 1.5

    /** Największe wysunięcie punktu pośredniego objazdu od linii A-B. */
    const val MAX_DETOUR_METERS = 3000.0

    /** Ile punktów użytkownik może zaznaczyć przy ręcznym wyznaczaniu trasy w luce. */
    const val MAX_MANUAL_POINTS = 10

    /** Ręcznie wyznaczona trasa może wymagać co najwyżej tylu razy większej prędkości niż tempo sprzed luki. */
    const val MAX_MANUAL_SPEED_RATIO = 2.0

    /** Zapas ponad prędkość wynikającą z samej odległości między końcami luki (żeby najkrótsza droga zawsze przeszła). */
    const val MANUAL_SLACK = 1.2

    /** Ile sekund ruchu musi mieć profil tempa, żeby go używać (wcześniej za mało danych). */
    const val PROFILE_MIN_SECONDS = 120.0

    /** Profil pamięta mniej więcej tyle sekund ruchu (starsze dane ważą coraz mniej): ok. godzina. */
    const val PROFILE_MAX_SECONDS = 3600.0

    /** Odcinki wolniejsze od tego (m/s) to postój, nie ruch, i nie wchodzą do profilu. */
    const val PROFILE_MIN_MOVING_MPS = 0.5

    /**
     * Ile sekund świeżych danych sprzed luki waży tyle co profil: przy 180 s świeżego ruchu świeże tempo
     * ma wagę 75%, przy 60 s 50%, a bez świeżych danych tempo wynika z samego profilu.
     */
    const val PROFILE_PSEUDO_SECONDS = 60.0

    /** Dłuższych przerw nie odtwarzamy (wymaganie 3.2.3: 10-20 minut). */
    const val MAX_GAP_SECONDS = 20 * 60L

    /** Odstęp między odtworzonymi punktami na prostych odcinkach. */
    const val SAMPLE_SPACING_METERS = 20.0

    /** Wierzchołki trasy bliższe niż to pomijamy, żeby nie mnożyć punktów. */
    const val MIN_VERTEX_SPACING_METERS = 3.0

    /** Trasa po drogach dłuższa niż tyle razy linia prosta (plus zapas) jest nieprawdopodobna. */
    const val MAX_ROUTE_DETOUR = 2.5
    const val DETOUR_ALLOWANCE_METERS = 50.0

    /** Największa wiarygodna średnia prędkość na odcinku odtwarzanym (m/s): szybciej to raczej pojazd lub skok GPS. */
    fun maxSpeedMps(type: ActivityType): Double = when (type) {
        ActivityType.WALKING -> 3.0
        ActivityType.RUNNING -> 8.0
        ActivityType.CYCLING -> 20.0
    }
}

/** Luka w nagraniu: ostatni punkt przed utratą GPS ([from]) i pierwszy po jego odzyskaniu ([to]). */
data class GpsGap(val from: TrackPoint, val to: TrackPoint) {
    val millis: Long get() = to.recordedAt.toEpochMilli() - from.recordedAt.toEpochMilli()
    val seconds: Double get() = millis / 1000.0
    val straightMeters: Double get() = distanceMeters(from.position(), to.position())
}

internal fun TrackPoint.position() = LatLng(latitude, longitude)

/** Długość łamanej w metrach. */
fun pathLengthMeters(path: List<LatLng>): Double = path.zipWithNext { a, b -> distanceMeters(a, b) }.sum()

/** Długość przebiegu zapisanych punktów w metrach. */
fun trackLengthMeters(points: List<TrackPoint>): Double =
    points.zipWithNext { a, b -> distanceMeters(a.position(), b.position()) }.sum()

/** Ile metrów trasy to odcinki odtworzone (z co najmniej jednym końcem oznaczonym jako odtworzony). */
fun reconstructedMeters(points: List<TrackPoint>): Double =
    points.zipWithNext { a, b ->
        if (a.isReconstructed || b.isReconstructed) distanceMeters(a.position(), b.position()) else 0.0
    }.sum()

/**
 * Odtwarza przebieg trasy w luce GPS. Czysta logika (poza zapytaniem o trasę), więc testowalna.
 *
 * Etap 1: najpierw natychmiast linia prosta ([straightLine]), potem trasa po drogach i ścieżkach
 * ([byRouting]), jeśli uda się ją wyznaczyć i jest wiarygodna. Czas rozkładamy równomiernie wzdłuż
 * odtworzonej linii, więc tempo na odcinku wynika z jej długości i czasu przerwy.
 */
class GapReconstructor(
    private val planner: RoutePlanner,
    private val weights: GapWeights = GapWeights(),
) {

    /** Czy lukę w ogóle odtwarzamy: nie za krótka, nie za długa i z wiarygodną prędkością dla danej aktywności. */
    fun isReconstructable(gap: GpsGap, type: ActivityType): Boolean {
        val seconds = gap.seconds
        if (seconds < GapRules.MIN_GAP_SECONDS || seconds > GapRules.MAX_GAP_SECONDS) return false
        return gap.straightMeters / seconds <= GapRules.maxSpeedMps(type)
    }

    /** Odtworzenie bez internetu: punkty na linii prostej między końcami luki. */
    fun straightLine(gap: GpsGap): List<TrackPoint> = placeAlongPath(gap, emptyList())

    /** Linia prosta jako jedyna propozycja (gdy ocena propozycji się nie powiodła, np. brak internetu). */
    fun straightCandidate(gap: GpsGap): GapCandidate = GapCandidate(
        kind = CandidateKind.STRAIGHT,
        points = straightLine(gap),
        lengthMeters = gap.straightMeters,
        score = 0.0,
        probability = 1.0,
    )

    /**
     * Przebieg trasy w luce wyznaczony ręcznie: trasa po drogach i ścieżkach od A przez zaznaczone punkty do B.
     * Odrzucamy ją, gdy wymaga nierealnego tempa (nie więcej niż [GapRules.MAX_MANUAL_SPEED_RATIO] razy tempo
     * sprzed luki, ale zawsze dopuszczamy tempo wynikające z odległości A-B i nigdy nie więcej niż największa
     * wiarygodna prędkość dla aktywności). Dzięki temu nie da się dopisać sobie dowolnie długiej trasy.
     */
    suspend fun manual(gap: GpsGap, type: ActivityType, waypoints: List<LatLng>, priorSpeedMps: Double): ManualRoute {
        if (waypoints.isEmpty() || waypoints.size > GapRules.MAX_MANUAL_POINTS) {
            return ManualRoute(ManualGapResult.BAD_POINT_COUNT)
        }
        val route = try {
            planner.plan(type, listOf(gap.from.position()) + waypoints + gap.to.position())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return ManualRoute(ManualGapResult.NO_ROUTE)
        }
        val path = route.geometry
        if (path.size < 2) return ManualRoute(ManualGapResult.NO_ROUTE)

        val length = pathLengthMeters(listOf(gap.from.position()) + path + gap.to.position())
        val speed = length / gap.seconds
        val straightSpeed = gap.straightMeters / gap.seconds
        val allowed = minOf(
            GapRules.maxSpeedMps(type),
            maxOf(priorSpeedMps * GapRules.MAX_MANUAL_SPEED_RATIO, straightSpeed * GapRules.MANUAL_SLACK),
        )
        if (speed > allowed) return ManualRoute(ManualGapResult.TOO_FAST)
        return ManualRoute(ManualGapResult.APPLIED, placeAlongPath(gap, path))
    }

    /**
     * Odtworzenie najkrótszą trasą po drogach i ścieżkach (GraphHopper przez backend).
     * Zwraca null, gdy trasy nie da się wyznaczyć albo jest nieprawdopodobna (zbyt długi objazd
     * lub zbyt duża prędkość); wtedy zostaje linia prosta.
     */
    suspend fun byRouting(gap: GpsGap, type: ActivityType): List<TrackPoint>? {
        val path = routedPath(gap, type, emptyList(), gap.straightMeters * GapRules.MAX_ROUTE_DETOUR + GapRules.DETOUR_ALLOWANCE_METERS)
            ?: return null
        return placeAlongPath(gap, path)
    }

    /**
     * Wszystkie sensowne wersje przebiegu trasy w luce, od najbardziej do najmniej prawdopodobnej (zawsze co
     * najmniej jedna: linia prosta). Wersje to: najkrótsza trasa po drogach, ewentualnie trasy z objazdami,
     * gdy tempo i kroki wskazują, że użytkownik przeszedł więcej niż najkrótszą drogą, oraz linia prosta.
     * Ocena porównuje długość z tym, ile użytkownik mógł przejść w swoim tempie, i penalizuje zawracanie.
     * Zapytania o trasy idą równolegle, a te, które się nie powiodą, są po prostu pomijane.
     */
    suspend fun rank(gap: GpsGap, type: ActivityType, context: GapContext): List<GapCandidate> {
        val from = gap.from.position()
        val to = gap.to.position()
        val expected = expectedMeters(gap, type, context, weights)

        val routed: List<Pair<CandidateKind, List<LatLng>>> = coroutineScope {
            val jobs = ArrayList<Deferred<Pair<CandidateKind, List<LatLng>>?>>()
            jobs.add(
                async {
                    val limit = gap.straightMeters * GapRules.MAX_ROUTE_DETOUR + GapRules.DETOUR_ALLOWANCE_METERS
                    routedPath(gap, type, emptyList(), limit)?.let { CandidateKind.SHORTEST_ROUTE to it }
                },
            )
            if (expected > gap.straightMeters * GapRules.DETOUR_TRIGGER) {
                for (via in detourViaPoints(from, to, expected, weights.roadFactor)) {
                    jobs.add(
                        async {
                            routedPath(gap, type, listOf(via), expected * 3 + GapRules.DETOUR_ALLOWANCE_METERS)
                                ?.let { CandidateKind.DETOUR to it }
                        },
                    )
                }
            }
            jobs.awaitAll().filterNotNull()
        }

        // trasy po drogach najpierw: przy remisie z linią prostą (ta sama geometria) zostaje wersja po drogach
        val shapes = ArrayList<Pair<CandidateKind, List<LatLng>>>(routed)
        shapes.add(CandidateKind.STRAIGHT to emptyList())

        val kept = ArrayList<Triple<CandidateKind, List<LatLng>, Double>>() // rodzaj, pełna łamana, długość
        for ((kind, middle) in shapes) {
            val full = listOf(from) + middle + to
            val length = pathLengthMeters(full)
            if (length <= 0.0) continue
            if (kept.none { sameRoute(it.third, it.second, length, full) }) kept.add(Triple(kind, full, length))
        }

        val scores = kept.map { (kind, full, length) ->
            scoreCandidate(kind, length, entryBearingOf(full), expected, context, weights)
        }
        val probabilities = softmax(scores)
        return kept.indices
            .map { i ->
                val (kind, full, length) = kept[i]
                GapCandidate(
                    kind = kind,
                    points = placeAlongPath(gap, full.subList(1, full.size - 1)),
                    lengthMeters = length,
                    score = scores[i],
                    probability = probabilities[i],
                )
            }
            .sortedByDescending { it.score }
    }

    /**
     * Pyta o trasę z A do B (przez [via], jeśli podano) i zwraca jej wierzchołki albo null, gdy się nie udało,
     * trasa jest dłuższa niż [maxMeters] albo wymagałaby nierealnej prędkości dla danej aktywności.
     */
    private suspend fun routedPath(gap: GpsGap, type: ActivityType, via: List<LatLng>, maxMeters: Double): List<LatLng>? {
        val route = try {
            planner.plan(type, listOf(gap.from.position()) + via + gap.to.position())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return null
        }
        val path = route.geometry
        if (path.size < 2) return null
        val length = pathLengthMeters(listOf(gap.from.position()) + path + gap.to.position())
        if (length > maxMeters) return null
        if (length / gap.seconds > GapRules.maxSpeedMps(type)) return null
        return path
    }
}

/**
 * Punkty odtworzone wzdłuż łamanej od końca A do końca B luki, bez punktów A i B (te są zmierzone).
 * [middle] to pośrednie wierzchołki trasy (może być puste = linia prosta).
 * Czas każdego punktu jest proporcjonalny do przebytej drogi.
 */
internal fun placeAlongPath(gap: GpsGap, middle: List<LatLng>): List<TrackPoint> {
    val full = thin(listOf(gap.from.position()) + middle + gap.to.position())
    if (full.size < 2 || gap.millis <= 0) return emptyList()

    val cumulative = DoubleArray(full.size)
    for (i in 1 until full.size) cumulative[i] = cumulative[i - 1] + distanceMeters(full[i - 1], full[i])
    val total = cumulative.last()
    if (total <= 0.0) return emptyList()

    val speed = total / gap.seconds
    val out = ArrayList<TrackPoint>()

    fun emit(position: LatLng, along: Double) {
        out.add(
            TrackPoint(
                latitude = position.latitude,
                longitude = position.longitude,
                speed = speed,
                recordedAt = gap.from.recordedAt.plusMillis((gap.millis * (along / total)).toLong()),
                isReconstructed = true,
            ),
        )
    }

    for (i in 1 until full.size) {
        val a = full[i - 1]
        val b = full[i]
        val segment = cumulative[i] - cumulative[i - 1]
        val pieces = ceil(segment / GapRules.SAMPLE_SPACING_METERS).toInt()
        for (k in 1 until pieces) {
            val f = k.toDouble() / pieces
            emit(
                LatLng(a.latitude + (b.latitude - a.latitude) * f, a.longitude + (b.longitude - a.longitude) * f),
                cumulative[i - 1] + segment * f,
            )
        }
        if (i < full.size - 1) emit(b, cumulative[i])
    }
    return out
}

/**
 * Zostawia pierwszy i ostatni punkt oraz te pośrednie, które są co najmniej kilka metrów od poprzedniego
 * zachowanego i od końca (trasa po drogach zaczyna i kończy się tuż przy punktach A i B, więc ich kopie pomijamy).
 */
private fun thin(path: List<LatLng>): List<LatLng> {
    val out = ArrayList<LatLng>()
    val end = path.last()
    out.add(path.first())
    for (i in 1 until path.size - 1) {
        val candidate = path[i]
        if (distanceMeters(out.last(), candidate) >= GapRules.MIN_VERTEX_SPACING_METERS &&
            distanceMeters(candidate, end) >= GapRules.MIN_VERTEX_SPACING_METERS
        ) {
            out.add(candidate)
        }
    }
    out.add(end)
    return out
}
