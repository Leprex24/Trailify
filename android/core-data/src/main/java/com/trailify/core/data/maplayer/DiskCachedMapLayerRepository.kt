package com.trailify.core.data.maplayer

import android.util.Log
import com.trailify.core.data.api.parseBackendLines
import com.trailify.core.domain.model.BoundingBox
import com.trailify.core.domain.model.LatLng
import com.trailify.core.domain.model.MapLayerType
import com.trailify.core.domain.repository.MapLayerRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Locale

/**
 * Zapisuje na dysku telefonu warstwy mapy (ścieżki, szlaki) pobrane ze [source], kafelek po kafelku.
 *
 *  - świeże dane (do [FRESH_MS]) są brane z dysku bez pytania serwera, więc odwiedzone miejsca ładują się od razu,
 *  - gdy dane są starsze, pytamy [source] i zapisujemy odpowiedź,
 *  - gdy [source] zawiedzie (brak sieci, serwer wyłączony), używamy ostatnich zapisanych danych, nawet starych,
 *    więc wcześniej oglądane okolice działają offline.
 *
 * Zapisane pliki zajmują najwyżej [MAX_BYTES]; przy przekroczeniu usuwamy najstarsze.
 */
class DiskCachedMapLayerRepository(
    private val source: MapLayerRepository,
    cacheDir: File,
    private val now: () -> Long = System::currentTimeMillis,
) : MapLayerRepository {

    private val dir = File(cacheDir, "map-layers").also { it.mkdirs() }

    override suspend fun load(type: MapLayerType, box: BoundingBox): List<List<LatLng>> {
        val file = File(dir, fileName(type, box))
        withContext(Dispatchers.IO) { readIfFresh(file) }?.let { return it }
        return try {
            source.load(type, box).also { lines -> withContext(Dispatchers.IO) { write(file, lines) } }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val stale = withContext(Dispatchers.IO) { read(file) }
            if (stale == null) throw e
            Log.w(TAG, "Brak nowych danych warstwy ${type.name} (${e.message}), używam zapisanych na dysku")
            stale
        }
    }

    private fun readIfFresh(file: File): List<List<LatLng>>? =
        if (file.isFile && now() - file.lastModified() < FRESH_MS) read(file) else null

    /** Odczyt zapisanego pliku; uszkodzony plik jest usuwany. */
    private fun read(file: File): List<List<LatLng>>? {
        if (!file.isFile) return null
        return try {
            parseBackendLines(file.readText())
        } catch (e: Exception) {
            file.delete()
            null
        }
    }

    private fun write(file: File, lines: List<List<LatLng>>) {
        try {
            val json = StringBuilder("{\"lines\":[")
            lines.forEachIndexed { i, line ->
                if (i > 0) json.append(',')
                json.append('[')
                line.forEachIndexed { j, p ->
                    if (j > 0) json.append(',')
                    json.append('[').append(p.latitude).append(',').append(p.longitude).append(']')
                }
                json.append(']')
            }
            json.append("]}")
            val temp = File(dir, file.name + ".tmp")
            temp.writeText(json.toString())
            if (!temp.renameTo(file)) temp.delete()
            trim()
        } catch (e: IOException) {
            // Brak miejsca na dysku nie powinien psuć działania mapy.
        }
    }

    /** Usuwa najstarsze pliki, gdy zapisane dane zajmują więcej niż [MAX_BYTES]. */
    private fun trim() {
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() }
        for (f in files) {
            if (total <= MAX_BYTES) break
            total -= f.length()
            f.delete()
        }
    }

    // Kafelki mają stałą siatkę, więc ten sam kafelek daje tę samą nazwę pliku.
    private fun fileName(type: MapLayerType, box: BoundingBox) =
        String.format(Locale.US, "%s_%.5f_%.5f_%.5f_%.5f.json", type.name, box.minLat, box.minLng, box.maxLat, box.maxLng)

    private companion object {
        const val TAG = "MapLayerCache"
        const val FRESH_MS = 7L * 24 * 60 * 60 * 1000
        const val MAX_BYTES = 50L * 1024 * 1024
    }
}
