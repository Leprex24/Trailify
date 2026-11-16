package com.trailify.feature.community.feed

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trailify.core.domain.model.UserSummary
import com.trailify.core.domain.repository.SocialRepository
import com.trailify.core.ui.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Najkrótsza fraza wysyłana do serwera (zgodnie z openapi.yaml: 2-50 znaków). */
const val SEARCH_QUERY_MIN = 2
const val SEARCH_QUERY_MAX = 50

data class UserSearchUiState(
    val query: String = "",
    val results: List<UserSummary> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    /** Czy dla bieżącej frazy odbyło się już wyszukiwanie (żeby odróżnić "brak wyników" od "jeszcze nie szukano"). */
    val searched: Boolean = false,
    val error: String? = null,
)

/** Szukanie osób po nazwie. Zapytanie idzie do serwera po krótkiej pauzie w pisaniu. */
class UserSearchViewModel(private val social: SocialRepository) : ViewModel() {

    private val _state = MutableStateFlow(UserSearchUiState())
    val state: StateFlow<UserSearchUiState> = _state.asStateFlow()

    private var job: Job? = null
    private var nextPage = 0

    fun onQueryChange(text: String) {
        val query = text.take(SEARCH_QUERY_MAX)
        job?.cancel()
        _state.update { UserSearchUiState(query = query) }
        if (query.trim().length < SEARCH_QUERY_MIN) return
        _state.update { it.copy(isLoading = true) }
        job = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            fetch(query.trim(), page = 0)
        }
    }

    fun retry() = onQueryChange(_state.value.query)

    fun loadMore() {
        val current = _state.value
        if (!current.hasMore || current.isLoading || current.isLoadingMore) return
        _state.update { it.copy(isLoadingMore = true, error = null) }
        job = viewModelScope.launch { fetch(current.query.trim(), nextPage) }
    }

    private suspend fun fetch(query: String, page: Int) {
        try {
            val result = social.search(query, page = page, size = PAGE_SIZE)
            nextPage = page + 1
            _state.update {
                it.copy(
                    results = if (page == 0) result.items else it.results + result.items,
                    isLoading = false,
                    isLoadingMore = false,
                    hasMore = nextPage < result.totalPages,
                    searched = true,
                    error = null,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Wyszukiwanie osób nie powiodło się", e)
            _state.update { it.copy(isLoading = false, isLoadingMore = false, searched = true, error = e.toUserMessage()) }
        }
    }

    internal companion object {
        const val TAG = "UserSearch"
        const val DEBOUNCE_MS = 350L
        const val PAGE_SIZE = 30
    }
}
