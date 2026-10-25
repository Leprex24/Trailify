package com.trailify.core.domain.recording

import com.trailify.core.domain.model.ActivityDraft
import com.trailify.core.domain.model.RecordingState
import com.trailify.core.domain.model.RecordingStatus

/**
 * Zamienia zakończone nagranie na dane do zapisu na serwerze.
 * Zwraca null, gdy nagrania nie da się zapisać: nie jest zakończone albo ma mniej niż 2 punkty GPS
 * (serwer wymaga co najmniej dwóch, bo trasa to linia).
 */
fun RecordingState.toActivityDraft(): ActivityDraft? {
    if (status != RecordingStatus.FINISHED) return null
    val id = clientId ?: return null
    val start = startedAt ?: return null
    val end = finishedAt ?: return null
    if (points.size < 2) return null

    val pace = paceMinPerKm(averageSpeedMps(distanceMeters, elapsedSeconds))
    return ActivityDraft(
        clientId = id,
        type = type,
        routeId = routeId,
        startTime = start,
        endTime = if (end.isBefore(start)) start else end,
        distance = distanceMeters,
        durationSeconds = elapsedSeconds.toInt(),
        avgPace = pace ?: 0.0,
        stepCount = steps,
        points = points,
    )
}
