package com.trailify.backend.poi

import com.trailify.backend.route.lengthMeters
import com.trailify.backend.route.validatePhotoInputs
import com.trailify.backend.route.validationError
import com.trailify.backend.web.ApiException
import com.trailify.backend.web.ApiFieldError
import org.springframework.http.HttpStatus
import java.util.UUID

/** Maksymalna odległość od punktu przy zbieraniu (opis POST /pois/{id}/collect). */
const val COLLECT_RADIUS_METERS = 50.0

/** Największy obszar mapy, dla którego zwracamy punkty (ok. 100 x 100 km), żeby nie pobierać całej bazy. */
const val MAX_BBOX_DEGREES = 1.0

const val CODE_POI_TOO_FAR = "POI_TOO_FAR"
const val CODE_ALREADY_COLLECTED = "ALREADY_COLLECTED"

const val STATUS_PENDING = "PENDING"
const val STATUS_APPROVED = "APPROVED"
const val STATUS_REJECTED = "REJECTED"

data class BoundingBox(val minLng: Double, val minLat: Double, val maxLng: Double, val maxLat: Double)

/** Parametr `bbox` w formacie `minLng,minLat,maxLng,maxLat`. Przy błędzie rzuca VALIDATION_ERROR. */
fun parseBbox(value: String?): BoundingBox {
    val parts = value?.split(",")?.map { it.trim().toDoubleOrNull() }
    val error = validationError(listOf(ApiFieldError("bbox", "Oczekiwano bbox=minLng,minLat,maxLng,maxLat")))
    if (parts == null || parts.size != 4 || parts.any { it == null || !it.isFinite() }) throw error
    val box = BoundingBox(parts[0]!!, parts[1]!!, parts[2]!!, parts[3]!!)
    val valid = box.minLng in -180.0..180.0 && box.maxLng in -180.0..180.0 &&
        box.minLat in -90.0..90.0 && box.maxLat in -90.0..90.0 &&
        box.minLng <= box.maxLng && box.minLat <= box.maxLat
    if (!valid) throw error
    if (box.maxLng - box.minLng > MAX_BBOX_DEGREES || box.maxLat - box.minLat > MAX_BBOX_DEGREES) {
        throw validationError(listOf(ApiFieldError("bbox", "Obszar jest za duży, przybliż mapę")))
    }
    return box
}

/** Odległość w metrach między dwoma punktami (szerokość, długość). */
fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double =
    lengthMeters(listOf(listOf(lng1, lat1), listOf(lng2, lat2)))

/** Zasady zbierania (W23): tylko w promieniu 50 m i tylko raz. Zwraca wyjątek do rzucenia albo null. */
fun collectRejection(distance: Double, alreadyCollected: Boolean): ApiException? = when {
    alreadyCollected -> ApiException(HttpStatus.CONFLICT, CODE_ALREADY_COLLECTED, "Ten punkt jest już w Twojej kolekcji")
    distance > COLLECT_RADIUS_METERS -> ApiException(
        HttpStatus.UNPROCESSABLE_CONTENT,
        CODE_POI_TOO_FAR,
        "Jesteś za daleko od punktu (${distance.toInt()} m, maksymalnie ${COLLECT_RADIUS_METERS.toInt()} m)",
    )
    else -> null
}

/** Zdjęcie punktu musi leżeć w folderze zgłaszającego (jak przy trasach i recenzjach). */
fun validatePoiCreate(userId: UUID, request: PoiCreateRequest): List<ApiFieldError> =
    validatePhotoInputs(userId, listOfNotNull(request.photo)).map { it.copy(field = "photo") }

/** Edycja punktu przez administratora (PoiUpdate): pola nieprzesłane zostają bez zmian. */
data class PoiUpdate(
    val name: String?,
    val description: String?,
    val latitude: Double?,
    val longitude: Double?,
    val category: String? = null,
)

/** Sprawdza treść PATCH /admin/pois/{id}. Przy błędach rzuca VALIDATION_ERROR z listą pól. */
fun parsePoiUpdate(body: Map<String, Any?>): PoiUpdate {
    val errors = mutableListOf<ApiFieldError>()

    fun text(field: String, max: Int): String? {
        if (field !in body) return null
        val value = (body[field] as? String)?.trim()
        if (value.isNullOrEmpty() || value.length > max) {
            errors.add(ApiFieldError(field, "Pole musi mieć od 1 do $max znaków"))
            return null
        }
        return value
    }

    fun coordinate(field: String, limit: Double): Double? {
        if (field !in body) return null
        val value = (body[field] as? Number)?.toDouble()
        if (value == null || !value.isFinite() || value !in -limit..limit) {
            errors.add(ApiFieldError(field, "Niepoprawna współrzędna"))
            return null
        }
        return value
    }

    fun category(): String? {
        if ("category" !in body) return null
        val value = parsePoiCategory(body["category"])
        if (value == null) errors.add(ApiFieldError("category", "Dozwolone: ${PoiCategory.entries.joinToString()}"))
        return value?.name
    }

    val update = PoiUpdate(
        name = text("name", POI_NAME_MAX),
        description = text("description", POI_DESCRIPTION_MAX),
        latitude = coordinate("latitude", 90.0),
        longitude = coordinate("longitude", 180.0),
        category = category(),
    )
    if (errors.isNotEmpty()) throw validationError(errors)
    return update
}
