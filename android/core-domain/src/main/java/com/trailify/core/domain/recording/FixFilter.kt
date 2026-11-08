package com.trailify.core.domain.recording

/**
 * Filtr "rozgrzewki" odczytów GPS. Zaraz po starcie, wznowieniu albo powrocie sygnału system potrafi najpierw
 * podać pozycję starą (z pamięci podręcznej) albo mało dokładną, a dopiero po chwili właściwą. Takie odczyty
 * przesuwały linię w bok ("teleportacja") i psuły wykrywanie luk w sygnale.
 *
 * Filtr odrzuca odczyty starsze niż [MAX_AGE_MS], a po starcie lub po ciszy dłuższej niż [SILENCE_MS] przepuszcza
 * dopiero drugi z kolei dokładny odczyt ([SETTLE_ACCURACY_METERS]). Gdy dokładnego sygnału nie ma przez
 * [SETTLE_TIMEOUT_MS], przepuszcza odczyty mimo to (dalej obowiązuje ogólny limit dokładności nagrywania),
 * żeby nagrywanie nie stanęło przy słabym GPS. Czysta logika, czas podajemy z zewnątrz.
 */
class FixFilter {
    private var settled = false
    private var goodStreak = 0
    private var settlingSinceMs: Long? = null
    private var lastFixAtMs: Long? = null

    /** Zaczyna "rozgrzewkę" od nowa (start i wznowienie nagrania). */
    fun reset() {
        settled = false
        goodStreak = 0
        settlingSinceMs = null
        lastFixAtMs = null
    }

    /**
     * @param nowMs chwila odebrania odczytu (zegar monotoniczny, ms)
     * @param ageMs wiek odczytu w chwili odebrania (ms)
     * @param accuracyMeters dokładność odczytu albo null, gdy nieznana
     * @return true, gdy odczyt można przekazać do nagrania
     */
    fun accept(nowMs: Long, ageMs: Long, accuracyMeters: Float?): Boolean {
        if (ageMs > MAX_AGE_MS) return false

        val previous = lastFixAtMs
        lastFixAtMs = nowMs
        if (settled && previous != null && nowMs - previous > SILENCE_MS) {
            settled = false
            goodStreak = 0
            settlingSinceMs = null
        }
        if (settled) return true

        val since = settlingSinceMs ?: nowMs.also { settlingSinceMs = it }
        val good = accuracyMeters == null || accuracyMeters <= SETTLE_ACCURACY_METERS
        goodStreak = if (good) goodStreak + 1 else 0
        if (goodStreak >= SETTLE_FIXES || nowMs - since >= SETTLE_TIMEOUT_MS) {
            settled = true
            return true
        }
        return false
    }

    companion object {
        const val MAX_AGE_MS = 10_000L
        const val SILENCE_MS = 20_000L
        const val SETTLE_ACCURACY_METERS = 15f
        const val SETTLE_FIXES = 2
        const val SETTLE_TIMEOUT_MS = 20_000L
    }
}
