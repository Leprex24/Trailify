package com.trailify.core.domain.recording

import com.trailify.core.domain.model.ActivityType
import com.trailify.core.domain.model.LatLng
import com.trailify.core.domain.model.PlannedRoute
import com.trailify.core.domain.model.TrackPoint
import com.trailify.core.domain.repository.RoutePlanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot

/**
 * Eksperyment do pracy: ile błędu daje odtwarzanie luk w GPS i jakie wagi oceny są najlepsze.
 *
 * Z prawdziwych nagrań (punkty zmierzone) wycina sztuczne luki o znanym przebiegu, odtwarza je różnymi metodami
 * i porównuje wynik z tym, którędy użytkownik naprawdę szedł. To nie jest zwykły test: uruchamia się go tylko
 * wtedy, gdy ustawiona jest zmienna TRAILIFY_EXPERIMENT_CSV (inaczej jest pomijany, więc nie przeszkadza
 * w `./gradlew test`). Opis uruchomienia: docs/eksperyment-luk.md.
 *
 * Zmienne środowiskowe:
 *  - TRAILIFY_EXPERIMENT_CSV: plik CSV z punktami (activity_id,type,t,latitude,longitude), wymagany,
 *  - TRAILIFY_GRAPHHOPPER_URL: adres GraphHoppera (domyślnie http://localhost:8989),
 *  - TRAILIFY_EXPERIMENT_OUT: katalog na wyniki (domyślnie katalog pliku CSV).
 */
class GapExperiment {

    // --- dane ---

    private class Track(val id: Long, val type: ActivityType, val points: List<TrackPoint>)

    /** Jedna sztuczna luka: punkty od [from] do [to] (włącznie) to prawda, którą chcemy odtworzyć. */
    private class Window(
        val track: Track,
        val trackIndex: Int,
        val durationSeconds: Int,
        val startIndex: Int,
        val endIndex: Int,
        val truth: List<TrackPoint>,
        val gap: GpsGap,
        val context: GapContext,
        val contextNoProfile: GapContext,
    )

    private class Metrics(val meanError: Double, val maxError: Double, val lengthError: Double)

    private fun loadTracks(file: File): List<Track> {
        val rows = file.readLines().drop(1).filter { it.isNotBlank() }.map { row -> row.split(',').map { it.trim().trim('"') } }
        return rows.groupBy { it[0].trim().toLong() }.map { (id, list) ->
            val type = ActivityType.valueOf(list.first()[1].trim().uppercase())
            val points = list
                .map { TrackPoint(latitude = it[3].toDouble(), longitude = it[4].toDouble(), recordedAt = Instant.ofEpochMilli((it[2].toDouble() * 1000).toLong())) }
                .sortedBy { it.recordedAt }
            Track(id, type, points)
        }.sortedBy { it.id }
    }

    /** Tempo użytkownika z pozostałych tras tego samego rodzaju (bez trasy, z której wycinamy lukę). */
    private fun profileWithout(tracks: List<Track>, skip: Track): Double? {
        val profile = PaceProfile()
        for (track in tracks) {
            if (track === skip || track.type != skip.type) continue
            for (k in 1 until track.points.size) {
                val a = track.points[k - 1]
                val b = track.points[k]
                val dt = (b.recordedAt.toEpochMilli() - a.recordedAt.toEpochMilli()) / 1000.0
                profile.update(track.type, distanceMeters(LatLng(a.latitude, a.longitude), LatLng(b.latitude, b.longitude)), dt)
            }
        }
        return profile.speedMps
    }

    private fun seconds(a: TrackPoint, b: TrackPoint) = (b.recordedAt.toEpochMilli() - a.recordedAt.toEpochMilli()) / 1000.0

    /** Wycina sztuczne luki o zadanych długościach z całych tras; pomija miejsca z prawdziwymi przerwami w sygnale. */
    private fun makeWindows(tracks: List<Track>, durations: List<Int>, perDuration: Int): List<Window> {
        val checker = GapReconstructor(NoPlanner)
        val result = ArrayList<Window>()
        tracks.forEachIndexed { trackIndex, track ->
            val pts = track.points
            if (pts.size < 20) return@forEachIndexed
            val profileSpeed = profileWithout(tracks, track)
            for (duration in durations) {
                var count = 0
                var i = 0
                while (i < pts.size - 2 && count < perDuration) {
                    if (seconds(pts[0], pts[i]) < MIN_PRIOR_SECONDS) { i++; continue }
                    var j = i + 1
                    while (j < pts.size && seconds(pts[i], pts[j]) < duration) j++
                    if (j >= pts.size) break
                    val ok = seconds(pts[i], pts[j]) <= duration + 15 &&
                        (i until j).all { seconds(pts[it], pts[it + 1]) <= MAX_STEP_SECONDS } &&
                        j - i >= 3
                    val gap = GpsGap(pts[i], pts[j])
                    if (ok && gap.straightMeters >= GapRules.MIN_GAP_METERS && checker.isReconstructable(gap, track.type)) {
                        val before = pts.subList(0, i + 1).toList()
                        result.add(
                            Window(
                                track, trackIndex, duration, i, j, pts.subList(i, j + 1).toList(), gap,
                                GapContext.build(before, gap, track.type, profileSpeedMps = profileSpeed),
                                GapContext.build(before, gap, track.type, profileSpeedMps = null),
                            ),
                        )
                        count++
                        // następna luka z tej trasy dopiero po przerwie, żeby okna się nie nakładały
                        var next = j
                        while (next < pts.size && seconds(pts[j], pts[next]) < 120) next++
                        i = next
                    } else {
                        i++
                    }
                }
            }
        }
        return result
    }

