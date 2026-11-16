package com.trailify.backend.social

import com.trailify.backend.common.PageRequest
import com.trailify.backend.route.UserSummaryResponse
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.util.UUID

/** Zapytania SQL do tabeli follow (V6). Zablokowanych użytkowników nie pokazujemy na listach. */
@Repository
class FollowRepository(private val jdbc: JdbcClient) {

    /** Ponowne obserwowanie nic nie zmienia. */
    fun follow(followerId: UUID, followeeId: UUID) {
        jdbc.sql("insert into follow (follower_id, followee_id) values (:follower, :followee) on conflict do nothing")
            .param("follower", followerId)
            .param("followee", followeeId)
            .update()
    }

    /** Odobserwowanie zawsze działa, także gdy nie obserwowaliśmy (wynik ten sam: nie obserwujemy). */
    fun unfollow(followerId: UUID, followeeId: UUID) {
        jdbc.sql("delete from follow where follower_id = :follower and followee_id = :followee")
            .param("follower", followerId)
            .param("followee", followeeId)
            .update()
    }

    fun isFollowing(followerId: UUID, followeeId: UUID): Boolean =
        jdbc.sql("select exists (select 1 from follow where follower_id = :follower and followee_id = :followee)")
            .param("follower", followerId)
            .param("followee", followeeId)
            .query(RowMapper<Boolean> { rs, _ -> rs.getBoolean(1) })
            .single()

    fun countFollowers(userId: UUID): Long = count("followee_id", "follower_id", userId)

    fun countFollowing(userId: UUID): Long = count("follower_id", "followee_id", userId)

    /** Kto obserwuje [userId], od najnowszych. */
    fun followers(userId: UUID, page: PageRequest): List<UserSummaryResponse> = list("followee_id", "follower_id", userId, page)

    /** Kogo obserwuje [userId], od najnowszych. */
    fun following(userId: UUID, page: PageRequest): List<UserSummaryResponse> = list("follower_id", "followee_id", userId, page)

    /** [ownColumn] to kolumna z id profilu, którego listę oglądamy, [otherColumn] kolumna z osobami na liście. */
    private fun count(ownColumn: String, otherColumn: String, userId: UUID): Long =
        jdbc.sql(
            """
            select count(*) from follow f join profile p on p.id = f.$otherColumn
            where f.$ownColumn = :id and p.status = 'ACTIVE'
            """.trimIndent(),
        )
            .param("id", userId)
            .query(RowMapper<Long> { rs, _ -> rs.getLong(1) })
            .single()

    private fun list(ownColumn: String, otherColumn: String, userId: UUID, page: PageRequest): List<UserSummaryResponse> =
        jdbc.sql(
            """
            select p.id, p.display_name, p.avatar_url
            from follow f join profile p on p.id = f.$otherColumn
            where f.$ownColumn = :id and p.status = 'ACTIVE'
            order by f.created_at desc, p.id
            limit :limit offset :offset
            """.trimIndent(),
        )
            .param("id", userId)
            .param("limit", page.size)
            .param("offset", page.offset)
            .query(
                RowMapper<UserSummaryResponse> { rs, _ ->
                    UserSummaryResponse(
                        id = rs.getString("id"),
                        displayName = rs.getString("display_name"),
                        avatarUrl = rs.getString("avatar_url"),
                    )
                },
            )
            .list()
}
