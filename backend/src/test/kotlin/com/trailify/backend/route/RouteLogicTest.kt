package com.trailify.backend.route

import com.trailify.backend.common.ActivityType
import com.trailify.backend.common.GeoJsonLineString
import com.trailify.backend.web.ApiException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.UUID

class RouteLogicTest {

    private val userId = UUID.fromString("3f1c2b7e-0000-0000-0000-000000000000")
    private val line = GeoJsonLineString(coordinates = listOf(listOf(22.5684, 51.2465), listOf(22.5701, 51.248)))

    private fun create(
        activityId: Long? = null,
        geometry: GeoJsonLineString? = line,
        photos: List<PhotoInputRequest>? = null,
    ) = RouteCreateRequest(
        name = "Wokół Zalewu",
        description = null,
        activityType = ActivityType.CYCLING,
        isTourist = true,
        isScenic = false,
        isPublic = true,
        activityId = activityId,
        geometry = geometry,
        photos = photos,
    )

    private fun fields(request: RouteCreateRequest) = validateRouteCreate(userId, request).map { it.field }

    // --- POST /routes ---

    @Test
    fun `sama geometria jest poprawna`() {
        assertThat(validateRouteCreate(userId, create())).isEmpty()
    }

    @Test
    fun `sam activityId jest poprawny`() {
        assertThat(validateRouteCreate(userId, create(activityId = 5, geometry = null))).isEmpty()
    }

    @Test
    fun `trzeba podac dokladnie jedno z activityId i geometry`() {
        assertThat(fields(create(activityId = null, geometry = null))).containsExactly("geometry")
        assertThat(fields(create(activityId = 5, geometry = line))).containsExactly("geometry")
    }

    @Test
    fun `geometria z jednym punktem lub zlymi wspolrzednymi jest odrzucana`() {
        assertThat(fields(create(geometry = GeoJsonLineString(coordinates = listOf(listOf(22.5, 51.2)))))).containsExactly("geometry")
        // zamienione kolejnosc: szerokosc 200 nie istnieje
        assertThat(fields(create(geometry = GeoJsonLineString(coordinates = listOf(listOf(51.2, 200.0), listOf(22.5, 51.2))))))
            .containsExactly("geometry")
        assertThat(fields(create(geometry = GeoJsonLineString(type = "Point", coordinates = line.coordinates)))).containsExactly("geometry")
        assertThat(fields(create(geometry = GeoJsonLineString(coordinates = listOf(listOf(22.5), listOf(22.6, 51.2))))))
            .containsExactly("geometry")
    }

    @Test
    fun `klucz zdjecia musi zaczynac sie od id uzytkownika`() {
        val good = PhotoInputRequest("$userId/a.jpg", "https://cdn.example.com/a.jpg")
        assertThat(validateRouteCreate(userId, create(photos = listOf(good)))).isEmpty()

        val foreign = PhotoInputRequest("${UUID.randomUUID()}/a.jpg", "https://cdn.example.com/a.jpg")
        assertThat(fields(create(photos = listOf(foreign)))).containsExactly("photos")

        val traversal = PhotoInputRequest("$userId/../x.jpg", "https://cdn.example.com/a.jpg")
        assertThat(fields(create(photos = listOf(traversal)))).containsExactly("photos")
    }

    @Test
    fun `adres zdjecia musi byc https a zdjecia nie moga sie powtarzac`() {
        val http = PhotoInputRequest("$userId/a.jpg", "http://cdn.example.com/a.jpg")
        assertThat(fields(create(photos = listOf(http)))).containsExactly("photos")

        val same = PhotoInputRequest("$userId/a.jpg", "https://cdn.example.com/a.jpg")
        assertThat(fields(create(photos = listOf(same, same)))).containsExactly("photos")
    }

    @Test
    fun `dlugosc linii w metrach`() {
        // 0.01 stopnia szerokosci to ok. 1112 m
        assertThat(lengthMeters(listOf(listOf(22.0, 51.0), listOf(22.0, 51.01)))).isBetween(1110.0, 1114.0)
        assertThat(lengthMeters(listOf(listOf(22.0, 51.0), listOf(22.0, 51.0)))).isEqualTo(0.0)
    }

    // --- PATCH /routes/{id} ---

    @Test
    fun `patch bez pol niczego nie zmienia`() {
        val update = parseRouteUpdate(emptyMap())
        assertThat(update).isEqualTo(RouteUpdate(null, false, null, null, null, null, null))
    }