    // --- miary błędu ---

    /** Odległość punktu od łamanej (m), w lokalnym rzucie płaskim. */
    private fun distanceToPolyline(p: LatLng, poly: List<LatLng>): Double {
        if (poly.size == 1) return distanceMeters(p, poly[0])
        val ky = 111_194.9
        val kx = ky * cos(Math.toRadians(p.latitude))
        var best = Double.MAX_VALUE
        for (k in 0 until poly.size - 1) {
            val ax = (poly[k].longitude - p.longitude) * kx
            val ay = (poly[k].latitude - p.latitude) * ky
            val bx = (poly[k + 1].longitude - p.longitude) * kx
            val by = (poly[k + 1].latitude - p.latitude) * ky
            val dx = bx - ax
            val dy = by - ay
            val len2 = dx * dx + dy * dy
            val t = if (len2 == 0.0) 0.0 else ((-ax) * dx + (-ay) * dy) / len2
            val c = t.coerceIn(0.0, 1.0)
            best = minOf(best, hypot(ax + c * dx, ay + c * dy))
        }
        return best
    }

    private fun TrackPoint.latLng() = LatLng(latitude, longitude)

    /** Błąd odtworzenia: średnia i maksymalna odległość między prawdziwą a odtworzoną trasą (w obie strony) oraz błąd długości. */
    private fun measure(window: Window, inner: List<TrackPoint>): Metrics {
        val truth = window.truth.map { it.latLng() }
        val rebuilt = listOf(window.gap.from.latLng()) + inner.map { it.latLng() } + window.gap.to.latLng()
        val toRebuilt = truth.map { distanceToPolyline(it, rebuilt) }
        val toTruth = rebuilt.map { distanceToPolyline(it, truth) }
        val mean = (toRebuilt.average() + toTruth.average()) / 2
        val max = maxOf(toRebuilt.max(), toTruth.max())
        val truthLength = pathLengthMeters(truth)
        val lengthError = (pathLengthMeters(rebuilt) - truthLength) / truthLength
        return Metrics(mean, max, lengthError)
    }

    // --- metody ---

    private enum class Method(val label: String) {
        STRAIGHT("linia prosta"),
        SHORTEST("najkrótsza trasa po ścieżkach"),
        RANKED_NO_PROFILE("ocena propozycji (bez profilu tempa)"),
        RANKED("ocena propozycji (z profilem tempa)"),
        ORACLE("najlepsza z propozycji (górna granica)"),
    }

    private suspend fun rebuild(method: Method, window: Window, planner: RoutePlanner, weights: GapWeights): List<TrackPoint> {
        val reconstructor = GapReconstructor(planner, weights)
        val type = window.track.type
        return when (method) {
            Method.STRAIGHT -> reconstructor.straightLine(window.gap)
            Method.SHORTEST -> reconstructor.byRouting(window.gap, type) ?: reconstructor.straightLine(window.gap)
            Method.RANKED_NO_PROFILE -> reconstructor.rank(window.gap, type, window.contextNoProfile).firstOrNull()?.points
                ?: reconstructor.straightLine(window.gap)
            Method.RANKED -> reconstructor.rank(window.gap, type, window.context).firstOrNull()?.points
                ?: reconstructor.straightLine(window.gap)
            Method.ORACLE -> reconstructor.rank(window.gap, type, window.context)
                .minByOrNull { measure(window, it.points).meanError }?.points
                ?: reconstructor.straightLine(window.gap)
        }
    }

    // --- eksperyment ---

