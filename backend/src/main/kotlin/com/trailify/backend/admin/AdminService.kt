package com.trailify.backend.admin

import com.fasterxml.jackson.annotation.JsonUnwrapped
import com.trailify.backend.account.AccountService
import com.trailify.backend.account.ProfileLookup
import com.trailify.backend.account.ProfileRecord
import com.trailify.backend.account.ProfileRepository
import com.trailify.backend.account.ProfileResponse
import com.trailify.backend.account.ProfileStatsResponse
import com.trailify.backend.common.PageRequest
import com.trailify.backend.common.PageResponse
import com.trailify.backend.common.pageOf
import com.trailify.backend.web.ApiException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

const val STATUS_ACTIVE = "ACTIVE"
const val STATUS_BLOCKED = "BLOCKED"
const val ROLE_ADMIN = "ADMIN"

/** Schemat AdminUser z openapi.yaml: Profile plus status konta. */
data class AdminUserResponse(
    @get:JsonUnwrapped val profile: ProfileResponse,
    val status: String,
)

/** Treść POST /admin/users/{id}/block. */
data class BlockRequest(val deleteContent: Boolean? = false)

/**
 * Administrator nie blokuje ani nie usuwa samego siebie ani innego administratora
 * (żeby nie dało się przypadkiem odciąć dostępu do panelu). Zwraca wyjątek do rzucenia albo null.
 */
fun userActionRejection(actorId: UUID, target: ProfileRecord): ApiException? = when {
    target.id == actorId -> ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Nie możesz wykonać tej operacji na własnym koncie")
    target.role == ROLE_ADMIN -> ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Nie można zablokować ani usunąć administratora")
    else -> null
}

/** Zarządzanie kontami (W34). Blokada działa od razu: BlockedAccountFilter odrzuca każde żądanie z tokenem. */
@Service
class AdminService(
    private val users: AdminUserRepository,
    private val profiles: ProfileRepository,
    private val lookup: ProfileLookup,
    private val accounts: AccountService,
    private val transactions: TransactionTemplate,
) {

    fun search(query: String?, page: PageRequest): PageResponse<AdminUserResponse> {
        val (total, items) = users.search(query?.take(MAX_QUERY_LENGTH), page)
        return pageOf(page, total, items.map(::toResponse))
    }

    fun block(actor: ProfileRecord, userId: UUID, deleteContent: Boolean): AdminUserResponse {
        val target = targetOf(actor, userId)
        transactions.executeWithoutResult {
            users.setStatus(target.id, STATUS_BLOCKED)
            if (deleteContent) users.deleteContent(target.id)
        }
        return toResponse(lookup.findById(userId)!!)
    }

    fun unblock(actor: ProfileRecord, userId: UUID): AdminUserResponse {
        val target = targetOf(actor, userId)
        users.setStatus(target.id, STATUS_ACTIVE)
        return toResponse(lookup.findById(userId)!!)
    }

    /** Usunięcie konta z danymi: ta sama procedura co przy DELETE /me (AccountService). */
    fun delete(actor: ProfileRecord, userId: UUID) {
        val target = targetOf(actor, userId)
        accounts.deleteAccount(target.id)
    }

    private fun targetOf(actor: ProfileRecord, userId: UUID): ProfileRecord {
        val target = lookup.findById(userId)
            ?: throw ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Użytkownik nie istnieje")
        userActionRejection(actor.id, target)?.let { throw it }
        return target
    }

    private fun toResponse(profile: ProfileRecord): AdminUserResponse {
        val stats = profiles.stats(profile.id)
        return AdminUserResponse(
            profile = ProfileResponse(
                id = profile.id.toString(),
                email = profile.email,
                displayName = profile.displayName,
                avatarUrl = profile.avatarUrl,
                role = profile.role,
                createdAt = profile.createdAt.toInstant().toString(),
                stats = ProfileStatsResponse(
                    totalDistance = stats.totalDistance,
                    activityCount = stats.activityCount,
                    routeCount = stats.routeCount,
                    collectedPoiCount = stats.collectedPoiCount,
                ),
            ),
            status = profile.status,
        )
    }

    private companion object {
        const val MAX_QUERY_LENGTH = 100
    }
}
