package com.trailify.backend.feed

import com.trailify.backend.common.PageRequest
import com.trailify.backend.common.parseLineStringWkt
import com.trailify.backend.route.UserSummaryResponse
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID

/** Wpis, na którym ktoś reaguje: id i autor (do reguł usuwania komentarzy i oznaczeń). */
data class FeedEventRef(val id: Long, val authorId: UUID)

/** Komentarz z danymi potrzebnymi do sprawdzenia, kto może go usunąć. */
data class CommentOwnership(val id: Long, val authorId: UUID, val eventAuthorId: UUID)

/**
 * Zapytania SQL do tabel feed_event, feed_like i feed_comment (V7).
 * Wpisy dodajemy przy akcji (insert ... on conflict do nothing), a widoczność sprawdza każde zapytanie odczytu.
 */
@Repository
class FeedRepository(private val jdbc: JdbcClient) {

    // --- tworzenie wpisów (autor pochodzi z obiektu, którego wpis dotyczy) ---

    fun addRoutePublished(routeId: Long) {
        jdbc.sql("insert into feed_event (user_id, type, route_id) select user_id, 'ROUTE_PUBLISHED', id from route where id = :id on conflict do nothing")
            .param("id", routeId)
            .update()
    }

    fun addActivity(activityId: Long) {
        jdbc.sql("insert into feed_event (user_id, type, activity_id) select user_id, 'ACTIVITY', id from activity where id = :id on conflict do nothing")
            .param("id", activityId)
            .update()
    }

    fun addReview(reviewId: Long) {
        jdbc.sql("insert into feed_event (user_id, type, review_id) select user_id, 'REVIEW', id from review where id = :id on conflict do nothing")
            .param("id", reviewId)
            .update()
    }

    fun addPoiCollected(userId: UUID, poiId: Long) {
        jdbc.sql(
            """
            insert into feed_event (user_id, type, collection_id)
            select user_id, 'POI_COLLECTED', id from poi_collection where user_id = :userId and poi_id = :poiId
            on conflict do nothing
            """.trimIndent(),
        )
            .param("userId", userId)
            .param("poiId", poiId)
            .update()
    }

    // --- oś czasu ---

    /** Liczba widocznych wpisów osób obserwowanych przez [viewerId]. */
    fun countFeed(viewerId: UUID): Long =
        jdbc.sql("select count(*) $FROM_VISIBLE and e.user_id in ($FOLLOWED)")
            .param("viewer", viewerId)
            .query(RowMapper<Long> { rs, _ -> rs.getLong(1) })
            .single()

    /** Wpisy osób obserwowanych przez [viewerId], od najnowszych. */
    fun feed(viewerId: UUID, page: PageRequest): List<FeedItemResponse> =
        jdbc.sql("$SELECT_ITEM $FROM_VISIBLE and e.user_id in ($FOLLOWED) order by e.created_at desc, e.id desc limit :limit offset :offset")
            .param("viewer", viewerId)
            .param("limit", page.size)
            .param("offset", page.offset)
            .query(itemMapper)
            .list()

    /** Wpis widoczny dla wszystkich (publiczna trasa, udostępniona aktywność, profil nie jest prywatny) albo null. */
    fun findVisible(eventId: Long): FeedEventRef? =
        jdbc.sql("select e.id, e.user_id $FROM_VISIBLE and e.id = :id")
            .param("id", eventId)
            .query(RowMapper<FeedEventRef> { rs, _ -> FeedEventRef(rs.getLong("id"), rs.getObject("user_id", UUID::class.java)) })
            .optional()
            .orElse(null)

    // --- polubienia ---

    fun like(eventId: Long, userId: UUID) {
        jdbc.sql("insert into feed_like (event_id, user_id) values (:event, :user) on conflict do nothing")
            .param("event", eventId)
            .param("user", userId)
            .update()
    }

    fun unlike(eventId: Long, userId: UUID) {
        jdbc.sql("delete from feed_like where event_id = :event and user_id = :user")
            .param("event", eventId)
            .param("user", userId)
            .update()
    }

    // --- komentarze ---

    /** Czy [userId] jest autorem wpisu albo już go komentował (można mu odpowiedzieć). */
    fun isParticipant(eventId: Long, userId: UUID): Boolean =
        jdbc.sql(
            """
            select exists (
                select 1 from feed_event e join profile p on p.id = e.user_id and p.status = 'ACTIVE'
                where e.id = :event and e.user_id = :user
            ) or exists (
                select 1 from feed_comment c join profile p on p.id = c.user_id and p.status = 'ACTIVE'
                where c.event_id = :event and c.user_id = :user
            )
            """.trimIndent(),
        )
            .param("event", eventId)
            .param("user", userId)
            .query(RowMapper<Boolean> { rs, _ -> rs.getBoolean(1) })
            .single()

