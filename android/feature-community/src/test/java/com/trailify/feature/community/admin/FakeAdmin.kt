package com.trailify.feature.community.admin

import com.trailify.core.domain.model.AdminUser
import com.trailify.core.domain.model.Page
import com.trailify.core.domain.model.Poi
import com.trailify.core.domain.model.PoiAdmin
import com.trailify.core.domain.model.PoiDraft
import com.trailify.core.domain.model.PoiStatus
import com.trailify.core.domain.repository.AdminRepository
import java.time.Instant

internal class FakeAdmin : AdminRepository {
    val created = mutableListOf<PoiDraft>()
    val calls = mutableListOf<String>()
    var poiList = mutableListOf<PoiAdmin>()
    var userList = mutableListOf<AdminUser>()

    override suspend fun pois(status: PoiStatus?, page: Int, size: Int) =
        Page(poiList.filter { status == null || it.status == status }, 0, size, poiList.size.toLong(), 1)

    override suspend fun createPoi(draft: PoiDraft): PoiAdmin {
        created += draft
        return PoiAdmin(Poi(10, draft.name, draft.description, draft.latitude, draft.longitude, null), PoiStatus.APPROVED, null, null, Instant.EPOCH)
    }

    override suspend fun updatePoi(id: Long, name: String?, description: String?): PoiAdmin {
        calls += "update $id $name $description"
        return poiList.first { it.poi.id == id }
    }

    override suspend fun deletePoi(id: Long) {
        calls += "delete $id"
        poiList.removeAll { it.poi.id == id }
    }

    override suspend fun approvePoi(id: Long) {
        calls += "approve $id"
        poiList.replaceAll { if (it.poi.id == id) it.copy(status = PoiStatus.APPROVED) else it }
    }

    override suspend fun rejectPoi(id: Long, reason: String?) {
        calls += "reject $id $reason"
        poiList.replaceAll { if (it.poi.id == id) it.copy(status = PoiStatus.REJECTED, rejectionReason = reason) else it }
    }

    override suspend fun users(query: String?, page: Int, size: Int) =
        Page(userList.filter { query.isNullOrBlank() || it.profile.displayName.contains(query, true) }, 0, size, userList.size.toLong(), 1)

    override suspend fun blockUser(id: String, deleteContent: Boolean) {
        calls += "block $id $deleteContent"
    }

    override suspend fun unblockUser(id: String) {
        calls += "unblock $id"
    }

    override suspend fun deleteUser(id: String) {
        calls += "deleteUser $id"
    }
}
