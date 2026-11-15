package com.trailify.backend.admin

import com.trailify.backend.common.DEFAULT_PAGE_SIZE
import com.trailify.backend.common.PageRequest
import com.trailify.backend.common.PageResponse
import com.trailify.backend.poi.PoiAdminResponse
import com.trailify.backend.poi.PoiCreateRequest
import com.trailify.backend.poi.PoiService
import com.trailify.backend.poi.RejectRequest
import com.trailify.backend.poi.STATUS_APPROVED
import com.trailify.backend.security.currentProfile
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Panel administratora (W29-W34). Dostęp tylko z rolą ADMIN: pilnuje tego SecurityConfig
 * (`/api/v1/admin/` wymaga ROLE_ADMIN), więc kontroler nie sprawdza roli sam.
 */
@RestController
@RequestMapping("/api/v1/admin")
class AdminController(
    private val pois: PoiService,
    private val users: AdminService,
) {

    // --- ciekawe punkty ---

    @GetMapping("/pois")
    fun listPois(
        @RequestParam("status", required = false) status: String?,
        @RequestParam("page", defaultValue = "0") page: Int,
        @RequestParam("size", defaultValue = "$DEFAULT_PAGE_SIZE") size: Int,
    ): PageResponse<PoiAdminResponse> = pois.listForAdmin(status, PageRequest.of(page, size))

    @PostMapping("/pois")
    @ResponseStatus(HttpStatus.CREATED)
    fun createPoi(@Valid @RequestBody body: PoiCreateRequest, authentication: Authentication): PoiAdminResponse =
        pois.create(authentication.currentProfile(), body, STATUS_APPROVED)

    @PatchMapping("/pois/{poiId}")
    fun updatePoi(@PathVariable poiId: Long, @RequestBody body: Map<String, Any?>): PoiAdminResponse =
        pois.update(poiId, body)

    @DeleteMapping("/pois/{poiId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deletePoi(@PathVariable poiId: Long) {
        pois.delete(poiId)
    }

    @PostMapping("/pois/{poiId}/approve")
    fun approvePoi(@PathVariable poiId: Long): PoiAdminResponse = pois.approve(poiId)

    @PostMapping("/pois/{poiId}/reject")
    fun rejectPoi(@PathVariable poiId: Long, @Valid @RequestBody(required = false) body: RejectRequest?): PoiAdminResponse =
        pois.reject(poiId, body?.reason)

    // --- użytkownicy ---

    @GetMapping("/users")
    fun listUsers(
        @RequestParam("q", required = false) q: String?,
        @RequestParam("page", defaultValue = "0") page: Int,
        @RequestParam("size", defaultValue = "$DEFAULT_PAGE_SIZE") size: Int,
    ): PageResponse<AdminUserResponse> = users.search(q, PageRequest.of(page, size))

    @DeleteMapping("/users/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteUser(@PathVariable userId: UUID, authentication: Authentication) {
        users.delete(authentication.currentProfile(), userId)
    }

    @PostMapping("/users/{userId}/block")
    fun blockUser(
        @PathVariable userId: UUID,
        @RequestBody(required = false) body: BlockRequest?,
        authentication: Authentication,
    ): AdminUserResponse = users.block(authentication.currentProfile(), userId, body?.deleteContent == true)

    @PostMapping("/users/{userId}/unblock")
    fun unblockUser(@PathVariable userId: UUID, authentication: Authentication): AdminUserResponse =
        users.unblock(authentication.currentProfile(), userId)
}
