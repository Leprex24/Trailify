package com.trailify.core.domain.repository

import com.trailify.core.domain.model.Poi
import kotlinx.coroutines.flow.StateFlow

/**
 * Most między ekranami (profil, oś czasu) a mapą START: "Zobacz na mapie". Ekran woła [focus], mapa odbiera
 * punkt z [request], przesuwa do niego kamerę i zaznacza prośbę jako [consume].
 */
interface PoiFocusController {
    val request: StateFlow<Poi?>
    fun focus(poi: Poi)
    fun consume()
}
