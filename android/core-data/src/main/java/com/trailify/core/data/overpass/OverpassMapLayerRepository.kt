package com.trailify.core.data.overpass

import com.trailify.core.domain.model.AppException
import com.trailify.core.domain.model.BoundingBox
import com.trailify.core.domain.model.LatLng
import com.trailify.core.domain.model.MapLayerType
import com.trailify.core.domain.repository.MapLayerRepository
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Pobiera ścieżki rowerowe i szlaki piesze z OpenStreetMap przez publiczne serwery Overpass.
 * Odpowiedzi są zapisywane na dysku na 7 dni.
 *
 * Publiczne serwery Overpass są darmowe, ale bywają przeciążone, a jeden adres IP może mieć tylko kilka
 * zapytań naraz. Dlatego:
 *  - porzucone zapytanie jest naprawdę przerywane (nie zajmuje miejsca na serwerze),
 *  - przy błędzie próbujemy kolejnego serwera z listy.
 */
class OverpassMapLayerRepository(cacheDir: File) : MapLayerRepository {

    // Pobrane odpowiedzi trzymamy też na dysku, więc odwiedzone wcześniej miejsca ładują się od razu,
    // także po ponownym uruchomieniu aplikacji.
    private val diskCache = File(cacheDir, "overpass").also { it.mkdirs() }

    @Volatile
    private var preferred = 0

    override suspend fun load(type: MapLayerType, box: BoundingBox): List<List<LatLng>> {
        val text = readCache(type, box) ?: postWithFallback(type, buildQuery(type, box)).also { writeCache(type, box, it) }
        val lines = withContext(Dispatchers.Default) { parseLines(text) }
        Log.d(TAG, "${type.name}: ${lines.size} linii")
        return lines
    }

    // W nazwie pliku jest skrót zapytania, więc po zmianie zapytania stare dane z dysku nie są używane.
    private fun cacheFile(type: MapLayerType, box: BoundingBox) =
        File(diskCache, "${type.name}_${buildQuery(type, box).hashCode()}.json")

    private suspend fun readCache(type: MapLayerType, box: BoundingBox): String? = withContext(Dispatchers.IO) {
        try {
            val file = cacheFile(type, box)
            if (file.isFile && System.currentTimeMillis() - file.lastModified() < CACHE_TTL_MS) file.readText() else null
        } catch (e: IOException) {
            null
        }
    }

    private suspend fun writeCache(type: MapLayerType, box: BoundingBox, text: String) {
        withContext(Dispatchers.IO) {
            try {
                val file = cacheFile(type, box)
                val temp = File(diskCache, file.name + ".tmp")
                temp.writeText(text)
                if (!temp.renameTo(file)) temp.delete()
            } catch (e: IOException) {
                // Brak miejsca na dysku nie powinien psuć działania mapy.
            }
        }
    }

    private suspend fun postWithFallback(type: MapLayerType, query: String): String {
        val problems = ArrayList<String>()
        for (offset in ENDPOINTS.indices) {
            val index = (preferred + offset) % ENDPOINTS.size
            val endpoint = ENDPOINTS[index]
            try {
                val result = post(endpoint, query)
                checkRemark(result)
                preferred = index
                return result
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                problems.add("${URL(endpoint).host}: ${describe(e)}")
                // Treść odpowiedzi błędu mówi, co dokładnie nie podobało się serwerowi (np. błąd składni).
                Log.w(TAG, "${type.name} ${URL(endpoint).host}: ${e.message}")
            }
        }
        val summary = problems.joinToString("; ")
        Log.w(TAG, "${type.name}: wszystkie serwery Overpass zawiodły: $summary")
        Log.w(TAG, "${type.name} zapytanie:\n$query")
        throw AppException("OVERPASS_ERROR", summary)
    }

    /** Wysyła zapytanie. Anulowanie korutyny zrywa połączenie. */
    private suspend fun post(endpoint: String, query: String): String =
        withContext(Dispatchers.IO) {
            suspendCancellableCoroutine { cont ->
                val connection = URL(endpoint).openConnection() as HttpURLConnection
                cont.invokeOnCancellation { connection.disconnect() }
                try {
                    connection.requestMethod = "POST"
                    connection.connectTimeout = 8_000
                    connection.readTimeout = 40_000
                    connection.doOutput = true
                    // Overpass prosi o identyfikowalny User-Agent.
                    connection.setRequestProperty("User-Agent", "Trailify/1.0 (engineering thesis project)")
                    connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    connection.outputStream.use { out ->
                        out.write(("data=" + URLEncoder.encode(query, "UTF-8")).toByteArray())
                    }
                    val code = connection.responseCode
                    if (code != HttpURLConnection.HTTP_OK) {
                        val body = try {
                            connection.errorStream?.bufferedReader()?.use { it.readText() }?.take(600) ?: ""
                        } catch (e: IOException) {
                            ""
                        }
                        throw HttpStatusException(code, body)
                    }
                    val text = connection.inputStream.bufferedReader().use { it.readText() }
                    if (cont.isActive) cont.resume(text)
                } catch (e: Exception) {
                    if (cont.isActive) cont.resumeWithException(e)
                } finally {
                    connection.disconnect()
                }
            }
        }

