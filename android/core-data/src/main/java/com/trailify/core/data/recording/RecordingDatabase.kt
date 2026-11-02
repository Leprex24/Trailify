package com.trailify.core.data.recording

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.trailify.core.domain.model.TrackPoint
import java.time.Instant

/** Nagranie odczytane z bazy po ponownym uruchomieniu aplikacji. */
internal class SavedRecording(
    val clientId: String,
    val type: String,
    val status: String,
    val startedAtMs: Long,
    val finishedAtMs: Long?,
    val elapsedMs: Long,
    val distanceMeters: Double,
    val steps: Int?,
    val routeId: Long?,
    val points: List<TrackPoint>,
)

/**
 * Lokalna baza telefonu (SQLite) z bieżącym nagraniem. Każdy punkt GPS trafia tu od razu,
 * więc awaria aplikacji, restart telefonu albo brak internetu nie kasują trasy (wymaganie 3.2.2).
 *
 * Trzymamy jedno nagranie naraz (tabela session ma jeden wiersz). Metody blokują wątek,
 * dlatego [RecordingStore] woła je zawsze z jednego wątku w tle.
 */
internal class RecordingDatabase(context: Context) : SQLiteOpenHelper(context, "recording.db", null, VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            create table session (
                id integer primary key,
                client_id text not null,
                type text not null,
                status text not null,
                started_at integer not null,
                finished_at integer,
                elapsed_ms integer not null,
                distance real not null,
                steps integer,
                route_id integer
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            create table point (
                id integer primary key autoincrement,
                lat real not null,
                lng real not null,
                altitude real,
                speed real,
                recorded_at integer not null,
                reconstructed integer not null default 0
            )
            """.trimIndent(),
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // wersja 2: id opublikowanej trasy, którą użytkownik przechodzi
        if (oldVersion < 2) db.execSQL("alter table session add column route_id integer")
        // wersja 3: punkty odtworzone po utracie sygnału GPS (1 = odtworzony, 0 = zmierzony)
        if (oldVersion < 3) db.execSQL("alter table point add column reconstructed integer not null default 0")
    }

    fun load(): SavedRecording? {
        val db = readableDatabase
        val session = db.rawQuery(
            "select client_id, type, status, started_at, finished_at, elapsed_ms, distance, steps, route_id from session where id = 1",
            null,
        ).use { c ->
            if (!c.moveToFirst()) return null
            arrayOf<Any?>(
                c.getString(0),
                c.getString(1),
                c.getString(2),
                c.getLong(3),
                if (c.isNull(4)) null else c.getLong(4),
                c.getLong(5),
                c.getDouble(6),
                if (c.isNull(7)) null else c.getInt(7),
                if (c.isNull(8)) null else c.getLong(8),
            )
        }
        val points = ArrayList<TrackPoint>()
        // kolejność wg czasu: punkty odtworzone dopisujemy później, ale leżą w czasie między zmierzonymi
        db.rawQuery(
            "select lat, lng, altitude, speed, recorded_at, reconstructed from point order by recorded_at, id",
            null,
        ).use { c ->
            while (c.moveToNext()) {
                points.add(
                    TrackPoint(
                        latitude = c.getDouble(0),
                        longitude = c.getDouble(1),
                        altitude = if (c.isNull(2)) null else c.getDouble(2),
                        speed = if (c.isNull(3)) null else c.getDouble(3),
                        recordedAt = Instant.ofEpochMilli(c.getLong(4)),
                        isReconstructed = c.getInt(5) != 0,
                    ),
                )
            }
        }
        return SavedRecording(
            clientId = session[0] as String,
            type = session[1] as String,
            status = session[2] as String,
            startedAtMs = session[3] as Long,
            finishedAtMs = session[4] as Long?,
            elapsedMs = session[5] as Long,
            distanceMeters = session[6] as Double,
            steps = session[7] as Int?,
            routeId = session[8] as Long?,
            points = points,
        )
    }

    /** Zaczyna nowe nagranie (kasuje poprzednie). */
    fun begin(clientId: String, type: String, startedAtMs: Long, routeId: Long?) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("point", null, null)
            db.delete("session", null, null)
            db.insert(
                "session",
                null,
                ContentValues().apply {
                    put("id", 1)
                    put("client_id", clientId)
                    put("type", type)
                    put("status", "RECORDING")
                    put("started_at", startedAtMs)
                    put("elapsed_ms", 0L)
                    put("distance", 0.0)
                    if (routeId != null) put("route_id", routeId)
                },
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun appendPoint(point: TrackPoint) {
        writableDatabase.insert("point", null, pointValues(point))
    }

    /**
     * Zastępuje punkty odtworzone leżące w czasie między [fromMs] a [toMs] (luka w sygnale GPS) podanymi.
     * Punkty zmierzone zostają nietknięte.
     */
    fun replaceReconstructed(fromMs: Long, toMs: Long, points: List<TrackPoint>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(
                "point",
                "reconstructed = 1 and recorded_at > ? and recorded_at < ?",
                arrayOf(fromMs.toString(), toMs.toString()),
            )
            points.forEach { db.insert("point", null, pointValues(it)) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun pointValues(point: TrackPoint) = ContentValues().apply {
        put("lat", point.latitude)
        put("lng", point.longitude)
        point.altitude?.let { put("altitude", it) }
        point.speed?.let { put("speed", it) }
        put("recorded_at", point.recordedAt.toEpochMilli())
        put("reconstructed", if (point.isReconstructed) 1 else 0)
    }

    fun updateSession(status: String, elapsedMs: Long, distanceMeters: Double, steps: Int?, finishedAtMs: Long?) {
        writableDatabase.update(
            "session",
            ContentValues().apply {
                put("status", status)
                put("elapsed_ms", elapsedMs)
                put("distance", distanceMeters)
                if (steps != null) put("steps", steps) else putNull("steps")
                if (finishedAtMs != null) put("finished_at", finishedAtMs) else putNull("finished_at")
            },
            "id = 1",
            null,
        )
    }

    fun clear() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("point", null, null)
            db.delete("session", null, null)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private companion object {
        const val VERSION = 3
    }
}
