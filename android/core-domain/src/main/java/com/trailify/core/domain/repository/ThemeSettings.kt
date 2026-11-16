package com.trailify.core.domain.repository

import kotlinx.coroutines.flow.StateFlow

/** Motyw aplikacji: zgodny z ustawieniem telefonu albo wymuszony jasny lub ciemny. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Wybrany motyw, zapamiętany na telefonie. Zmiana działa od razu w całej aplikacji. */
interface ThemeSettings {
    val mode: StateFlow<ThemeMode>
    fun setMode(mode: ThemeMode)
}
