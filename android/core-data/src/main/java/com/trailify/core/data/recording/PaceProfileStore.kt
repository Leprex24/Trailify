package com.trailify.core.data.recording

import android.content.Context
import com.trailify.core.domain.model.ActivityType
import com.trailify.core.domain.recording.PaceProfile

/**
 * Zapamiętuje na telefonie profil tempa użytkownika ([PaceProfile]) osobno dla każdego rodzaju aktywności,
 * żeby wiedza z poprzednich tras była dostępna od pierwszych sekund kolejnej.
 */
class PaceProfileStore(context: Context) {

    private val prefs = context.getSharedPreferences("trailify_pace_profile", Context.MODE_PRIVATE)

    fun load(type: ActivityType): PaceProfile = try {
        PaceProfile(
            meters = Double.fromBits(prefs.getLong("${type.name}_meters", 0.0.toRawBits())),
            seconds = Double.fromBits(prefs.getLong("${type.name}_seconds", 0.0.toRawBits())),
        )
    } catch (e: ClassCastException) {
        PaceProfile()
    }

    fun save(type: ActivityType, profile: PaceProfile) {
        prefs.edit()
            .putLong("${type.name}_meters", profile.meters.toRawBits())
            .putLong("${type.name}_seconds", profile.seconds.toRawBits())
            .apply()
    }
}
