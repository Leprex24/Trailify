package com.trailify.feature.map

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Style mapy do wyboru w menu "Warstwy". Wszystkie z OpenFreeMap (dane OpenStreetMap, bez klucza API).
 * Kolejne style można dopisać tutaj: wystarczy nazwa i adres pliku stylu.
 */
internal enum class MapStyleOption(val label: String, val url: String) {
    LIBERTY("Liberty (kolorowa)", "https://tiles.openfreemap.org/styles/liberty"),
    BRIGHT("Bright (jasna)", "https://tiles.openfreemap.org/styles/bright"),
    POSITRON("Positron (stonowana)", "https://tiles.openfreemap.org/styles/positron"),
}

/** Wybrany styl mapy, wspólny dla mapy START i mapy w podsumowaniu (do końca działania aplikacji). */
internal object MapStyleChoice {
    var current by mutableStateOf(MapStyleOption.LIBERTY)
}
