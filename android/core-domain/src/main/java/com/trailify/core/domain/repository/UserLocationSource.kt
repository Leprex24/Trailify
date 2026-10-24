package com.trailify.core.domain.repository

import com.trailify.core.domain.model.LatLng
import kotlinx.coroutines.flow.StateFlow

/**
 * Aktualna pozycja użytkownika do pokazania na mapie (niebieska kropka).
 * Gdy trwa nagrywanie, pozycje dostarcza to samo źródło co nagranie, więc GPS pracuje tylko raz.
 */
interface UserLocationSource {
    /** Ostatnia znana pozycja, null dopóki nie złapano sygnału. */
    val location: StateFlow<LatLng?>

    /**
     * Włącza lub wyłącza śledzenie pozycji na potrzeby mapy (wywoływane, gdy mapa jest widoczna).
     * Bez zgody na lokalizację nic nie robi; po przyznaniu zgody trzeba wywołać ponownie.
     */
    fun setWatching(watching: Boolean)
}
