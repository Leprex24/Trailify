package com.trailify.backend.maplayer

import com.trailify.backend.routing.LatLon
import com.trailify.backend.web.ApiException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus

class MapLayerTest {

    private val lublin = GeoBox(51.20, 22.50, 51.25, 22.58)

    // --- prostokąt ---

    @Test
    fun `poprawny prostokat nie ma bledu`() {
        assertThat(boxError(lublin)).isNull()
        // duży kafelek aplikacji (0,15 x 0,24 stopnia) jest dozwolony
        assertThat(boxError(GeoBox(51.0, 22.0, 51.15, 22.24))).isNull()
    }

    @Test
    fun `bledne prostokaty sa odrzucane`() {
        assertThat(boxError(GeoBox(51.25, 22.5, 51.20, 22.6))).isNotNull() // odwrócony
        assertThat(boxError(GeoBox(51.2, 22.5, 51.2, 22.6))).isNotNull() // pusty
        assertThat(boxError(GeoBox(51.0, 22.0, 52.0, 22.1))).isNotNull() // za wysoki
        assertThat(boxError(GeoBox(51.0, 22.0, 51.1, 23.0))).isNotNull() // za szeroki
        assertThat(boxError(GeoBox(Double.NaN, 22.0, 51.1, 22.1))).isNotNull()
        assertThat(boxError(GeoBox(95.0, 22.0, 96.0, 22.1))).isNotNull() // poza zakresem
    }

    // --- zapytania ---

    @Test
    fun `zapytanie zawiera prostokat w kolejnosci poludnie zachod polnoc wschod`() {
        for (type in MapLayerType.entries) {
            val query = buildOverpassQuery(type, lublin)
            assertThat(query).contains("51.2,22.5,51.25,22.58")
            assertThat(query).startsWith("[out:json]")
            assertThat(query).contains("out skel geom")
        }
    }

    @Test
    fun `zapytania warstw szukaja wlasciwych obiektow`() {
        assertThat(buildOverpassQuery(MapLayerType.CYCLE_PATHS, lublin)).contains("\"highway\"=\"cycleway\"", "\"route\"=\"bicycle\"")
        assertThat(buildOverpassQuery(MapLayerType.HIKING_TRAILS, lublin)).contains("hiking|foot|walking")
        val forest = buildOverpassQuery(MapLayerType.FOREST_PATHS, lublin)
        assertThat(forest).contains("path|track|bridleway", "sidewalk")
        // znak końca tekstu w wyrażeniach regularnych Overpass ($") nie może zniknąć przy składaniu zapytania
        assertThat(forest).contains("^(path|track|bridleway)$\"")
    }

    // --- odpowiedź Overpass ---

    @Test
    fun `odpowiedz Overpass zamienia sie na linie`() {
        val body = mapOf(
            "elements" to listOf(
                mapOf("type" to "way", "geometry" to listOf(mapOf("lat" to 51.2, "lon" to 22.5), mapOf("lat" to 51.21, "lon" to 22.51))),
                mapOf("type" to "way", "geometry" to listOf(mapOf("lat" to 51.2, "lon" to 22.5))), // jeden punkt: pomijana
                mapOf("type" to "node"), // bez geometrii: pomijany
            ),
        )
        val lines = parseOverpassLines(body)
        assertThat(lines).hasSize(1)
        assertThat(lines[0]).containsExactly(LatLon(51.2, 22.5), LatLon(51.21, 22.51))
    }

    @Test
    fun `blad wykonania zapytania w odpowiedzi to niedostepnosc`() {
        val body = mapOf("elements" to emptyList<Any>(), "remark" to "runtime error: Query timed out in \"query\" at line 3")
        assertThatThrownBy { parseOverpassLines(body) }.isInstanceOf(OverpassUnavailableException::class.java)
    }

    @Test
    fun `brak elementow to pusta lista`() {
        assertThat(parseOverpassLines(emptyMap())).isEmpty()
    }

