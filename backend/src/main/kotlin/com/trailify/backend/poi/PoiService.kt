package com.trailify.backend.poi

import com.trailify.backend.account.ProfileRecord
import com.trailify.backend.common.PageRequest
import com.trailify.backend.common.PageResponse
import com.trailify.backend.common.pageOf
import com.trailify.backend.route.validationError
import com.trailify.backend.web.ApiException
import com.trailify.backend.web.ApiFieldError
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate

@Service
class PoiService(
    private val repository: PoiRepository,
    private val transactions: TransactionTemplate,
) {

    /** Zatwierdzone punkty w widocznym obszarze mapy (W4). */
    fun inBox(bbox: String?): List<PoiResponse> = repository.approvedInBox(parseBbox(bbox))

    /** Szczegóły punktu: publicznie widać tylko zatwierdzone. */
    fun get(id: Long): PoiResponse {
        val poi = repository.findById(id) ?: notFound()
        if (poi.status != STATUS_APPROVED) notFound()
        return poi.poi
    }

    /** Zgłoszenie użytkownika (W22) trafia do moderacji jako PENDING. Administrator dodaje od razu APPROVED (W31). */
    fun create(user: ProfileRecord, request: PoiCreateRequest, status: String): PoiAdminResponse {
        val errors = validatePoiCreate(user.id, request)
        if (errors.isNotEmpty()) throw validationError(errors)
        val id = try {
            transactions.execute {
                val poiId = repository.insert(user.id, request, status)
                request.photo?.let { repository.replacePhoto(poiId, it.storageKey!!, it.url!!) }
                poiId
            }!!
        } catch (e: DuplicateKeyException) {
            throw ApiException(HttpStatus.CONFLICT, "CONFLICT", "Zdjęcie o tym kluczu jest już użyte")
        }
        return repository.findById(id)!!
    }

    /**
     * Zebranie punktu (W23). Telefon wykrywa bliskość sam (nearbyPois), a serwer jeszcze raz sprawdza
     * odległość, żeby nie dało się zebrać punktu z kanapy.
     */
    fun collect(user: ProfileRecord, poiId: Long, request: CollectRequest): PoiCollectionEntryResponse {
        val poi = repository.findById(poiId)?.takeIf { it.status == STATUS_APPROVED } ?: notFound()
        val distance = distanceMeters(request.latitude!!, request.longitude!!, poi.poi.latitude, poi.poi.longitude)
        collectRejection(distance, repository.isCollected(user.id, poiId))?.let { throw it }
        val collectedAt = try {
            repository.collect(user.id, poiId)
        } catch (e: DuplicateKeyException) {
            // dwa równoczesne zebrania tego samego punktu
            throw collectRejection(0.0, alreadyCollected = true)!!
        }
        return PoiCollectionEntryResponse(poi.poi, collectedAt.toInstant().toString())
    }

    fun myCollection(user: ProfileRecord): List<PoiCollectionEntryResponse> = repository.collectionOf(user.id)

    // --- moderacja (W29-W32, tylko ADMIN; rolę sprawdza SecurityConfig) ---

    fun listForAdmin(status: String?, page: PageRequest): PageResponse<PoiAdminResponse> {
        if (status != null && status !in listOf(STATUS_PENDING, STATUS_APPROVED, STATUS_REJECTED)) {
            throw validationError(listOf(ApiFieldError("status", "Dozwolone: PENDING, APPROVED, REJECTED")))
        }
        val (total, items) = repository.listForAdmin(status, page)
        return pageOf(page, total, items)
    }

    fun update(poiId: Long, body: Map<String, Any?>): PoiAdminResponse {
        val update = parsePoiUpdate(body)
        repository.findById(poiId) ?: notFound()
        repository.update(poiId, update)
        return repository.findById(poiId)!!
    }

    fun approve(poiId: Long): PoiAdminResponse {
        repository.findById(poiId) ?: notFound()
        repository.setStatus(poiId, STATUS_APPROVED, rejectionReason = null)
        return repository.findById(poiId)!!
    }

    fun reject(poiId: Long, reason: String?): PoiAdminResponse {
        val poi = repository.findById(poiId) ?: notFound()
        if (poi.status == STATUS_APPROVED) {
            // zatwierdzony punkt mógł już trafić do kolekcji: zamiast odrzucać, administrator go usuwa
            throw ApiException(HttpStatus.CONFLICT, "CONFLICT", "Zatwierdzonego punktu nie można odrzucić, można go usunąć")
        }
        repository.setStatus(poiId, STATUS_REJECTED, reason?.trim()?.ifEmpty { null })
        return repository.findById(poiId)!!
    }

    fun delete(poiId: Long) {
        repository.findById(poiId) ?: notFound()
        repository.delete(poiId)
    }

    private fun notFound(): Nothing = throw ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Punkt nie istnieje")
}