    @Test
    fun run() {
        val csvPath = System.getenv("TRAILIFY_EXPERIMENT_CSV")
        assumeTrue("Eksperyment pomijany: brak zmiennej TRAILIFY_EXPERIMENT_CSV", !csvPath.isNullOrBlank())
        val csv = File(csvPath!!)
        val outDir = File(System.getenv("TRAILIFY_EXPERIMENT_OUT") ?: csv.absoluteFile.parent).also { it.mkdirs() }
        val planner = GraphHopperPlanner(System.getenv("TRAILIFY_GRAPHHOPPER_URL") ?: "http://localhost:8989", File(outDir, "routing_cache.tsv"))

        val tracks = loadTracks(csv)
        val windows = makeWindows(tracks, DURATIONS, PER_DURATION)
        val report = StringBuilder()
        fun line(text: String = "") { report.appendLine(text); println(text) }

        line("# Wyniki eksperymentu rekonstrukcji luk GPS")
        line()
        line("Trasy: ${tracks.size} (${tracks.groupBy { it.type }.map { "${it.key}: ${it.value.size}" }.joinToString(", ")}), " +
            "punktów: ${tracks.sumOf { it.points.size }}, sztucznych luk: ${windows.size}.")
        if (windows.isEmpty()) {
            line("Nie udało się wyciąć żadnej luki (za krótkie trasy albo ciągłe przerwy w sygnale).")
            File(outDir, "experiment_results.md").writeText(report.toString())
            return
        }

        runBlocking {
            val defaults = GapWeights()

            // 1. porównanie metod z domyślnymi wagami, na wszystkich lukach
            val perWindow = ArrayList<List<Any>>() // do pliku CSV
            val results = HashMap<Method, List<Metrics>>()
            for (method in Method.entries) results[method] = windows.map { measure(it, rebuild(method, it, planner, defaults)) }

            fun table(title: String, subset: (Window) -> Boolean) {
                val idx = windows.indices.filter { subset(windows[it]) }
                if (idx.isEmpty()) return
                line()
                line("## $title (luk: ${idx.size})")
                line()
                line("| metoda | średni błąd [m] | mediana błędu [m] | maks. błąd [m] (średnia) | błąd długości [%] (bezwzgl.) |")
                line("|---|---|---|---|---|")
                for (method in Method.entries) {
                    val m = idx.map { results.getValue(method)[it] }
                    line("| ${method.label} | ${f(m.map { it.meanError }.average())} | ${f(median(m.map { it.meanError }))} | " +
                        "${f(m.map { it.maxError }.average())} | ${f(m.map { abs(it.lengthError) * 100 }.average())} |")
                }
            }
            table("Wszystkie luki") { true }
            for (d in DURATIONS) table("Luki o długości ok. $d s") { it.durationSeconds == d }
            for (t in ActivityType.entries) table("Aktywność: $t") { it.track.type == t }

            for ((wi, w) in windows.withIndex()) {
                perWindow.add(
                    listOf(w.track.id, w.track.type, w.durationSeconds, f(w.gap.straightMeters), f(pathLengthMeters(w.truth.map { it.latLng() }))) +
                        Method.entries.map { f(results.getValue(it)[wi].meanError) },
                )
            }
            File(outDir, "experiment_windows.csv").writeText(
                "activity_id,type,duration_s,straight_m,truth_m," + Method.entries.joinToString(",") { it.name.lowercase() } + "\n" +
                    perWindow.joinToString("\n") { it.joinToString(",") },
            )

            // 2. dobór wag: trasy o parzystym numerze to zbiór uczący, nieparzyste testowy (żeby nie oceniać na tym, na czym dobieramy)
            val splitPossible = tracks.size >= 4
            val train = windows.filter { !splitPossible || it.trackIndex % 2 == 0 }
            val test = windows.filter { !splitPossible || it.trackIndex % 2 == 1 }.ifEmpty { train }
            line()
            line("## Dobór wag oceny")
            line()
            if (!splitPossible) line("Mało tras (< 4): wagi dobrane i sprawdzone na tych samych lukach, więc wynik jest optymistyczny.")
            line("Zbiór uczący: ${train.size} luk, testowy: ${test.size} luk.")

            suspend fun score(weights: GapWeights, set: List<Window>): Double =
                set.map { measure(it, rebuild(Method.RANKED, it, planner, weights)).meanError }.average()

            var best = defaults
            var bestScore = score(defaults, train)
            for (sigma in listOf(0.15, 0.25, 0.4))
                for (direction in listOf(0.0, 0.5, 1.0, 2.0))
                    for (offRoad in listOf(0.0, 0.4, 0.7, 1.2))
                        for (detour in listOf(0.0, 0.2, 0.5))
                            for (roadFactor in listOf(1.1, 1.25, 1.4)) {
                                val w = defaults.copy(lengthSigma = sigma, directionWeight = direction, offRoadPenalty = offRoad, detourPenalty = detour, roadFactor = roadFactor)
                                val s = score(w, train)
                                if (s < bestScore - 1e-9) { best = w; bestScore = s }
                            }

            line()
            line("| wagi | błąd na zbiorze uczącym [m] | błąd na zbiorze testowym [m] |")
            line("|---|---|---|")
            line("| domyślne | ${f(score(defaults, train))} | ${f(score(defaults, test))} |")
            line("| dobrane | ${f(bestScore)} | ${f(score(best, test))} |")
            line()
            line("Domyślne: $defaults")
            line("Dobrane:  $best")
            line()
            line("Do porównania, na zbiorze testowym: linia prosta ${f(test.map { measure(it, rebuild(Method.STRAIGHT, it, planner, defaults)).meanError }.average())} m, " +
                "najkrótsza trasa ${f(test.map { measure(it, rebuild(Method.SHORTEST, it, planner, defaults)).meanError }.average())} m.")
        }
        File(outDir, "experiment_results.md").writeText(report.toString())
    }