    @Test
    fun `patch odroznia brak opisu od opisu null`() {
        assertThat(parseRouteUpdate(mapOf("description" to null)).changeDescription).isTrue()
        assertThat(parseRouteUpdate(mapOf("description" to null)).description).isNull()
        assertThat(parseRouteUpdate(mapOf("name" to "Nowa")).changeDescription).isFalse()
    }

    @Test
    fun `patch przycina nazwe i sprawdza typ oraz flagi`() {
        val update = parseRouteUpdate(mapOf("name" to "  Nowa nazwa ", "activityType" to "RUNNING", "isPublic" to false))
        assertThat(update.name).isEqualTo("Nowa nazwa")
        assertThat(update.activityType).isEqualTo(ActivityType.RUNNING)
        assertThat(update.isPublic).isFalse()
        assertThat(update.isTourist).isNull()
    }

    @Test
    fun `patch odrzuca zle wartosci`() {
        val error = catchApiException {
            parseRouteUpdate(mapOf("name" to "  ", "activityType" to "FLYING", "isScenic" to "tak"))
        }
        assertThat(error.fieldErrors!!.map { it.field }).containsExactlyInAnyOrder("name", "activityType", "isScenic")
    }

    // --- GET /routes ---

    private fun filter(
        lat: Double? = null,
        lng: Double? = null,
        radiusKm: Double? = null,
        minDistance: Double? = null,
        maxDistance: Double? = null,
        minRating: Double? = null,
        query: String? = null,
        sort: String? = null,
    ) = buildSearchFilter(lat, lng, radiusKm, null, minDistance, maxDistance, minRating, null, null, query, sort)

    @Test
    fun `domyslnie ranking rekomendacji i promien 10 km`() {
        val f = filter(lat = 51.2, lng = 22.5)
        assertThat(f.sort).isEqualTo(RouteSort.RECOMMENDED)
        assertThat(f.radiusKm).isEqualTo(10.0)
        assertThat(f.hasLocation).isTrue()
    }

    @Test
    fun `lat i lng tylko razem`() {
        assertThat(catchApiException { filter(lat = 51.2) }.fieldErrors!!.map { it.field }).contains("lat")
        assertThat(catchApiException { filter(lng = 22.5) }.fieldErrors!!.map { it.field }).contains("lat")
        assertThat(catchApiException { filter(radiusKm = 5.0) }.fieldErrors!!.map { it.field }).contains("radiusKm")
    }

    @Test
    fun `sortowanie nearest wymaga lokalizacji`() {
        assertThat(catchApiException { filter(sort = "nearest") }.fieldErrors!!.map { it.field }).containsExactly("sort")
        assertThat(filter(lat = 51.2, lng = 22.5, sort = "NEAREST").sort).isEqualTo(RouteSort.NEAREST)
    }

    @Test
    fun `nieznane sortowanie i zle zakresy sa odrzucane`() {
        assertThat(catchApiException { filter(sort = "losowo") }.fieldErrors!!.map { it.field }).containsExactly("sort")
        assertThat(catchApiException { filter(lat = 51.2, lng = 22.5, radiusKm = 500.0) }.fieldErrors!!.map { it.field })
            .containsExactly("radiusKm")
        assertThat(catchApiException { filter(minDistance = 5000.0, maxDistance = 1000.0) }.fieldErrors!!.map { it.field })
            .containsExactly("minDistance")
        assertThat(catchApiException { filter(minRating = 6.0) }.fieldErrors!!.map { it.field }).containsExactly("minRating")
        assertThat(catchApiException { filter(query = "a".repeat(101)) }.fieldErrors!!.map { it.field }).containsExactly("q")
    }

    @Test
    fun `puste q jest ignorowane a typy sa bez duplikatow`() {
        val f = buildSearchFilter(
            null, null, null, listOf(ActivityType.CYCLING, ActivityType.CYCLING), null, null, null, null, null, "  ", null,
        )
        assertThat(f.query).isNull()
        assertThat(f.activityTypes).containsExactly(ActivityType.CYCLING)
    }

    @Test
    fun `znaki wieloznaczne w q sa zabezpieczone`() {
        assertThat(likePattern("lasy")).isEqualTo("%lasy%")
        assertThat(likePattern("100%_a\\b")).isEqualTo("%100\\%\\_a\\\\b%")
    }

    private fun catchApiException(block: () -> Unit): ApiException {
        var caught: ApiException? = null
        assertThatThrownBy(block).isInstanceOfSatisfying(ApiException::class.java) { caught = it }
        return caught!!
    }
}
