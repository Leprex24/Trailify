package com.trailify.core.domain.recording

import com.trailify.core.domain.model.ActivityType
import com.trailify.core.domain.model.RecordingState
import com.trailify.core.domain.model.RecordingStatus
import com.trailify.core.domain.model.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class ActivityDraftFactoryTest {

    private val t0 = Instant.parse("2026-11-03T10:00:00Z")
    private val points = listOf(
        TrackPoint(51.0, 22.0, recordedAt = t0),
        TrackPoint(51.001, 22.0, recordedAt = t0.plusSeconds(60)),
    )

    private fun finished() = RecordingState(
        status = RecordingStatus.FINISHED,
        type = ActivityType.RUNNING,
        points = points,
        distanceMeters = 1000.0,
        elapsedSeconds = 300,
        steps = 800,
        startedAt = t0,
        finishedAt = t0.plusSeconds(310),
        clientId = "11111111-1111-1111-1111-111111111111",
    )

    @Test
    fun `zakonczone nagranie daje komplet danych`() {
        val draft = finished().toActivityDraft()
        assertNotNull(draft)
        assertEquals(ActivityType.RUNNING, draft!!.type)
        assertEquals(1000.0, draft.distance, 0.0)
        assertEquals(300, draft.durationSeconds)
        // 1000 m w 300 s = 3,33 m/s = 5 min/km
        assertEquals(5.0, draft.avgPace, 0.01)
        assertEquals(800, draft.stepCount)
        assertEquals(2, draft.points.size)
    }

    @Test
    fun `trwajace nagranie nie nadaje sie do zapisu`() {
        assertNull(finished().copy(status = RecordingStatus.RECORDING).toActivityDraft())
    }

    @Test
    fun `za malo punktow`() {
        assertNull(finished().copy(points = points.take(1)).toActivityDraft())
    }

    @Test
    fun `bez dystansu tempo wynosi zero`() {
        assertEquals(0.0, finished().copy(distanceMeters = 0.0).toActivityDraft()!!.avgPace, 0.0)
    }
}
