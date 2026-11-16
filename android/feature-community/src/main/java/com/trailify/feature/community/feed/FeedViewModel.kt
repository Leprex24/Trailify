package com.trailify.feature.community.feed

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trailify.core.domain.model.FeedItem
import com.trailify.core.domain.model.withLiked
import com.trailify.core.domain.repository.FeedRepository
import com.trailify.core.ui.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FeedUiState(
    val items: List<FeedItem> = emptyList(),
    /** Pierwsze wczytywanie (puste okno z kółkiem). Kolejne odświeżenia nie czyszczą listy. */
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    /** Błąd wczytywania: na ekranie z przyciskiem "Spróbuj ponownie". */
    val error: String? = null,
    /** Krótki komunikat o nieudanym polubieniu. */
    val message: String? = null,
)

/**
 * Oś czasu osób obserwowanych: wpisy strona po stronie i polubienia. Polubienie zmienia wpis od razu
 * (znacznik i licznik), a przy błędzie sieci wraca do poprzedniego stanu.
 */
class FeedViewModel(private val feed: FeedRepository) : ViewModel() {

    private val _state = MutableStateFlow(FeedUiState())
    val state: StateFlow<FeedUiState> = _state.asStateFlow()

    private var nextPage = 0

    /** Wpisy, których polubienie właśnie się wysyła (drugie dotknięcie w tym czasie jest ignorowane). */
    private val liking = mutableSetOf<Long>()

    init {
        load()
    }

    /** Pobiera pierwszą stronę od nowa. Już widoczne wpisy zostają na ekranie do czasu nowych danych. */
    fun load() {
        viewModelScope.launch {
            if (_state.value.items.isEmpty()) _state.update { it.copy(isLoading = true, error = null) }
            try {
                val page = feed.feed(page = 0, size = PAGE_SIZE)
                nextPage = 1
                _state.update {
                    it.copy(
                        items = page.items,
                        isLoading = false,
                        error = null,
                        hasMore = nextPage < page.totalPages,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Pobieranie osi czasu nie powiodło się", e)
                _state.update { it.copy(isLoading = false, error = if (it.items.isEmpty()) e.toUserMessage() else null) }
            }
        }
    }

    fun loadMore() {
        val current = _state.value
        if (!current.hasMore || current.isLoading || current.isLoadingMore) return
        _state.update { it.copy(isLoadingMore = true, message = null) }
        viewModelScope.launch {
            try {
                val page = feed.feed(page = nextPage, size = PAGE_SIZE)
                nextPage++
                _state.update {
                    // wpis mógł się już pojawić na poprzedniej stronie (nowe wpisy przesuwają strony)
                    val known = it.items.map { item -> item.id }.toSet()
                    it.copy(
                        items = it.items + page.items.filter { item -> item.id !in known },
                        isLoadingMore = false,
                        hasMore = nextPage < page.totalPages,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Pobieranie kolejnej strony osi czasu nie powiodło się", e)
                _state.update { it.copy(isLoadingMore = false, message = e.toUserMessage()) }
            }
        }
    }

    fun toggleLike(eventId: Long) {
        val item = _state.value.items.firstOrNull { it.id == eventId } ?: return
        if (!liking.add(eventId)) return
        val liked = !item.likedByMe
        replace(eventId) { it.withLiked(liked) }
        viewModelScope.launch {
            try {
                if (liked) feed.like(eventId) else feed.unlike(eventId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Zmiana polubienia nie powiodła się", e)
                replace(eventId) { it.withLiked(!liked) }
                _state.update { it.copy(message = e.toUserMessage()) }
            } finally {
                liking.remove(eventId)
            }
        }
    }

    /** Liczba komentarzy zmieniła się w oknie komentarzy (dodanie albo usunięcie). */
    fun onCommentCount(eventId: Long, count: Int) = replace(eventId) { it.copy(commentCount = count) }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    private fun replace(eventId: Long, change: (FeedItem) -> FeedItem) =
        _state.update { state -> state.copy(items = state.items.map { if (it.id == eventId) change(it) else it }) }

    internal companion object {
        const val TAG = "Feed"
        const val PAGE_SIZE = 20
    }
}
