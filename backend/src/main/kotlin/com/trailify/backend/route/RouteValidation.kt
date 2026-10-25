package com.trailify.backend.route

import com.trailify.backend.common.ActivityType
import com.trailify.backend.web.ApiException
import com.trailify.backend.web.ApiFieldError
import org.springframework.http.HttpStatus
import java.net.URI
import java.util.UUID
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

const val MAX_QUERY_LENGTH = 100
const val DEFAULT_RADIUS_KM = 10.0
const val MIN_RADIUS_KM = 0.1
const val MAX_RADIUS_KM = 200.0
const val NAME_MAX = 255
const val DESCRIPTION_MAX = 5000

fun validationError(errors: List<ApiFieldError>): ApiException =
    ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Niepoprawne dane", errors)

/** Długość linii w metrach (suma odcinków, wzór haversine). Współrzędne to [lng, lat]. */
fun lengthMeters(coordinates: List<List<Double>>): Double =
    coordinates.zipWithNext().sumOf { (a, b) -> haversine(a[1], a[0], b[1], b[0]) }

private fun haversine(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
    return 2 * 6_371_000.0 * asin(sqrt(h))
}

/**
 * Reguły dla POST /routes, których nie da się wyrazić adnotacjami: dokładnie jedno z pól
 * `activityId` i `geometry`, poprawność współrzędnych i zdjęć. Zwraca listę błędów (pustą, gdy OK).
 */
fun validateRouteCreate(userId: UUID, request: RouteCreateRequest): List<ApiFieldError> {
    val errors = mutableListOf<ApiFieldError>()

    if ((request.activityId == null) == (request.geometry == null)) {
        errors.add(ApiFieldError("geometry", "Podaj dokładnie jedno z pól: activityId albo geometry"))
    }

    request.geometry?.let { geometry ->
        val coordinates = geometry.coordinates
        when {
            geometry.type != "LineString" -> errors.add(ApiFieldError("geometry", "Geometria musi być typu LineString"))
            coordinates.size < 2 || coordinates.size > MAX_ROUTE_POINTS ->
                errors.add(ApiFieldError("geometry", "Trasa musi mieć od 2 do $MAX_ROUTE_POINTS punktów"))
            coordinates.any { !isValidCoordinate(it) } ->
                errors.add(ApiFieldError("geometry", "Niepoprawne współrzędne: oczekiwano [długość, szerokość] w poprawnych zakresach"))
        }
    }

    val photos = request.photos.orEmpty()
    val prefix = "$userId/"
    if (photos.any { it.storageKey != null && (!it.storageKey.startsWith(prefix) || it.storageKey.contains("..")) }) {
        errors.add(ApiFieldError("photos", "Klucz zdjęcia musi zaczynać się od identyfikatora użytkownika"))
    }
    if (photos.any { it.url != null && !isValidHttpsUrl(it.url) }) {
        errors.add(ApiFieldError("photos", "Adres zdjęcia musi być poprawnym adresem https"))
    }
    if (photos.mapNotNull { it.storageKey }.let { it.size != it.toSet().size }) {
        errors.add(ApiFieldError("photos", "Zdjęcia nie mogą się powtarzać"))
    }
    return errors
}

private fun isValidCoordinate(c: List<Double>): Boolean =
    c.size in 2..3 && c.all { it.isFinite() } && c[0] in -180.0..180.0 && c[1] in -90.0..90.0

fun isValidHttpsUrl(url: String): Boolean {
    if (url.length > 1024) return false
    return try {
        val uri = URI(url)
        uri.scheme == "https" && !uri.host.isNullOrBlank()
    } catch (e: Exception) {
        false
    }
}

/**
 * Edycja metadanych trasy (schemat RouteUpdate). Pola nieprzesłane zostają bez zmian,
 * a `description: null` usuwa opis, dlatego odróżniamy "brak pola" od "pole = null".
 */
data class RouteUpdate(
    val name: String?,
    val changeDescription: Boolean,
    val description: String?,
    val activityType: ActivityType?,
    val isTourist: Boolean?,
    val isScenic: Boolean?,
    val isPublic: Boolean?,
)

/** Sprawdza treść PATCH /routes/{id}. Przy błędach rzuca ApiException 400 z listą pól. */
fun parseRouteUpdate(body: Map<String, Any?>): RouteUpdate {
    val errors = mutableListOf<ApiFieldError>()

    var name: String? = null
    if (body.containsKey("name")) {
        val raw = body["name"]
        val trimmed = (raw as? String)?.trim()
        if (trimmed.isNullOrEmpty() || trimmed.length > NAME_MAX) {
            errors.add(ApiFieldError("name", "Nazwa musi mieć od 1 do $NAME_MAX znaków"))
        } else {
            name = trimmed
        }
    }

    val changeDescription = body.containsKey("description")
    var description: String? = null
    if (changeDescription) {
        val raw = body["description"]
        when {
            raw == null -> description = null
            raw !is String || raw.length > DESCRIPTION_MAX ->
                errors.add(ApiFieldError("description", "Opis może mieć najwyżej $DESCRIPTION_MAX znaków"))
            else -> description = raw.trim().ifEmpty { null }
        }
    }

    var activityType: ActivityType? = null
    if (body.containsKey("activityType")) {
        activityType = (body["activityType"] as? String)?.let { value -> ActivityType.entries.firstOrNull { it.name == value } }
        if (activityType == null) errors.add(ApiFieldError("activityType", "Dozwolone: CYCLING, RUNNING, WALKING"))
    }

    fun flag(field: String): Boolean? {
        if (!body.containsKey(field)) return null
        val raw = body[field]
        if (raw !is Boolean) {
            errors.add(ApiFieldError(field, "Wartość musi być true albo false"))
            return null
        }
        return raw
    }

    val isTourist = flag("isTourist")
    val isScenic = flag("isScenic")
    val isPublic = flag("isPublic")

    if (errors.isNotEmpty()) throw validationError(errors)
    return RouteUpdate(name, changeDescription, description, activityType, isTourist, isScenic, isPublic)
}

