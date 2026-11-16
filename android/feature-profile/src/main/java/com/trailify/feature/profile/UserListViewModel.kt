package com.trailify.feature.profile

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trailify.core.domain.model.AppException
import com.trailify.core.domain.model.Page
import com.trailify.core.domain.model.UserSummary
import com.trailify.core.domain.repository.SocialRepository
import com.trailify.core.ui.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Którą listę osób pokazujemy. [route] to wartość w adresie ekranu. */
enum class UserListKind(val route: String, val title: String) {
    FOLLOWERS("followers", "Obserwujący"),
    FOLLOWING("following", "Obserwowani"),
    ;

    companion object {
        fun fromRoute(value: String?): UserListKind = entries.firstOrNull { it.route == value } ?: FOLLOWERS
    }
}

data class UserListUiState(
    val items: List<UserSummary> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val error: String? = null,
    /** Profil prywatny: lista jest ukryta. */
    val hidden: Boolean = false,
)

/** Lista obserwujących albo obserwowanych danego użytkownika, strona po stronie. */
class UserListViewModel(
    private val userId: String,
    private val kind: UserListKind,
    private val social: SocialRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(UserListUiState())
    val state: StateFlow<UserListUiState> = _state.asStateFlow()

    private var nextPage = 0

    fun load() {
        nextPage = 0
        _state.value = UserListUiState()
        fetch()
    }

    fun loadMore() {
        val current = _state.value
        if (!current.hasMore || current.isLoading || current.isLoadingMore) return
        _state.update { it.copy(isLoadingMore = true, error = null) }
        fetch()
    }

    private fun fetch() {
        viewModelScope.launch {
            try {
                val page = request(nextPage)
                nextPage++
                _state.update {
                    it.copy(
                        items = it.items + page.items,
                        isLoading = false,
                        isLoadingMore = false,
                        hasMore = nextPage < page.totalPages,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Pobieranie listy osób nie powiodło się", e)
                val hidden = (e as? AppException)?.code == "PRIVATE_PROFILE"
                _state.update {
                    it.copy(isLoading = false, isLoadingMore = false, hidden = hidden, error = if (hidden) null else e.toUserMessage())
                }
            }
        }
    }

    private suspend fun request(page: Int): Page<UserSummary> = when (kind) {
        UserListKind.FOLLOWERS -> social.followers(userId, page, PAGE_SIZE)
        UserListKind.FOLLOWING -> social.following(userId, page, PAGE_SIZE)
    }

    internal companion object {
        const val TAG = "UserList"
        const val PAGE_SIZE = 30
    }
}
