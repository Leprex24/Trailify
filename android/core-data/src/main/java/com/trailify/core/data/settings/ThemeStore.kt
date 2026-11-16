package com.trailify.core.data.settings

import android.content.Context
import com.trailify.core.domain.repository.ThemeMode
import com.trailify.core.domain.repository.ThemeSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Zapamiętuje na telefonie wybrany motyw (jasny, ciemny albo zgodny z systemem). */
class ThemeStore(context: Context) : ThemeSettings {

    private val prefs = context.getSharedPreferences("trailify_settings", Context.MODE_PRIVATE)

    private val _mode = MutableStateFlow(load())
    override val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    override fun setMode(mode: ThemeMode) {
        _mode.value = mode
        prefs.edit().putString(KEY_THEME, mode.name).apply()
    }

    private fun load(): ThemeMode =
        ThemeMode.entries.firstOrNull { it.name == prefs.getString(KEY_THEME, null) } ?: ThemeMode.SYSTEM

    private companion object {
        const val KEY_THEME = "theme_mode"
    }
}
