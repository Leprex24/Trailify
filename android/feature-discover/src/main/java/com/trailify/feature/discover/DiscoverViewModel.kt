package com.trailify.feature.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trailify.core.domain.model.RouteQuery
import com.trailify.core.domain.model.RouteSummary
import com.trailify.core.domain.repository.RouteRepository
import com.trailify.core.ui.UiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * WZORZEC dla pozostałych ekranów: ViewModel dostaje interfejs repozytorium (nie wie, czy to atrapa,
 * czy prawdziwe API), trzyma stan jako StateFlow<UiState<...>>, ekran go tylko wyświetla.
 */
class DiscoverViewModel(private val routes: RouteRepository) : ViewModel() {

    private val _state = MutableStateFlow<UiState<List<RouteSummary>>>(UiState.Loading)
    val state: StateFlow<UiState<List<RouteSummary>>> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            _state.value = try {
                UiState.Success(routes.search(RouteQuery()).items)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UiState.Error(e.message ?: "Nie udało się pobrać tras")
            }
        }
    }
}
