package com.trailify.backend.poi

import com.trailify.backend.common.PageRequest
import com.trailify.backend.route.UserSummaryResponse
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Types
import java.time.OffsetDateTime
import java.util.UUID

/** Zapytania SQL do tabel point_of_interest, poi_collection i photo (V3). */
@Repository
class PoiRepository(private val jdbc: JdbcClient) {

    /** Zatwierdzone punkty w prostokącie (indeks GiST na kolumnie location). */
    fun approvedInBox(box: BoundingBox): List<PoiResponse> =
        jdbc.sql(
            """
            $SELECT_POI
            where p.status = 'APPROVED'
              and p.location && ST_MakeEnvelope(:minLng, :minLat, :maxLng, :maxLat, 4326)
            order by p.id
            limit $MAX_POIS_IN_BOX
            """.trimIndent(),
        )
            .param("minLng", box.minLng)
            .param("minLat", box.minLat)
            .param("maxLng", box.maxLng)
            .param("maxLat", box.maxLat)
            .query(poiMapper)
            .list()

    /** Punkt z danymi moderacji, bez względu na status. */
    fun findById(id: Long): PoiAdminResponse? =
        jdbc.sql("$SELECT_POI_ADMIN where p.id = :id")
            .param("id", id)
            .query(poiAdminMapper)
            .optional()
            .orElse(null)

    /** Zapisuje punkt (wywoływane wewnątrz transakcji). Zwraca id. */
    fun insert(submittedBy: UUID, request: PoiCreateRequest, status: String): Long =
        jdbc.sql(
            """
            insert into point_of_interest (submitted_by, name, description, latitude, longitude, status)
            values (:userId, :name, :description, :lat, :lng, :status)
            returning id
            """.trimIndent(),
        )
            .param("userId", submittedBy)
            .param("name", request.name!!.trim())
            .param("description", request.description!!.trim())
            .param("lat", request.latitude!!)
            .param("lng", request.longitude!!)
            .param("status", status)
            .query(RowMapper<Long> { rs, _ -> rs.getLong(1) })
            .single()

    /** Punkt ma najwyżej jedno zdjęcie (unikalny indeks photo_poi_unique), nowe zastępuje stare. */
    fun replacePhoto(poiId: Long, storageKey: String, url: String) {
        jdbc.sql("delete from photo where poi_id = :id").param("id", poiId).update()
        jdbc.sql("insert into photo (poi_id, url, storage_key) values (:id, :url, :key)")
            .param("id", poiId)
            .param("url", url)
            .param("key", storageKey)
            .update()
    }

    fun update(id: Long, update: PoiUpdate) {
        jdbc.sql(
            """
            update point_of_interest set
                name = coalesce(:name, name),
                description = coalesce(:description, description),
                latitude = coalesce(:lat, latitude),
                longitude = coalesce(:lng, longitude)
            where id = :id
            """.trimIndent(),
        )
            .param("id", id)
            .param("name", update.name, Types.VARCHAR)
            .param("description", update.description, Types.VARCHAR)
            .param("lat", update.latitude, Types.DOUBLE)
            .param("lng", update.longitude, Types.DOUBLE)
            .update()
    }

    fun setStatus(id: Long, status: String, rejectionReason: String?) {
        jdbc.sql("update point_of_interest set status = :status, rejection_reason = :reason where id = :id")
            .param("id", id)
            .param("status", status)
            .param("reason", rejectionReason, Types.VARCHAR)
            .update()
    }

    /** Zdjęcie i wpisy w kolekcjach usuwa baza (on delete cascade). */
    fun delete(id: Long) {
        jdbc.sql("delete from point_of_interest where id = :id").param("id", id).update()
    }

    /** Lista do moderacji, od najnowszego. [status] = null to wszystkie. */
    fun listForAdmin(status: String?, page: PageRequest): Pair<Long, List<PoiAdminResponse>> {
        val where = if (status != null) "where p.status = :status" else ""
        val count = jdbc.sql("select count(*) from point_of_interest p $where")
            .let { if (status != null) it.param("status", status) else it }
            .query(Long::class.java)
            .single()
        val items = jdbc.sql("$SELECT_POI_ADMIN $where order by p.created_at desc, p.id desc limit :limit offset :offset")
            .let { if (status != null) it.param("status", status) else it }
            .param("limit", page.size)
            .param("offset", page.offset)
            .query(poiAdminMapper)
            .list()
        return count to items
    }

    fun isCollected(userId: UUID, poiId: Long): Boolean =
        jdbc.sql("select exists(select 1 from poi_collection where user_id = :userId and poi_id = :poiId)")
            .param("userId", userId)
            .param("poiId", poiId)
            .query(Boolean::class.java)
            .single()

    /** Zapisuje zebranie punktu i zwraca jego czas. */
    fun collect(userId: UUID, poiId: Long): OffsetDateTime =
        jdbc.sql("insert into poi_collection (user_id, poi_id) values (:userId, :poiId) returning collected_at")
            .param("userId", userId)
            .param("poiId", poiId)
            .query(RowMapper { rs, _ -> rs.getObject("collected_at", OffsetDateTime::class.java) })
            .single()

    /** Zebrane punkty użytkownika, od najnowszego. */
    fun collectionOf(userId: UUID): List<PoiCollectionEntryResponse> =
        jdbc.sql(
            """
            select p.id, p.name, p.description, p.latitude, p.longitude,
                   (select ph.url from photo ph where ph.poi_id = p.id) as photo_url,
                   c.collected_at
            from poi_collection c
            join point_of_interest p on p.id = c.poi_id
            where c.user_id = :userId
            order by c.collected_at desc
            """.trimIndent(),
        )
            .param("userId", userId)
            .query(RowMapper { rs, _ ->
                PoiCollectionEntryResponse(
                    poi = rs.toPoi(),
                    collectedAt = rs.getObject("collected_at", OffsetDateTime::class.java).toInstant().toString(),
                )
            })
            .list()

    private companion object {
        const val MAX_POIS_IN_BOX = 500

        val SELECT_POI = """
            select p.id, p.name, p.description, p.latitude, p.longitude,
                   (select ph.url from photo ph where ph.poi_id = p.id) as photo_url
            from point_of_interest p
        """.trimIndent()

        val SELECT_POI_ADMIN = """
            select p.id, p.name, p.description, p.latitude, p.longitude, p.status, p.rejection_reason, p.created_at,
                   (select ph.url from photo ph where ph.poi_id = p.id) as photo_url,
                   pr.id as submitter_id, pr.display_name as submitter_name, pr.avatar_url as submitter_avatar
            from point_of_interest p
            left join profile pr on pr.id = p.submitted_by
        """.trimIndent()

        val poiMapper = RowMapper<PoiResponse> { rs, _ -> rs.toPoi() }

        val poiAdminMapper = RowMapper<PoiAdminResponse> { rs, _ ->
            val submitter = rs.getObject("submitter_id", UUID::class.java)
            PoiAdminResponse(
                poi = rs.toPoi(),
                status = rs.getString("status"),
                submittedBy = submitter?.let {
                    UserSummaryResponse(it.toString(), rs.getString("submitter_name"), rs.getString("submitter_avatar"))
                },
                rejectionReason = rs.getString("rejection_reason"),
                createdAt = rs.getObject("created_at", OffsetDateTime::class.java).toInstant().toString(),
            )
        }

        fun ResultSet.toPoi() = PoiResponse(
            id = getLong("id"),
            name = getString("name"),
            description = getString("description"),
            latitude = getDouble("latitude"),
            longitude = getDouble("longitude"),
            photoUrl = getString("photo_url"),
        )
    }
}