    // --- upraszczanie ---

    @Test
    fun `punkty na prostej sa usuwane, a koniec zostaje`() {
        val line = (0..10).map { LatLon(51.0 + it * 0.0001, 22.0) } // prosta na północ
        val simple = simplifyLine(line, 2.0)
        assertThat(simple).containsExactly(line.first(), line.last())
    }

    @Test
    fun `zakret wiekszy niz tolerancja zostaje`() {
        // 100 m na północ, potem 100 m na wschód
        val line = listOf(LatLon(51.0, 22.0), LatLon(51.0005, 22.0), LatLon(51.0009, 22.0), LatLon(51.0009, 22.0014), LatLon(51.0009, 22.0028))
        val simple = simplifyLine(line, 2.0)
        assertThat(simple).contains(LatLon(51.0009, 22.0))
        assertThat(simple.first()).isEqualTo(line.first())
        assertThat(simple.last()).isEqualTo(line.last())
    }

    @Test
    fun `krotkie linie nie sa zmieniane`() {
        val line = listOf(LatLon(51.0, 22.0), LatLon(51.1, 22.1))
        assertThat(simplifyLine(line, 2.0)).isEqualTo(line)
    }

    // --- pamięć podręczna ---

    @Test
    fun `wpis wygasa po czasie i najstarszy wypada przy przepelnieniu`() {
        var now = 0L
        val cache = TileCache<String>(maxEntries = 2, ttlMillis = 1000) { now }
        cache.put("a", "A")
        cache.put("b", "B")
        assertThat(cache.get("a")).isEqualTo("A") // "a" jest teraz ostatnio używany
        cache.put("c", "C") // przepełnienie: wypada "b"
        assertThat(cache.get("b")).isNull()
        assertThat(cache.get("c")).isEqualTo("C")
        now = 2000
        assertThat(cache.get("a")).isNull()
    }

    // --- serwis ---

    @Test
    fun `ponowne zapytanie o ten sam kafelek nie odpytuje Overpass`() {
        var calls = 0
        val service = MapLayerService {
            calls++
            listOf(listOf(LatLon(51.2, 22.5), LatLon(51.2005, 22.5004), LatLon(51.201, 22.5)))
        }
        val first = service.lines(MapLayerType.CYCLE_PATHS, lublin)
        val second = service.lines(MapLayerType.CYCLE_PATHS, lublin)
        assertThat(calls).isEqualTo(1)
        assertThat(second).isEqualTo(first)
        assertThat(first.lines).hasSize(1)
        assertThat(first.lines[0][0]).containsExactly(51.2, 22.5)

        service.lines(MapLayerType.HIKING_TRAILS, lublin) // inna warstwa: nowe zapytanie
        assertThat(calls).isEqualTo(2)
    }

    @Test
    fun `niedostepny Overpass daje 503`() {
        val service = MapLayerService { throw OverpassUnavailableException("nie dziala") }
        assertThatThrownBy { service.lines(MapLayerType.CYCLE_PATHS, lublin) }
            .isInstanceOfSatisfying(ApiException::class.java) {
                assertThat(it.status).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                assertThat(it.code).isEqualTo("MAP_LAYER_UNAVAILABLE")
            }
    }

    @Test
    fun `zbyt duzy prostokat daje 400 i nie odpytuje Overpass`() {
        var calls = 0
        val service = MapLayerService { calls++; emptyList() }
        assertThatThrownBy { service.lines(MapLayerType.CYCLE_PATHS, GeoBox(50.0, 20.0, 52.0, 24.0)) }
            .isInstanceOfSatisfying(ApiException::class.java) {
                assertThat(it.status).isEqualTo(HttpStatus.BAD_REQUEST)
                assertThat(it.code).isEqualTo("VALIDATION_ERROR")
            }
        assertThat(calls).isEqualTo(0)
    }
}
