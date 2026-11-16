package com.trailify.core.data

import com.trailify.core.domain.model.Poi
import com.trailify.core.domain.repository.PoiFocusController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Prośba trzymana tylko w pamięci aplikacji. */
class InMemoryPoiFocusController : PoiFocusController {
    private val _request = MutableStateFlow<Poi?>(null)
    override val request: StateFlow<Poi?> = _request.asStateFlow()

    override fun focus(poi: Poi) {
        _request.value = poi
    }

    override fun consume() {
        _request.value = null
    }
}
