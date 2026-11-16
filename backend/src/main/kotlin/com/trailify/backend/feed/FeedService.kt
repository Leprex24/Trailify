package com.trailify.backend.feed

import com.trailify.backend.account.ProfileRecord
import com.trailify.backend.common.PageRequest
import com.trailify.backend.common.PageResponse
import com.trailify.backend.common.pageOf
import com.trailify.backend.web.ApiException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service

/** Oś czasu osób obserwowanych, polubienia i komentarze wpisów. */
@Service
class FeedService(private val repository: FeedRepository) {

    fun feed(viewer: ProfileRecord, page: PageRequest): PageResponse<FeedItemResponse> =
        pageOf(page, repository.countFeed(viewer.id), repository.feed(viewer.id, page))

    /** Polubienie wpisu; ponowne polubienie nic nie zmienia. */
    fun like(viewer: ProfileRecord, eventId: Long) {
        requireEvent(eventId)
        repository.like(eventId, viewer.id)
    }

    /** Cofnięcie polubienia zawsze działa. */
    fun unlike(viewer: ProfileRecord, eventId: Long) = repository.unlike(eventId, viewer.id)

    fun comments(eventId: Long, page: PageRequest): PageResponse<CommentResponse> {
        requireEvent(eventId)
        return pageOf(page, repository.countComments(eventId), repository.listComments(eventId, page))
    }

    fun addComment(viewer: ProfileRecord, eventId: Long, request: CommentCreateRequest): CommentResponse {
        requireEvent(eventId)
        val replyTo = request.replyToUserId
        replyRejection(replyTo, isParticipant = replyTo == null || repository.isParticipant(eventId, replyTo))?.let { throw it }
        val id = repository.insertComment(eventId, viewer.id, replyTo, request.body!!.trim())
        return repository.findComment(id)!!
    }

    fun deleteComment(viewer: ProfileRecord, commentId: Long) {
        val comment = repository.commentOwnership(commentId)
            ?: throw ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Komentarz nie istnieje")
        if (!canDeleteComment(comment.authorId, comment.eventAuthorId, viewer.id, isAdmin = viewer.role == ROLE_ADMIN)) {
            throw ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Możesz usunąć tylko własny komentarz albo komentarz pod własnym wpisem")
        }
        repository.deleteComment(commentId)
    }

    /** Wpis ukryty (trasa znów prywatna, profil prywatny, konto zablokowane) udaje, że nie istnieje. */
    private fun requireEvent(eventId: Long): FeedEventRef =
        repository.findVisible(eventId) ?: throw ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Wpis nie istnieje")

    private companion object {
        const val ROLE_ADMIN = "ADMIN"
    }
}
