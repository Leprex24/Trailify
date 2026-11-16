package com.trailify.backend.admin

import com.trailify.backend.account.ProfileRecord
import com.trailify.backend.common.PageRequest
import com.trailify.backend.route.likePattern
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.util.UUID

/** Zapytania administratora do tabeli profile oraz usuwanie treści użytkownika (W34). */
@Repository
class AdminUserRepository(private val jdbc: JdbcClient) {

    /** Wyszukiwanie po fragmencie e-maila lub nazwy, od najnowszego konta. */
    fun search(query: String?, page: PageRequest): Pair<Long, List<ProfileRecord>> {
        val pattern = query?.trim()?.takeIf { it.isNotEmpty() }?.let { likePattern(it) }
        val where = if (pattern != null) "where email ilike :q escape '\\' or display_name ilike :q escape '\\'" else ""
        val total = jdbc.sql("select count(*) from profile $where")
            .let { if (pattern != null) it.param("q", pattern) else it }
            .query(Long::class.java)
            .single()
        val items = jdbc.sql(
            """
            select id, email, display_name, avatar_url, background_url, is_private, role, status, created_at
            from profile $where
            order by created_at desc, id
            limit :limit offset :offset
            """.trimIndent(),
        )
            .let { if (pattern != null) it.param("q", pattern) else it }
            .param("limit", page.size)
            .param("offset", page.offset)
            .query(profileMapper)
            .list()
        return total to items
    }

    fun setStatus(userId: UUID, status: String) {
        jdbc.sql("update profile set status = :status where id = :id")
            .param("id", userId)
            .param("status", status)
            .update()
    }

    /**
     * Usuwa treści naruszające regulamin: opublikowane trasy, recenzje i niezatwierdzone zgłoszenia punktów.
     * Aktywności są prywatne, więc zostają. Zdjęcia w bazie usuwa on delete cascade.
     */
    fun deleteContent(userId: UUID) {
        jdbc.sql("delete from review where user_id = :id").param("id", userId).update()
        jdbc.sql("delete from route where user_id = :id").param("id", userId).update()
        jdbc.sql("delete from point_of_interest where submitted_by = :id and status in ('PENDING', 'REJECTED')")
            .param("id", userId)
            .update()
    }

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
                backgroundUrl = rs.getString("background_url"),
                isPrivate = rs.getBoolean("is_private"),
            )
        }
    }
}
