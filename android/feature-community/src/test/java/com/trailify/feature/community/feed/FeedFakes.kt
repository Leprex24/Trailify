package com.trailify.feature.community.feed

import com.trailify.core.domain.model.AppException
import com.trailify.core.domain.model.Comment
import com.trailify.core.domain.model.FeedEventType
import com.trailify.core.domain.model.FeedItem
import com.trailify.core.domain.model.Page
import com.trailify.core.domain.model.UserSummary
import com.trailify.core.domain.repository.AuthRepository
import com.trailify.core.domain.repository.FeedRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant

internal val ME = UserSummary("ja", "Ja")
internal val ANNA = UserSummary("anna", "Anna")
internal val JAN = UserSummary("jan", "Jan")

internal fun feedItem(id: Long, author: UserSummary = ANNA, likes: Int = 0, liked: Boolean = false) =
    FeedItem(id, FeedEventType.POI_COLLECTED, Instant.EPOCH, author, likeCount = likes, likedByMe = liked)

internal fun comment(id: Long, author: UserSummary = ANNA, replyTo: UserSummary? = null) =
    Comment(id, author, replyTo, "Komentarz $id", Instant.EPOCH)

internal class FakeAuth(userId: String? = ME.id) : AuthRepository {
    override val currentUser: StateFlow<UserSummary?> = MutableStateFlow(userId?.let { UserSummary(it, "Ja") })
    override suspend fun signIn(email: String, password: String) = Unit
    override suspend fun signUp(email: String, password: String, displayName: String) = Unit
    override suspend fun signOut() = Unit
}

internal class FakeFeed(
    var items: List<FeedItem> = emptyList(),
    var comments: List<Comment> = emptyList(),
) : FeedRepository {
    var fail = false
    val requestedPages = mutableListOf<Int>()
    val likeCalls = mutableListOf<Long>()
    val unlikeCalls = mutableListOf<Long>()
    val added = mutableListOf<Triple<Long, String, String?>>()
    val deleted = mutableListOf<Long>()

    private fun check() {
        if (fail) throw AppException("NETWORK_ERROR", "offline")
    }

    override suspend fun feed(page: Int, size: Int): Page<FeedItem> {
        check()
        requestedPages += page
        val slice = items.drop(page * size).take(size)
        return Page(slice, page, size, items.size.toLong(), (items.size + size - 1) / size)
    }

    override suspend fun like(eventId: Long) {
        check()
        likeCalls += eventId
    }

    override suspend fun unlike(eventId: Long) {
        check()
        unlikeCalls += eventId
    }

    override suspend fun comments(eventId: Long, page: Int, size: Int): Page<Comment> {
        check()
        return Page(comments, 0, size, comments.size.toLong(), 1)
    }

    override suspend fun addComment(eventId: Long, body: String, replyToUserId: String?): Comment {
        check()
        added += Triple(eventId, body, replyToUserId)
        return Comment(100L + added.size, ME, replyToUserId?.let { UserSummary(it, "X") }, body, Instant.EPOCH)
    }

    override suspend fun deleteComment(commentId: Long) {
        check()
        deleted += commentId
    }
}
