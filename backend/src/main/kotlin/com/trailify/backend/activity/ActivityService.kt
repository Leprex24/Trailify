package com.trailify.backend.activity

import com.trailify.backend.common.PageRequest
import com.trailify.backend.common.PageResponse
import com.trailify.backend.common.pageOf
import com.trailify.backend.feed.FeedRepository
import com.trailify.backend.web.ApiException
import com.trailify.backend.web.ApiFieldError
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

/** Wynik zapisu: `created = false` oznacza, że aktywność z tym `clientId` już była (odpowiedź 200). */
data class CreateActivityResult(val activity: ActivityResponse, val created: Boolean)

@Service
class ActivityService(
    private val repository: ActivityRepository,
    private val feed: FeedRepository,
    private val transactions: TransactionTemplate,
) {

    fun create(userId: UUID, request: ActivityCreateRequest): CreateActivityResult {
        validate(userId, request)

        // Ponowna wysyłka tej samej aktywności (np. po zerwaniu połączenia) nie tworzy duplikatu.
        repository.findByClientId(userId, request.clientId!!)?.let { return CreateActivityResult(it, created = false) }

        val id = try {
            // aktywność i wszystkie jej punkty zapisują się razem albo wcale
            transactions.execute {
                val activityId = repository.insert(userId, request)
                // udostępniona od razu aktywność trafia na oś czasu obserwujących
                if (request.isShared == true) feed.addActivity(activityId)
                activityId
            }!!
        } catch (e: DuplicateKeyException) {
            // dwa równoległe żądania z tym samym clientId: wygrało drugie, zwracamy jego wynik
            val existing = repository.findByClientId(userId, request.clientId)
                ?: throw ApiException(HttpStatus.CONFLICT, "CONFLICT", "Aktywność o tym clientId już istnieje")
            return CreateActivityResult(existing, created = false)
        }
        return CreateActivityResult(repository.findOwned(userId, id)!!, created = true)
    }

    /** Własna aktywność albo cudza udostępniona (przez osobę z nieprywatnym profilem). Inne to 404. */
    fun get(viewerId: UUID, activityId: Long): ActivityResponse =
        repository.findViewable(viewerId, activityId) ?: notFound()

    fun points(viewerId: UUID, activityId: Long): List<GpsPointResponse> {
        repository.findViewable(viewerId, activityId) ?: notFound()
        return repository.listPoints(activityId)
    }

    /** Udostępnia (true) albo ukrywa (false) własną aktywność. Ukrycie chowa ją też z osi czasu (widoczność sprawdza zapytanie). */
    fun setShared(userId: UUID, activityId: Long, shared: Boolean) {
        transactions.executeWithoutResult {
            if (!repository.setShared(userId, activityId, shared)) notFound()
            if (shared) feed.addActivity(activityId)
        }
    }

    fun delete(userId: UUID, activityId: Long) {
        if (!repository.delete(userId, activityId)) notFound()
    }

    fun listMine(userId: UUID, page: PageRequest, range: ActivityRange = ActivityRange.ALL): PageResponse<ActivitySummaryResponse> =
        pageOf(page, repository.countByUser(userId, range), repository.listByUser(userId, page, range))

    private fun validate(userId: UUID, request: ActivityCreateRequest) {
        val errors = validateActivityFields(request).toMutableList()
        val routeId = request.routeId
        if (errors.isEmpty() && routeId != null && !repository.routeVisibleTo(userId, routeId)) {
            errors.add(ApiFieldError("routeId", "Trasa nie istnieje lub jest niedostępna"))
        }
        if (errors.isNotEmpty()) {
            throw ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Niepoprawne dane", errors)
        }
    }

    private fun notFound(): Nothing =
        throw ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Aktywność nie istnieje")
}

/** Reguły, których nie da się wyrazić adnotacjami: zależności między polami. Zwraca listę błędów (pustą, gdy OK). */
fun validateActivityFields(request: ActivityCreateRequest): List<ApiFieldError> {
    val errors = mutableListOf<ApiFieldError>()
    val start = request.startTime
    val end = request.endTime
    if (start != null && end != null && end.isBefore(start)) {
        errors.add(ApiFieldError("endTime", "Koniec aktywności nie może być przed jej początkiem"))
    }
    return errors
}
