package com.trailify.feature.community.feed

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trailify.core.domain.model.Comment
import com.trailify.core.domain.model.UserSummary
import com.trailify.core.domain.repository.AuthRepository
import com.trailify.core.domain.repository.FeedRepository
import com.trailify.core.ui.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Najdłuższy komentarz (limit z openapi.yaml). */
const val COMMENT_MAX = 1000

data class CommentsUiState(
    val comments: List<Comment> = emptyList(),
    val total: Int = 0,
    val isLoading: Boolean = true,
    val error: String? = null,
    val draft: String = "",
    /** Osoba oznaczona w odpowiedzi; null przy zwykłym komentarzu. */
    val replyTo: UserSummary? = null,
    val isSending: Boolean = false,
    val message: String? = null,
)

/** Komentarz może dodać każdy, kto widzi wpis, a usunąć jego autor albo autor wpisu. */
fun canDeleteComment(comment: Comment, meId: String?, eventAuthorId: String): Boolean =
    meId != null && (comment.author.id == meId || eventAuthorId == meId)

/** Komentarze jednego wpisu osi czasu z odpowiedziami (oznaczeniem osoby, której się odpowiada). */
class CommentsViewModel(
    private val eventId: Long,
    val eventAuthorId: String,
    private val feed: FeedRepository,
    auth: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CommentsUiState())
    val state: StateFlow<CommentsUiState> = _state.asStateFlow()

    /** Id zalogowanego użytkownika (do przycisków usuwania i ukrycia "Odpowiedz" przy własnych komentarzach). */
    val meId: String? = auth.currentUser.value?.id

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val page = feed.comments(eventId, page = 0, size = PAGE_SIZE)
                _state.update { it.copy(comments = page.items, total = page.totalElements.toInt(), isLoading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Pobieranie komentarzy nie powiodło się", e)
                _state.update { it.copy(isLoading = false, error = e.toUserMessage()) }
            }
        }
    }

    fun onDraftChange(text: String) = _state.update { it.copy(draft = text.take(COMMENT_MAX), message = null) }

    /** Odpowiedź osobie: jej nazwa pojawi się w komentarzu jako oznaczenie. */
    fun startReply(user: UserSummary) {
        if (user.id == meId) return
        _state.update { it.copy(replyTo = user) }
    }

    fun cancelReply() = _state.update { it.copy(replyTo = null) }

    fun send() {
        val current = _state.value
        val body = current.draft.trim()
        if (body.isEmpty() || current.isSending) return
        _state.update { it.copy(isSending = true, message = null) }
        viewModelScope.launch {
            try {
                val comment = feed.addComment(eventId, body, current.replyTo?.id)
                _state.update {
                    it.copy(
                        comments = it.comments + comment,
                        total = it.total + 1,
                        draft = "",
                        replyTo = null,
                        isSending = false,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Dodanie komentarza nie powiodło się", e)
                _state.update { it.copy(isSending = false, message = e.toUserMessage()) }
            }
        }
    }

    fun delete(comment: Comment) {
        if (!canDeleteComment(comment, meId, eventAuthorId)) return
        viewModelScope.launch {
            try {
                feed.deleteComment(comment.id)
                _state.update {
                    it.copy(
                        comments = it.comments.filterNot { c -> c.id == comment.id },
                        total = (it.total - 1).coerceAtLeast(0),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Usunięcie komentarza nie powiodło się", e)
                _state.update { it.copy(message = e.toUserMessage()) }
            }
        }
    }

    internal companion object {
        const val TAG = "Comments"
        const val PAGE_SIZE = 100
    }
}