    fun insertComment(eventId: Long, userId: UUID, replyToUserId: UUID?, body: String): Long =
        jdbc.sql(
            """
            insert into feed_comment (event_id, user_id, reply_to_user_id, body)
            values (:event, :user, :reply, :body)
            returning id
            """.trimIndent(),
        )
            .param("event", eventId)
            .param("user", userId)
            .param("reply", replyToUserId, java.sql.Types.OTHER)
            .param("body", body)
            .query(RowMapper<Long> { rs, _ -> rs.getLong(1) })
            .single()

    fun findComment(commentId: Long): CommentResponse? =
        jdbc.sql("$SELECT_COMMENT where c.id = :id")
            .param("id", commentId)
            .query(commentMapper)
            .optional()
            .orElse(null)

    fun countComments(eventId: Long): Long =
        jdbc.sql(
            """
            select count(*) from feed_comment c
            join profile u on u.id = c.user_id and u.status = 'ACTIVE'
            where c.event_id = :event
            """.trimIndent(),
        )
            .param("event", eventId)
            .query(RowMapper<Long> { rs, _ -> rs.getLong(1) })
            .single()

    /** Komentarze wpisu od najstarszych (rozmowa czytana od góry). */
    fun listComments(eventId: Long, page: PageRequest): List<CommentResponse> =
        jdbc.sql("$SELECT_COMMENT where c.event_id = :event order by c.created_at, c.id limit :limit offset :offset")
            .param("event", eventId)
            .param("limit", page.size)
            .param("offset", page.offset)
            .query(commentMapper)
            .list()

    fun commentOwnership(commentId: Long): CommentOwnership? =
        jdbc.sql(
            """
            select c.id, c.user_id, e.user_id as event_user_id
            from feed_comment c join feed_event e on e.id = c.event_id
            where c.id = :id
            """.trimIndent(),
        )
            .param("id", commentId)
            .query(
                RowMapper<CommentOwnership> { rs, _ ->
                    CommentOwnership(
                        id = rs.getLong("id"),
                        authorId = rs.getObject("user_id", UUID::class.java),
                        eventAuthorId = rs.getObject("event_user_id", UUID::class.java),
                    )
                },
            )
            .optional()
            .orElse(null)

    fun deleteComment(commentId: Long) {
        jdbc.sql("delete from feed_comment where id = :id").param("id", commentId).update()
    }

