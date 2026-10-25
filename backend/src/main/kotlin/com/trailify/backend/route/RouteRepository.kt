package com.trailify.backend.route

import com.trailify.backend.common.PageRequest
import com.trailify.backend.common.lineStringWkt
import com.trailify.backend.common.parseLineStringWkt
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Types
import java.time.OffsetDateTime
import java.util.UUID

/** Zapytania SQL do tabel route i photo. Statystyki ocen i ranking pochodzą z widoku route_rating_stats (V4). */
@Repository
class RouteRepository(
    private val jdbc: JdbcClient,
    private val jdbcTemplate: JdbcTemplate,
) {

    /** Zapisuje trasę (wywoływane wewnątrz transakcji). Dystans liczy PostGIS po geometrii. Zwraca id. */
    fun insert(
        userId: UUID,
        request: RouteCreateRequest,
        coordinates: List<List<Double>>,
        durationSeconds: Int?,
    ): Long {
        val wkt = lineStringWkt(coordinates.map { it[0] to it[1] })
        return jdbc.sql(
            """
            with g as (select ST_SetSRID(ST_GeomFromText(:wkt), 4326) as geom)
            insert into route (user_id, name, description, activity_type, is_tourist, is_scenic, is_public,
                               total_distance, duration_seconds, geometry)
            select :userId, :name, :description, :type, :tourist, :scenic, :public,
                   ST_Length(g.geom::geography), :duration, g.geom
            from g
            returning id
            """.trimIndent(),
        )
            .param("wkt", wkt)
            .param("userId", userId)
            .param("name", request.name!!.trim())
            .param("description", request.description?.trim()?.ifEmpty { null }, Types.VARCHAR)
            .param("type", request.activityType!!.name)
            .param("tourist", request.isTourist!!)
            .param("scenic", request.isScenic!!)
            .param("public", request.isPublic!!)
            .param("duration", durationSeconds, Types.INTEGER)
            .query(RowMapper<Long> { rs, _ -> rs.getLong(1) })
            .single()
    }

    fun insertPhotos(routeId: Long, photos: List<PhotoInputRequest>) {
        if (photos.isEmpty()) return
        jdbcTemplate.batchUpdate(
            "insert into photo (route_id, url, storage_key) values (?, ?, ?)",
            photos,
            photos.size,
        ) { ps, p ->
            ps.setLong(1, routeId)
            ps.setString(2, p.url!!)
            ps.setString(3, p.storageKey!!)
        }
    }

    /** Trasa ze szczegółami, bez sprawdzania widoczności (robi to serwis). */
    fun findDetails(routeId: Long): RouteResponse? {
        val route = jdbc.sql(
            """
            ${selectSummary(distanceColumn = false, extra = ", r.description, ST_AsText(r.geometry) as geometry_wkt")}
            where r.id = :id
            """.trimIndent(),
        )
            .param("id", routeId)
            .query(RowMapper<Pair<RouteSummaryResponse, Pair<String?, String>>> { rs, _ ->
                summaryMapper.mapRow(rs, 0)!! to (rs.getString("description") to rs.getString("geometry_wkt"))
            })
            .optional()
            .orElse(null) ?: return null

        val photos = jdbc.sql("select id, url from photo where route_id = :id order by id")
            .param("id", routeId)
            .query(RowMapper<PhotoResponse> { rs, _ -> PhotoResponse(rs.getLong("id"), rs.getString("url")) })
            .list()

        val (summary, extra) = route
        return RouteResponse(
            summary = summary,
            description = extra.first,
            geometry = parseLineStringWkt(extra.second),
            photos = photos,
        )
    }

    /** Wyszukiwanie tras publicznych. Zwraca liczbę wszystkich pasujących tras i jedną stronę wyników. */
    fun search(filter: RouteSearchFilter, page: PageRequest): Pair<Long, List<RouteSummaryResponse>> {
        val params = linkedMapOf<String, Any>()
        val where = mutableListOf("r.is_public")

        if (filter.hasLocation) {
            params["lat"] = filter.lat!!
            params["lng"] = filter.lng!!
            params["radiusM"] = filter.radiusKm * 1000
            where += "ST_DWithin(r.geometry::geography, $QUERY_POINT, :radiusM)"
        }
        if (filter.activityTypes.isNotEmpty()) {
            params["types"] = filter.activityTypes.map { it.name }
            where += "r.activity_type in (:types)"
        }
        filter.minDistance?.let { params["minDistance"] = it; where += "r.total_distance >= :minDistance" }
        filter.maxDistance?.let { params["maxDistance"] = it; where += "r.total_distance <= :maxDistance" }
        filter.minRating?.let { params["minRating"] = it; where += "s.average_rating >= :minRating" }
        filter.isTourist?.let { params["tourist"] = it; where += "r.is_tourist = :tourist" }
        filter.isScenic?.let { params["scenic"] = it; where += "r.is_scenic = :scenic" }
        filter.query?.let { params["q"] = likePattern(it); where += "r.name ilike :q escape '\\'" }

        val whereSql = where.joinToString(" and ")
        val total = jdbc.sql(
            "select count(*) from route r join route_rating_stats s on s.route_id = r.id where $whereSql",
        )
            .params(params)
            .query(RowMapper<Long> { rs, _ -> rs.getLong(1) })
            .single()
        if (total == 0L) return 0L to emptyList()

        val items = jdbc.sql(
            """
            ${selectSummary(distanceColumn = filter.hasLocation)}
            where $whereSql
            order by ${filter.sort.orderBy}
            limit :limit offset :offset
            """.trimIndent(),
        )
            .params(params + mapOf("limit" to page.size, "offset" to page.offset))
            .query(summaryMapper)
            .list()
        return total to items
    }

    fun countByUser(userId: UUID): Long =
        jdbc.sql("select count(*) from route where user_id = :userId")
            .param("userId", userId)
            .query(RowMapper<Long> { rs, _ -> rs.getLong(1) })
            .single()

    /** Wszystkie trasy użytkownika (publiczne i prywatne), od najnowszej. */
    fun listByUser(userId: UUID, page: PageRequest): List<RouteSummaryResponse> =
        jdbc.sql(
            """
            ${selectSummary(distanceColumn = false)}
            where r.user_id = :userId
            order by r.created_at desc, r.id desc
            limit :limit offset :offset
            """.trimIndent(),
        )
            .param("userId", userId)
            .param("limit", page.size)
            .param("offset", page.offset)
            .query(summaryMapper)
            .list()

    /** Zmiana metadanych trasy. Geometrii nie da się zmienić. */
    fun update(routeId: Long, update: RouteUpdate) {
        jdbc.sql(
            """
            update route set
                name          = coalesce(:name, name),
                description   = case when :changeDescription then :description else description end,
                activity_type = coalesce(:type, activity_type),
                is_tourist    = coalesce(:tourist, is_tourist),
                is_scenic     = coalesce(:scenic, is_scenic),
                is_public     = coalesce(:public, is_public)
            where id = :id
            """.trimIndent(),
        )
            .param("id", routeId)
            .param("name", update.name, Types.VARCHAR)
            .param("changeDescription", update.changeDescription)
            .param("description", update.description, Types.VARCHAR)
            .param("type", update.activityType?.name, Types.VARCHAR)
            .param("tourist", update.isTourist, Types.BOOLEAN)
            .param("scenic", update.isScenic, Types.BOOLEAN)
            .param("public", update.isPublic, Types.BOOLEAN)
            .update()
    }

    /** Usuwa trasę; recenzje i zdjęcia znikają kaskadowo, aktywności zostają (route_id = null). */
    fun delete(routeId: Long): Boolean =
        jdbc.sql("delete from route where id = :id").param("id", routeId).update() > 0

    private companion object {
        const val QUERY_POINT = "ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography"

        /** Wspólny początek zapytań o trasy; kończy się przed `where`. */
        fun selectSummary(distanceColumn: Boolean, extra: String = ""): String = """
            select r.id, r.name, r.activity_type, r.is_tourist, r.is_scenic, r.is_public,
                   r.total_distance, r.duration_seconds, r.created_at,
                   p.id as owner_id, p.display_name as owner_name, p.avatar_url as owner_avatar,
                   s.review_count, s.average_rating, s.recommendation_score,
                   ST_Y(ST_StartPoint(r.geometry)) as start_lat, ST_X(ST_StartPoint(r.geometry)) as start_lng,
                   (select ph.url from photo ph where ph.route_id = r.id order by ph.id limit 1) as thumbnail_url,
                   ${if (distanceColumn) "ST_Distance(r.geometry::geography, $QUERY_POINT)" else "null::double precision"}
                       as distance_from_query$extra
            from route r
            join profile p on p.id = r.user_id
            join route_rating_stats s on s.route_id = r.id
        """.trimIndent()

        val summaryMapper = RowMapper<RouteSummaryResponse> { rs, _ ->
            RouteSummaryResponse(
                id = rs.getLong("id"),
                name = rs.getString("name"),
                activityType = rs.getString("activity_type"),
                isTourist = rs.getBoolean("is_tourist"),
                isScenic = rs.getBoolean("is_scenic"),
                isPublic = rs.getBoolean("is_public"),
                totalDistance = rs.getNullableDouble("total_distance"),
                durationSeconds = rs.getNullableInt("duration_seconds"),
                averageRating = rs.getNullableDouble("average_rating"),
                reviewCount = rs.getInt("review_count"),
                recommendationScore = rs.getNullableDouble("recommendation_score"),
                startPoint = LatLngResponse(rs.getDouble("start_lat"), rs.getDouble("start_lng")),
                thumbnailUrl = rs.getString("thumbnail_url"),
                distanceFromQuery = rs.getNullableDouble("distance_from_query"),
                owner = UserSummaryResponse(
                    id = rs.getString("owner_id"),
                    displayName = rs.getString("owner_name"),
                    avatarUrl = rs.getString("owner_avatar"),
                ),
                createdAt = rs.getObject("created_at", OffsetDateTime::class.java).toInstant().toString(),
            )
        }
    }
}

private fun ResultSet.getNullableDouble(column: String): Double? = getDouble(column).takeUnless { wasNull() }

private fun ResultSet.getNullableInt(column: String): Int? = getInt(column).takeUnless { wasNull() }
