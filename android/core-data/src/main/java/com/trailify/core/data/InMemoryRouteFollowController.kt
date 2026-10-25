package com.trailify.core.data

import com.trailify.core.domain.repository.FollowRequest
import com.trailify.core.domain.repository.RouteFollowController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Prośba trzymana tylko w pamięci aplikacji (po jej zamknięciu nie ma po co jej przywracać). */
class InMemoryRouteFollowController : RouteFollowController {
    private val _request = MutableStateFlow<FollowRequest?>(null)
    override val request: StateFlow<FollowRequest?> = _request.asStateFlow()

    override fun follow(request: FollowRequest) {
        _request.value = request
    }

    override fun consume() {
        _request.value = null
    }
}
