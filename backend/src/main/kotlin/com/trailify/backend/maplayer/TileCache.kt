package com.trailify.backend.maplayer

/**
 * Prosta pamięć podręczna w RAM: najwyżej [maxEntries] wpisów (wypada najdawniej używany), każdy ważny [ttlMillis].
 * Kafelki w aplikacji leżą na stałej siatce, więc to samo zapytanie wraca od wielu użytkowników.
 */
class TileCache<V : Any>(
    private val maxEntries: Int,
    private val ttlMillis: Long,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private class Entry<V>(val value: V, val storedAt: Long)

    private val entries = object : LinkedHashMap<String, Entry<V>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry<V>>?): Boolean = size > maxEntries
    }

    @Synchronized
    fun get(key: String): V? {
        val entry = entries[key] ?: return null
        if (clock() - entry.storedAt > ttlMillis) {
            entries.remove(key)
            return null
        }
        return entry.value
    }

    @Synchronized
    fun put(key: String, value: V) {
        entries[key] = Entry(value, clock())
    }
}
