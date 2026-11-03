package com.trailify.backend.review

import com.trailify.backend.common.PageRequest
import com.trailify.backend.route.PhotoInputRequest
import com.trailify.backend.route.PhotoResponse
import com.trailify.backend.route.UserSummaryResponse
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.Types
import java.time.OffsetDateTime
import java.util.UUID

/** Autor i trasa recenzji (do sprawdzania uprawnień przy usuwaniu). */
data class ReviewOwnership(val authorId: UUID, val routeId: Long)

/** Zapytania SQL do tabel review i photo (V3). Średnią i ranking trasy liczy widok route_rating_stats (V4). */
@Repository
class ReviewRepository(
    private val jdbc: JdbcClient,
    private val jdbcTemplate: JdbcTemplate,
) {

    /** Zapisuje recenzję (wywoływane wewnątrz transakcji). Zwraca id. */
    fun insert(userId: UUID, routeId: Long, request: ReviewCreateRequest): Long =
        jdbc.sql(
            """
            insert into review (user_id, route_id, rating, comment)
            values (:userId, :routeId, :rating, :comment)
            returning id
            """.trimIndent(),
        )
            .param("userId", userId)
            .param("routeId", routeId)
            .param("rating", request.rating!!)
            .param("comment", request.comment?.trim()?.ifEmpty { null }, Types.VARCHAR)
            .query(RowMapper<Long> { rs, _ -> rs.getLong(1) })
            .single()

    fun insertPhotos(reviewId: Long, photos: List<PhotoInputRequest>) {
        if (photos.isEmpty()) return
        jdbcTemplate.batchUpdate(
            "insert into photo (review_id, url, storage_key) values (?, ?, ?)",
            photos,
            photos.size,
        ) { ps, p ->
            ps.setLong(1, reviewId)
            ps.setString(2, p.url!!)
            ps.setString(3, p.storageKey!!)
        }
    }

    /** Czy użytkownik przeszedł trasę: ma zapisaną aktywność z route_id tej trasy. */
    fun hasCompletedRoute(userId: UUID, routeId: Long): Boolean =
        jdbc.sql("select exists(select 1 from activity where user_id = :userId and route_id = :routeId)")
            .param("userId", userId)
            .param("routeId", routeId)
            .query(Boolean::class.java)
            .single()

    fun hasReviewed(userId: UUID, routeId: Long): Boolean =
        jdbc.sql("select exists(select 1 from review where user_id = :userId and route_id = :routeId)")
            .param("userId", userId)
            .param("routeId", routeId)
            .query(Boolean::class.java)
            .single()

    fun countByRoute(routeId: Long): Long =
        jdbc.sql("select count(*) from review where route_id = :routeId")
            .param("routeId", routeId)
            .query(Long::class.java)
            .single()

    /** Strona recenzji trasy, od najnowszej, razem z autorami i zdjęciami. */
    fun listByRoute(routeId: Long, page: PageRequest): List<ReviewResponse> {
        val rows = jdbc.sql(
            """
            $SELECT_REVIEW
            where rv.route_id = :routeId
            order by rv.created_at desc, rv.id desc
            limit :limit offset :offset
            """.trimIndent(),
        )
            .param("routeId", routeId)
            .param("limit", page.size)
            .param("offset", page.offset)
            .query(reviewMapper)
            .list()
        return withPhotos(rows)
    }

    fun findById(reviewId: Long): ReviewResponse? {
        val row = jdbc.sql("$SELECT_REVIEW where rv.id = :id")
            .param("id", reviewId)
            .query(reviewMapper)
            .optional()
            .orElse(null) ?: return null
        return withPhotos(listOf(row)).single()
    }

    fun findOwnership(reviewId: Long): ReviewOwnership? =
        jdbc.sql("select user_id, route_id from review where id = :id")
            .param("id", reviewId)
            .query(RowMapper { rs, _ -> ReviewOwnership(rs.getObject("user_id", UUID::class.java), rs.getLong("route_id")) })
            .optional()
            .orElse(null)

    /** Zdjęcia recenzji usuwa baza (on delete cascade). Pliki w Storage zostają. */
    fun delete(reviewId: Long) {
        jdbc.sql("delete from review where id = :id").param("id", reviewId).update()
    }

    /** Dokłada zdjęcia do recenzji jednym zapytaniem (zamiast osobnego dla każdej recenzji). */
    private fun withPhotos(reviews: List<ReviewResponse>): List<ReviewResponse> {
        if (reviews.isEmpty()) return reviews
        val photos = jdbc.sql("select id, review_id, url from photo where review_id in (:ids) order by id")
            .param("ids", reviews.map { it.id })
            .query(RowMapper { rs, _ -> rs.getLong("review_id") to PhotoResponse(rs.getLong("id"), rs.getString("url")) })
            .list()
            .groupBy({ it.first }, { it.second })
        return reviews.map { it.copy(photos = photos[it.id].orEmpty()) }
    }

    private companion object {
        val SELECT_REVIEW = """
            select rv.id, rv.route_id, rv.rating, rv.comment, rv.created_at,
                   p.id as user_id, p.display_name, p.avatar_url
            from review rv
            join profile p on p.id = rv.user_id
        """.trimIndent()

        val reviewMapper = RowMapper<ReviewResponse> { rs, _ ->
            ReviewResponse(
                id = rs.getLong("id"),
                routeId = rs.getLong("route_id"),
                user = UserSummaryResponse(
                    id = rs.getObject("user_id", UUID::class.java).toString(),
                    displayName = rs.getString("display_name"),
                    avatarUrl = rs.getString("avatar_url"),
                ),
                rating = rs.getInt("rating"),
                comment = rs.getString("comment"),
                photos = emptyList(),
                createdAt = rs.getObject("created_at", OffsetDateTime::class.java).toInstant().toString(),
            )
        }
    }
}
