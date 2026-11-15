package com.trailify.feature.map

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style

/** Domyślna mapa: kafelki wektorowe z danych OpenStreetMap (OpenFreeMap, bez klucza API). */
private const val OSM_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

/**
 * Ortofotomapa z Geoportalu (GUGiK), usługa WMS w układzie Web Mercator. MapLibre sam podstawia
 * obszar kafelka w {bbox-epsg-3857}. Jeśli kiedyś przestanie działać (puste szare tło), zmień tylko ten adres.
 */
private const val ORTHOPHOTO_URL =
    "https://mapy.geoportal.gov.pl/wss/service/PZGIK/ORTO/WMS/StandardResolution" +
        "?SERVICE=WMS&VERSION=1.1.1&REQUEST=GetMap&LAYERS=Raster&STYLES=&FORMAT=image/jpeg" +
        "&SRS=EPSG:3857&WIDTH=256&HEIGHT=256&BBOX={bbox-epsg-3857}"

/**
 * Styl z jedną warstwą rastrową (bez napisów, więc bez czcionek). Atrybucja musi mieć odnośniki <a href>:
 * MapLibre pokazuje w oknie "i" tylko takie wpisy.
 */
private val SATELLITE_STYLE_JSON = """
    {
      "version": 8,
      "sources": {
        "orto": {
          "type": "raster",
          "tiles": ["$ORTHOPHOTO_URL"],
          "tileSize": 256,
          "minzoom": 0,
          "maxzoom": 19,
          "attribution": "<a href='https://www.geoportal.gov.pl'>Geoportal.gov.pl</a> <a href='https://www.gov.pl/web/gugik'>© GUGiK</a>"
        }
      },
      "layers": [{ "id": "orto", "type": "raster", "source": "orto" }]
    }
""".trimIndent()

/** Podkład mapy do wyboru w menu "Warstwy": domyślna mapa albo zdjęcia satelitarne (ortofotomapa). */
internal enum class MapStyleOption(val label: String) {
    MAP("Mapa"),
    SATELLITE("Satelita"),
    ;

    /** Ustawia ten podkład na mapie; [onLoaded] wywoła się, gdy styl będzie gotowy do dodawania warstw. */
    fun load(map: MapLibreMap, onLoaded: (Style) -> Unit) {
        when (this) {
            MAP -> map.setStyle(OSM_STYLE_URL) { onLoaded(it) }
            SATELLITE -> map.setStyle(Style.Builder().fromJson(SATELLITE_STYLE_JSON)) { onLoaded(it) }
        }
    }
}

/** Wybrany podkład, wspólny dla mapy START i mapy w podsumowaniu (do końca działania aplikacji). */
internal object MapStyleChoice {
    var current by mutableStateOf(MapStyleOption.MAP)
}
