package com.trailify.backend.activity

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
import java.time.ZoneOffset
import java.util.UUID

/** Zapytania SQL do tabel activity i gps_point. Każde zapytanie o konkretną aktywność sprawdza właściciela. */
@Repository
class ActivityRepository(
    private val jdbc: JdbcClient,
    private val jdbcTemplate: JdbcTemplate,
) {

    /** Zapisuje aktywność i jej punkty GPS (wywoływane wewnątrz transakcji). Zwraca id. */
    fun insert(userId: UUID, request: ActivityCreateRequest): Long {
        val points = request.points!!
        val line = lineStringWkt(points.map { it.longitude!! to it.latitude!! })

        val id = jdbc.sql(
            """
            insert into activity (client_id, user_id, route_id, type, start_time, end_time,
                                  distance, duration_seconds, avg_pace, step_count, is_shared, geometry)
            values (:clientId, :userId, :routeId, :type, :startTime, :endTime,
                    :distance, :duration, :pace, :steps, :shared, ST_SetSRID(ST_GeomFromText(:wkt), 4326))
            returning id
            """.trimIndent(),
        )
            .param("clientId", request.clientId)
            .param("userId", userId)
            .param("routeId", request.routeId, Types.BIGINT)
            .param("type", request.type!!.name)
            .param("startTime", request.startTime!!.toUtc(), Types.TIMESTAMP_WITH_TIMEZONE)
            .param("endTime", request.endTime!!.toUtc(), Types.TIMESTAMP_WITH_TIMEZONE)
            .param("distance", request.distance, Types.DOUBLE)
            .param("duration", request.durationSeconds, Types.INTEGER)
            .param("pace", request.avgPace, Types.DOUBLE)
            .param("steps", request.stepCount, Types.INTEGER)
            .param("shared", request.isShared ?: false)
            .param("wkt", line)
            .query(RowMapper<Long> { rs, _ -> rs.getLong(1) })
            .single()

        jdbcTemplate.batchUpdate(
            """
            insert into gps_point (activity_id, latitude, longitude, altitude, speed, recorded_at, is_reconstructed)
            values (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            points,
            BATCH_SIZE,
        ) { ps, p ->
            ps.setLong(1, id)
            ps.setDouble(2, p.latitude!!)
            ps.setDouble(3, p.longitude!!)
            if (p.altitude == null) ps.setNull(4, Types.DOUBLE) else ps.setDouble(4, p.altitude)
            if (p.speed == null) ps.setNull(5, Types.DOUBLE) else ps.setDouble(5, p.speed)
            ps.setObject(6, p.recordedAt!!.toUtc())
            ps.setBoolean(7, p.isReconstructed ?: false)
        }
        return id
    }

    fun findByClientId(userId: UUID, clientId: UUID): ActivityResponse? =
        jdbc.sql("$SELECT_DETAILS where user_id = :userId and client_id = :clientId")
            .param("userId", userId)
            .param("clientId", clientId)
            .query(detailsMapper)
            .optional()
            .orElse(null)

    fun findOwned(userId: UUID, activityId: Long): ActivityResponse? =
        jdbc.sql("$SELECT_DETAILS where user_id = :userId and id = :id")
            .param("userId", userId)
            .param("id", activityId)
            .query(detailsMapper)
            .optional()
            .orElse(null)

    /**
     * Aktywność widoczna dla oglądającego: własna albo udostępniona przez osobę z aktywnym, nieprywatnym profilem.
     * Cudza prywatna (i każda aktywność prywatnego profilu) wygląda jak nieistniejąca.
     */
    fun findViewable(viewerId: UUID, activityId: Long): ActivityResponse? =
        jdbc.sql(
            "$SELECT_DETAILS where id = :id and (user_id = :viewer or (is_shared and $OWNER_VISIBLE))",
        )
            .param("id", activityId)
            .param("viewer", viewerId)
            .query(detailsMapper)
            .optional()
            .orElse(null)

    /** Udostępnia albo ukrywa aktywność. Zwraca false, gdy aktywność nie istnieje albo nie jest własna. */
    fun setShared(userId: UUID, activityId: Long, shared: Boolean): Boolean =
        jdbc.sql("update activity set is_shared = :shared where id = :id and user_id = :userId")
            .param("shared", shared)
            .param("id", activityId)
            .param("userId", userId)
            .update() > 0

    fun countSharedByUser(userId: UUID): Long =
        jdbc.sql("select count(*) from activity where user_id = :userId and is_shared")
            .param("userId", userId)
            .query(RowMapper<Long> { rs, _ -> rs.getLong(1) })
            .single()

    /** Udostępnione aktywności użytkownika (do oglądania na jego profilu), od najnowszych. */
    fun listSharedByUser(userId: UUID, page: PageRequest): List<ActivitySummaryResponse> =
        jdbc.sql(
            """
            select id, type, route_id, start_time, end_time, distance, duration_seconds, avg_pace, step_count, is_shared
            from activity
            where user_id = :userId and is_shared
            order by start_time desc, id desc
            limit :limit offset :offset
            """.trimIndent(),
        )
            .param("userId", userId)
            .param("limit", page.size)
            .param("offset", page.offset)
            .query(summaryMapper)
            .list()

    fun countByUser(userId: UUID, range: ActivityRange = ActivityRange.ALL): Long {
        val (where, params) = userAndRange(userId, range)
        return jdbc.sql("select count(*) from activity where $where")
            .params(params)
            .query(RowMapper<Long> { rs, _ -> rs.getLong(1) })
            .single()
    }

    fun listByUser(userId: UUID, page: PageRequest, range: ActivityRange = ActivityRange.ALL): List<ActivitySummaryResponse> {
        val (where, params) = userAndRange(userId, range)
        return jdbc.sql(
            """
            select id, type, route_id, start_time, end_time, distance, duration_seconds, avg_pace, step_count, is_shared
            from activity
            where $where
            order by start_time desc, id desc
            limit :limit offset :offset
            """.trimIndent(),
        )
            .params(params + mapOf("limit" to page.size, "offset" to page.offset))
            .query(summaryMapper)
            .list()
    }

    /** Warunek "aktywności tego użytkownika z tego przedziału czasu" razem z parametrami. */
    private fun userAndRange(userId: UUID, range: ActivityRange): Pair<String, Map<String, Any>> {
        val where = mutableListOf("user_id = :userId")
        val params = linkedMapOf<String, Any>("userId" to userId)
        range.from?.let { params["from"] = OffsetDateTime.ofInstant(it, ZoneOffset.UTC); where += "start_time >= :from" }
        range.to?.let { params["to"] = OffsetDateTime.ofInstant(it, ZoneOffset.UTC); where += "start_time < :to" }
        return where.joinToString(" and ") to params
    }

    /** Czy użytkownik może wybrać tę trasę: jest jego albo publiczna. */
    fun routeVisibleTo(userId: UUID, routeId: Long): Boolean =
        jdbc.sql("select exists (select 1 from route where id = :id and (is_public or user_id = :userId))")
            .param("id", routeId)
            .param("userId", userId)
            .query(RowMapper<Boolean> { rs, _ -> rs.getBoolean(1) })
            .single()

    fun listPoints(activityId: Long): List<GpsPointResponse> =
        jdbc.sql(
            """
            select latitude, longitude, altitude, speed, recorded_at, is_reconstructed
            from gps_point
            where activity_id = :id
            order by recorded_at, id
            """.trimIndent(),
        )
            .param("id", activityId)
            .query(
                RowMapper<GpsPointResponse> { rs, _ ->
                    GpsPointResponse(
                        latitude = rs.getDouble("latitude"),
                        longitude = rs.getDouble("longitude"),
                        altitude = rs.getNullableDouble("altitude"),
                        speed = rs.getNullableDouble("speed"),
                        recordedAt = rs.getInstantString("recorded_at")!!,
                        isReconstructed = rs.getBoolean("is_reconstructed"),
                    )
                },
            )
            .list()

    /** Zwraca true, jeśli aktywność należała do użytkownika i została usunięta (punkty GPS znikają kaskadowo). */
    fun delete(userId: UUID, activityId: Long): Boolean =
        jdbc.sql("delete from activity where id = :id and user_id = :userId")
            .param("id", activityId)
            .param("userId", userId)
            .update() > 0

    private companion object {
        const val BATCH_SIZE = 1000

        const val SELECT_DETAILS = """
            select id, type, route_id, start_time, end_time, distance, duration_seconds, avg_pace, step_count, is_shared,
                   ST_AsText(geometry) as geometry_wkt
            from activity
        """

        /** Właściciel aktywności ma aktywne konto i profil nieprywatny (warunek dla cudzych aktywności). */
        const val OWNER_VISIBLE =
            "exists (select 1 from profile o where o.id = activity.user_id and o.status = 'ACTIVE' and not o.is_private)"

        val summaryMapper = RowMapper<ActivitySummaryResponse> { rs, _ ->
            ActivitySummaryResponse(
                id = rs.getLong("id"),
                type = rs.getString("type"),
                routeId = rs.getNullableLong("route_id"),
                startTime = rs.getInstantString("start_time")!!,
                endTime = rs.getInstantString("end_time"),
                distance = rs.getNullableDouble("distance"),
                durationSeconds = rs.getNullableInt("duration_seconds"),
                avgPace = rs.getNullableDouble("avg_pace"),
                stepCount = rs.getNullableInt("step_count"),
                isShared = rs.getBoolean("is_shared"),
            )
        }

        val detailsMapper = RowMapper<ActivityResponse> { rs, _ ->
            ActivityResponse(
                id = rs.getLong("id"),
                type = rs.getString("type"),
                routeId = rs.getNullableLong("route_id"),
                startTime = rs.getInstantString("start_time")!!,
                endTime = rs.getInstantString("end_time"),
                distance = rs.getNullableDouble("distance"),
                durationSeconds = rs.getNullableInt("duration_seconds"),
                avgPace = rs.getNullableDouble("avg_pace"),
                stepCount = rs.getNullableInt("step_count"),
                isShared = rs.getBoolean("is_shared"),
                geometry = parseLineStringWkt(rs.getString("geometry_wkt")),
            )
        }
    }
}

private fun java.time.Instant.toUtc(): OffsetDateTime = OffsetDateTime.ofInstant(this, ZoneOffset.UTC)

private fun ResultSet.getNullableDouble(column: String): Double? = getDouble(column).takeUnless { wasNull() }

private fun ResultSet.getNullableInt(column: String): Int? = getInt(column).takeUnless { wasNull() }

private fun ResultSet.getNullableLong(column: String): Long? = getLong(column).takeUnless { wasNull() }

/** Czas z kolumny timestamptz jako tekst ISO 8601 w UTC, np. 2026-10-20T14:30:00Z. */
private fun ResultSet.getInstantString(column: String): String? =
    getObject(column, OffsetDateTime::class.java)?.toInstant()?.toString()