    // --- pomocnicze ---

    private fun f(x: Double) = String.format(Locale.US, "%.1f", x)

    private fun median(values: List<Double>): Double {
        val s = values.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }

    private object NoPlanner : RoutePlanner {
        override suspend fun plan(type: ActivityType, waypoints: List<LatLng>): PlannedRoute = throw IllegalStateException("bez sieci")
    }

    /**
     * Planer tras wołający GraphHoppera bezpośrednio (tak jak backend: profile foot i bike). Odpowiedzi trafiają do
     * pamięci podręcznej (też w pliku), więc powtórne uruchomienie i dobór wag nie odpytują silnika drugi raz.
     */
    private class GraphHopperPlanner(private val baseUrl: String, private val cacheFile: File) : RoutePlanner {
        private val cache = ConcurrentHashMap<String, String>()

        init {
            if (cacheFile.exists()) cacheFile.readLines().forEach { l -> l.split('\t').takeIf { it.size == 2 }?.let { cache[it[0]] = it[1] } }
        }

        override suspend fun plan(type: ActivityType, waypoints: List<LatLng>): PlannedRoute {
            val profile = if (type == ActivityType.CYCLING) "bike" else "foot"
            val key = profile + "|" + waypoints.joinToString(";") { String.format(Locale.US, "%.5f,%.5f", it.latitude, it.longitude) }
            val value = cache[key] ?: withContext(Dispatchers.IO) { fetch(profile, waypoints) }.also { stored ->
                cache[key] = stored
                synchronized(cacheFile) { cacheFile.appendText("$key\t$stored\n") }
            }
            if (value == "FAIL") throw IllegalStateException("GraphHopper nie znalazł trasy")
            val (distance, time, coordinates) = value.split('|')
            val geometry = coordinates.split(';').map { pair -> pair.split(',').let { LatLng(it[1].toDouble(), it[0].toDouble()) } }
            return PlannedRoute(distance.toDouble(), time.toLong().div(1000).toInt(), geometry)
        }

        private fun fetch(profile: String, waypoints: List<LatLng>): String {
            val query = waypoints.joinToString("&") { String.format(Locale.US, "point=%.6f,%.6f", it.latitude, it.longitude) } +
                "&profile=$profile&points_encoded=false&instructions=false"
            val connection = URL("${baseUrl.trimEnd('/')}/route?$query").openConnection() as HttpURLConnection
            connection.connectTimeout = 3_000
            connection.readTimeout = 15_000
            return try {
                if (connection.responseCode != 200) return "FAIL" // np. 400: nie znaleziono punktu albo połączenia
                val body = connection.inputStream.bufferedReader().readText()
                val distance = Regex("\"distance\":\\s*([0-9.eE+-]+)").find(body)?.groupValues?.get(1) ?: return "FAIL"
                val time = Regex("\"time\":\\s*(\\d+)").find(body)?.groupValues?.get(1) ?: return "FAIL"
                val pointsAt = body.indexOf("\"points\"")
                val start = body.indexOf("\"coordinates\"", pointsAt)
                if (pointsAt < 0 || start < 0) return "FAIL"
                val end = body.indexOf("]]", start)
                val coordinates = Regex("\\[\\s*(-?[0-9.eE+-]+)\\s*,\\s*(-?[0-9.eE+-]+)")
                    .findAll(body.substring(start, end + 2))
                    .joinToString(";") { "${it.groupValues[1]},${it.groupValues[2]}" }
                if (coordinates.isEmpty()) "FAIL" else "$distance|$time|$coordinates"
            } finally {
                connection.disconnect()
            }
        }
    }

    private companion object {
        /** Długości sztucznych luk (s): od progu wykrywania do kilku minut. */
        val DURATIONS = listOf(45, 90, 180, 300)
        const val PER_DURATION = 8
        const val MIN_PRIOR_SECONDS = 180.0
        const val MAX_STEP_SECONDS = 12.0
    }
}
