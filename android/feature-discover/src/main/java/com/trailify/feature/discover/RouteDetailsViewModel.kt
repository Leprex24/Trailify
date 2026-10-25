package com.trailify.feature.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trailify.core.domain.model.PlannedRoute
import com.trailify.core.domain.model.Route
import com.trailify.core.domain.repository.FollowRequest
import com.trailify.core.domain.repository.RouteFollowController
import com.trailify.core.domain.repository.RouteRepository
import com.trailify.core.ui.UiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RouteDetailsViewModel(
    private val routes: RouteRepository,
    private val follow: RouteFollowController,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<Route>>(UiState.Loading)
    val state: StateFlow<UiState<Route>> = _state.asStateFlow()

    /** "Przejdź tę trasę": mapa START pokaże trasę i poprowadzi po niej (zapis aktywności dostanie routeId). */
    fun followRoute(route: Route) {
        follow.follow(
            FollowRequest(
                routeId = route.summary.id,
                name = route.summary.name,
                activityType = route.summary.activityType,
                route = PlannedRoute(
                    distance = route.summary.totalDistance ?: 0.0,
                    durationSeconds = route.summary.durationSeconds ?: 0,
                    geometry = route.geometry,
                ),
            ),
        )
    }

    fun load(routeId: Long) {
        viewModelScope.launch {
            _state.value = UiState.Loading
            _state.value = try {
                UiState.Success(routes.get(routeId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UiState.Error(e.message ?: "Nie udało się pobrać trasy")
            }
        }
    }
}
