package com.trailify.backend.account

import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Types
import java.time.OffsetDateTime
import java.util.UUID

/** Wiersz tabeli `profile` (V1). */
data class ProfileRecord(
    val id: UUID,
    val email: String,
    val displayName: String,
    val avatarUrl: String?,
    /** `USER` albo `ADMIN`. */
    val role: String,
    /** `ACTIVE` albo `BLOCKED`. */
    val status: String,
    val createdAt: OffsetDateTime,
) {
    val isBlocked: Boolean get() = status == STATUS_BLOCKED

    companion object {
        const val STATUS_BLOCKED = "BLOCKED"
    }
}

/** Liczby do ekranu profilu (schemat ProfileStats z openapi.yaml). */
data class ProfileStatsRecord(
    val totalDistance: Double,
    val activityCount: Long,
    val routeCount: Long,
    val collectedPoiCount: Long,
)

/** Wąski interfejs, żeby logikę bezpieczeństwa dało się testować bez bazy. */
interface ProfileLookup {
    fun findById(id: UUID): ProfileRecord?
}

@Repository
class ProfileRepository(private val jdbc: JdbcClient) : ProfileLookup {

    override fun findById(id: UUID): ProfileRecord? =
        jdbc.sql(
            """
            select id, email, display_name, avatar_url, role, status, created_at
            from profile
            where id = :id
            """.trimIndent(),
        )
            .param("id", id)
            .query(profileMapper)
            .optional()
            .orElse(null)

    /** Zmienia tylko te pola, które przyszły w żądaniu (patrz [ProfileUpdate]). */
    fun update(id: UUID, update: ProfileUpdate) {
        val sets = mutableListOf<String>()
        if (update.displayName != null) sets.add("display_name = :displayName")
        if (update.changeAvatar) sets.add("avatar_url = :avatarUrl")
        if (sets.isEmpty()) return
        jdbc.sql("update profile set ${sets.joinToString()} where id = :id")
            .param("id", id)
            .param("displayName", update.displayName, Types.VARCHAR)
            .param("avatarUrl", update.avatarUrl, Types.VARCHAR)
            .update()
    }

    /**
     * Punkty zgłoszone przez użytkownika i jeszcze niezatwierdzone (oczekujące, odrzucone) znikają razem z kontem.
     * Zatwierdzone zostają anonimowo (klucz obcy ma on delete set null, patrz V3).
     */
    fun deleteUnpublishedPoiSubmissions(userId: UUID) {
        jdbc.sql("delete from point_of_interest where submitted_by = :id and status in ('PENDING', 'REJECTED')")
            .param("id", userId)
            .update()
    }

    fun stats(userId: UUID): ProfileStatsRecord =
        jdbc.sql(
            """
            select
                coalesce((select sum(distance) from activity where user_id = :id), 0) as total_distance,
                (select count(*) from activity where user_id = :id)                  as activity_count,
                (select count(*) from route where user_id = :id)                     as route_count,
                (select count(*) from poi_collection where user_id = :id)            as collected_poi_count
            """.trimIndent(),
        )
            .param("id", userId)
            .query(
                RowMapper<ProfileStatsRecord> { rs, _ ->
                    ProfileStatsRecord(
                        totalDistance = rs.getDouble("total_distance"),
                        activityCount = rs.getLong("activity_count"),
                        routeCount = rs.getLong("route_count"),
                        collectedPoiCount = rs.getLong("collected_poi_count"),
                    )
                },
            )
            .single()

    private companion object {
        val profileMapper = RowMapper<ProfileRecord> { rs, _ ->
            ProfileRecord(
                id = rs.getObject("id", UUID::class.java),
                email = rs.getString("email"),
                displayName = rs.getString("display_name"),
                avatarUrl = rs.getString("avatar_url"),
                role = rs.getString("role"),
                status = rs.getString("status"),
                createdAt = rs.getObject("created_at", OffsetDateTime::class.java),
            )
        }
    }
}