    private class HttpStatusException(val code: Int, body: String) : Exception("HTTP $code $body".trim())

    private class OverpassRuntimeException : Exception("Overpass runtime error")

    /**
     * Overpass odpowiada kodem 200 także wtedy, gdy zapytanie przekroczyło limit czasu lub pamięci.
     * Dane są wtedy puste lub niepełne, a informacja o błędzie siedzi w polu "remark".
     */
    private fun checkRemark(text: String) {
        if (text.contains("\"remark\"") && text.contains("runtime error")) throw OverpassRuntimeException()
    }

    private fun describe(error: Exception?): String = when {
        error is HttpStatusException && error.code == 429 -> "serwer map jest przeciążony, spróbuj za chwilę"
        error is HttpStatusException && (error.code == 502 || error.code == 503 || error.code == 504) ->
            "serwer map nie zdążył odpowiedzieć (${error.code})"
        error is HttpStatusException -> "serwer map zwrócił błąd ${error.code}"
        error is OverpassRuntimeException -> "serwer map przerwał zbyt ciężkie zapytanie"
        error is ConnectException -> "nie można połączyć się z serwerem"
        error is SocketTimeoutException -> "przekroczono czas oczekiwania"
        error is UnknownHostException -> "brak połączenia z internetem"
        else -> error?.message ?: "nieznany błąd"
    }

    private companion object {
        const val TAG = "Overpass"
        const val CACHE_TTL_MS = 7L * 24 * 60 * 60 * 1000

        val ENDPOINTS = listOf(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.private.coffee/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter",
            "https://overpass.openstreetmap.fr/api/interpreter",
        )
    }
}

/** Składa zapytanie Overpass QL dla danej warstwy i prostokąta (kolejność: południe, zachód, północ, wschód). */
internal fun buildQuery(type: MapLayerType, box: BoundingBox): String {
    val bbox = "${box.minLat},${box.minLng},${box.maxLat},${box.maxLng}"
    val body = when (type) {
        MapLayerType.CYCLE_PATHS -> """
            way["highway"="cycleway"]($bbox)->.paths;
            rel["route"="bicycle"]($bbox);
            way(r)($bbox)->.routes;
            (.paths; .routes;);
        """.trimIndent()

        // Szlaki i trasy piesze: znakowane, miejskie, tematyczne. Wszystko, co w OSM jest trasą pieszą.
        MapLayerType.HIKING_TRAILS -> """
            rel["route"~"^(hiking|foot|walking)${'$'}"]($bbox);
            way(r)($bbox);
        """.trimIndent()

        // Ścieżki i drogi gruntowe wszędzie. Chodniki (footway) tylko te z gruntową nawierzchnią lub
        // oznaczone jako szlak (sac_scale), więc zwykłe chodniki miejskie (kostka, asfalt) odpadają.
        MapLayerType.FOREST_PATHS -> """
            way["highway"~"^(path|track|bridleway)${'$'}"]["footway"!~"^(sidewalk|crossing|link|traffic_island)${'$'}"]["access"!~"^(private|no)${'$'}"]($bbox)->.p;
            way["highway"="footway"]["footway"!~"^(sidewalk|crossing|link|traffic_island)${'$'}"]["access"!~"^(private|no)${'$'}"]["surface"~"^(ground|dirt|earth|gravel|fine_gravel|compacted|grass|sand|mud|pebblestone|unpaved|wood)${'$'}"]($bbox)->.f;
            way["highway"="footway"]["footway"!~"^(sidewalk|crossing|link|traffic_island)${'$'}"]["access"!~"^(private|no)${'$'}"]["sac_scale"]($bbox)->.h;
            (.p; .f; .h;);
        """.trimIndent()
    }
    return "[out:json][timeout:25];\n$body\nout skel geom qt 12000;"
}

/** Zamienia odpowiedź Overpass (JSON z `out geom`) na listę linii. */
internal fun parseLines(json: String): List<List<LatLng>> {
    val elements = JSONObject(json).optJSONArray("elements") ?: return emptyList()
    val result = ArrayList<List<LatLng>>(elements.length())
    for (i in 0 until elements.length()) {
        val geometry = elements.optJSONObject(i)?.optJSONArray("geometry") ?: continue
        val line = ArrayList<LatLng>(geometry.length())
        for (j in 0 until geometry.length()) {
            val point = geometry.optJSONObject(j) ?: continue
            line.add(LatLng(point.getDouble("lat"), point.getDouble("lon")))
        }
        if (line.size >= 2) result.add(line)
    }
    return result
}