/** Sortowanie wyników wyszukiwania (parametr `sort`); [orderBy] to gotowy fragment SQL, nigdy z wejścia użytkownika. */
enum class RouteSort(val orderBy: String) {
    RECOMMENDED("s.recommendation_score desc, s.review_count desc, r.created_at desc, r.id desc"),
    RATING("s.average_rating desc nulls last, s.review_count desc, r.id desc"),
    NEWEST("r.created_at desc, r.id desc"),
    NEAREST("distance_from_query asc, r.id desc"),

    /** Od najkrótszej. */
    DISTANCE("r.total_distance asc nulls last, r.id desc"),
    ;

    companion object {
        fun parse(value: String?): RouteSort? =
            if (value == null) RECOMMENDED else entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }
}

/** Zwalidowane kryteria wyszukiwania tras (GET /routes). */
data class RouteSearchFilter(
    val lat: Double?,
    val lng: Double?,
    val radiusKm: Double,
    val activityTypes: List<ActivityType>,
    val minDistance: Double?,
    val maxDistance: Double?,
    val minRating: Double?,
    val isTourist: Boolean?,
    val isScenic: Boolean?,
    val query: String?,
    val sort: RouteSort,
) {
    val hasLocation: Boolean get() = lat != null && lng != null
}

@Suppress("LongParameterList")
fun buildSearchFilter(
    lat: Double?,
    lng: Double?,
    radiusKm: Double?,
    activityTypes: List<ActivityType>?,
    minDistance: Double?,
    maxDistance: Double?,
    minRating: Double?,
    isTourist: Boolean?,
    isScenic: Boolean?,
    query: String?,
    sort: String?,
): RouteSearchFilter {
    val errors = mutableListOf<ApiFieldError>()

    if ((lat == null) != (lng == null)) {
        errors.add(ApiFieldError("lat", "Parametry lat i lng podaje się razem"))
    }
    if (lat != null && lat !in -90.0..90.0) errors.add(ApiFieldError("lat", "Szerokość musi być z zakresu -90..90"))
    if (lng != null && lng !in -180.0..180.0) errors.add(ApiFieldError("lng", "Długość musi być z zakresu -180..180"))
    if (radiusKm != null && lat == null) {
        errors.add(ApiFieldError("radiusKm", "Promień ma sens tylko razem z lat i lng"))
    }
    if (radiusKm != null && radiusKm !in MIN_RADIUS_KM..MAX_RADIUS_KM) {
        errors.add(ApiFieldError("radiusKm", "Promień musi być z zakresu $MIN_RADIUS_KM..$MAX_RADIUS_KM km"))
    }
    if (minDistance != null && minDistance < 0) errors.add(ApiFieldError("minDistance", "Dystans nie może być ujemny"))
    if (maxDistance != null && maxDistance < 0) errors.add(ApiFieldError("maxDistance", "Dystans nie może być ujemny"))
    if (minDistance != null && maxDistance != null && minDistance > maxDistance) {
        errors.add(ApiFieldError("minDistance", "minDistance nie może być większe niż maxDistance"))
    }
    if (minRating != null && minRating !in 1.0..5.0) errors.add(ApiFieldError("minRating", "Ocena musi być z zakresu 1..5"))
    if (query != null && query.length > MAX_QUERY_LENGTH) {
        errors.add(ApiFieldError("q", "Fraza może mieć najwyżej $MAX_QUERY_LENGTH znaków"))
    }

    val parsedSort = RouteSort.parse(sort)
    if (parsedSort == null) {
        errors.add(ApiFieldError("sort", "Dozwolone: recommended, rating, newest, nearest, distance"))
    } else if (parsedSort == RouteSort.NEAREST && lat == null) {
        errors.add(ApiFieldError("sort", "Sortowanie nearest wymaga parametrów lat i lng"))
    }

    if (errors.isNotEmpty()) throw validationError(errors)
    return RouteSearchFilter(
        lat = lat,
        lng = lng,
        radiusKm = radiusKm ?: DEFAULT_RADIUS_KM,
        activityTypes = activityTypes.orEmpty().distinct(),
        minDistance = minDistance,
        maxDistance = maxDistance,
        minRating = minRating,
        isTourist = isTourist,
        isScenic = isScenic,
        query = query?.trim()?.takeIf { it.isNotEmpty() },
        sort = parsedSort!!,
    )
}

/** Zamienia fragment nazwy na wzorzec dla `ilike ... escape '\'`, żeby `%` i `_` z wejścia nie działały jak symbole wieloznaczne. */
fun likePattern(query: String): String =
    "%" + query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