    private companion object {
        /** Obserwowani przez oglądającego (parametr :viewer). */
        const val FOLLOWED = "select followee_id from follow where follower_id = :viewer"

        /**
         * Widoczne wpisy: autor aktywny i bez prywatnego profilu oraz obiekt wpisu nadal publiczny
         * (trasa publiczna, aktywność udostępniona, punkt zatwierdzony). [fr] to trasa tylko wtedy, gdy jest publiczna.
         * Kończy się warunkiem `where`, więc kolejne warunki dopisujemy przez `and`.
         */
        val FROM_VISIBLE = """
            from feed_event e
            join profile a on a.id = e.user_id and a.status = 'ACTIVE' and not a.is_private
            left join activity act on act.id = e.activity_id
            left join review rv on rv.id = e.review_id
            left join poi_collection pc on pc.id = e.collection_id
            left join point_of_interest poi on poi.id = pc.poi_id
            left join route fr on fr.id = coalesce(e.route_id, rv.route_id, act.route_id) and fr.is_public
            where (
                (e.type in ('ROUTE_PUBLISHED', 'REVIEW') and fr.id is not null)
                or (e.type = 'ACTIVITY' and act.is_shared)
                or (e.type = 'POI_COLLECTED' and poi.status = 'APPROVED')
            )
        """.trimIndent()

        /** Obrys do podglądu: uproszczony (ok. 20 m), żeby karta nie niosła tysięcy punktów. */
        val SELECT_ITEM = """
            select e.id, e.type, e.created_at,
                   a.id as author_id, a.display_name as author_name, a.avatar_url as author_avatar,
                   fr.id as route_id, fr.name as route_name, fr.activity_type as route_type,
                   fr.total_distance as route_distance, fr.duration_seconds as route_duration,
                   act.id as activity_id, act.type as activity_type, act.distance as activity_distance,
                   act.duration_seconds as activity_duration, act.avg_pace as activity_pace,
                   rv.rating as review_rating, rv.comment as review_comment,
                   poi.id as poi_id, poi.name as poi_name, poi.category as poi_category,
                   poi.description as poi_description, poi.latitude as poi_latitude, poi.longitude as poi_longitude,
                   ST_AsText(
                       ST_Simplify(
                           case e.type when 'ACTIVITY' then act.geometry when 'POI_COLLECTED' then null else fr.geometry end,
                           0.0002, true
                       ), 6
                   ) as geometry_wkt,
                   case e.type
                       when 'ROUTE_PUBLISHED' then (select ph.url from photo ph where ph.route_id = fr.id order by ph.id limit 1)
                       when 'REVIEW' then (select ph.url from photo ph where ph.review_id = rv.id order by ph.id limit 1)
                       when 'POI_COLLECTED' then (select ph.url from photo ph where ph.poi_id = poi.id limit 1)
                   end as photo_url,
                   (select count(*) from feed_like l where l.event_id = e.id) as like_count,
                   (select count(*) from feed_comment c join profile cu on cu.id = c.user_id and cu.status = 'ACTIVE'
                     where c.event_id = e.id) as comment_count,
                   exists (select 1 from feed_like l where l.event_id = e.id and l.user_id = :viewer) as liked_by_me
        """.trimIndent()

        val SELECT_COMMENT = """
            select c.id, c.body, c.created_at,
                   u.id as author_id, u.display_name as author_name, u.avatar_url as author_avatar,
                   r.id as reply_id, r.display_name as reply_name, r.avatar_url as reply_avatar
            from feed_comment c
            join profile u on u.id = c.user_id and u.status = 'ACTIVE'
            left join profile r on r.id = c.reply_to_user_id and r.status = 'ACTIVE'
        """.trimIndent()

        val commentMapper = RowMapper<CommentResponse> { rs, _ ->
            CommentResponse(
                id = rs.getLong("id"),
                author = UserSummaryResponse(rs.getString("author_id"), rs.getString("author_name"), rs.getString("author_avatar")),
                replyTo = rs.getString("reply_id")?.let {
                    UserSummaryResponse(it, rs.getString("reply_name"), rs.getString("reply_avatar"))
                },
                body = rs.getString("body"),
                createdAt = rs.getObject("created_at", OffsetDateTime::class.java).toInstant().toString(),
            )
        }

        val itemMapper = RowMapper<FeedItemResponse> { rs, _ ->
            FeedItemResponse(
                id = rs.getLong("id"),
                type = rs.getString("type"),
                createdAt = rs.getObject("created_at", OffsetDateTime::class.java).toInstant().toString(),
                author = UserSummaryResponse(rs.getString("author_id"), rs.getString("author_name"), rs.getString("author_avatar")),
                route = rs.getNullableLong("route_id")?.let {
                    FeedRouteResponse(
                        id = it,
                        name = rs.getString("route_name"),
                        activityType = rs.getString("route_type"),
                        totalDistance = rs.getNullableDouble("route_distance"),
                        durationSeconds = rs.getNullableInt("route_duration"),
                    )
                },
                activity = rs.getNullableLong("activity_id")?.let {
                    FeedActivityResponse(
                        id = it,
                        type = rs.getString("activity_type"),
                        distance = rs.getNullableDouble("activity_distance"),
                        durationSeconds = rs.getNullableInt("activity_duration"),
                        avgPace = rs.getNullableDouble("activity_pace"),
                    )
                },
                review = rs.getNullableInt("review_rating")?.let { FeedReviewResponse(it, rs.getString("review_comment")) },
                poi = rs.getNullableLong("poi_id")?.let { FeedPoiResponse(
                        id = it,
                        name = rs.getString("poi_name"),
                        category = rs.getString("poi_category"),
                        description = rs.getString("poi_description"),
                        latitude = rs.getDouble("poi_latitude"),
                        longitude = rs.getDouble("poi_longitude"),
                    ) },
                geometry = rs.getString("geometry_wkt")?.let(::parseLineStringWkt),
                photoUrl = rs.getString("photo_url"),
                likeCount = rs.getInt("like_count"),
                commentCount = rs.getInt("comment_count"),
                likedByMe = rs.getBoolean("liked_by_me"),
            )
        }
    }
}

private fun ResultSet.getNullableDouble(column: String): Double? = getDouble(column).takeUnless { wasNull() }

private fun ResultSet.getNullableInt(column: String): Int? = getInt(column).takeUnless { wasNull() }

private fun ResultSet.getNullableLong(column: String): Long? = getLong(column).takeUnless { wasNull() }
