package com.trailify.backend.feed

import com.trailify.backend.route.validationError
import com.trailify.backend.web.ApiException
import com.trailify.backend.web.ApiFieldError
import java.util.UUID

/** Komentarz może usunąć jego autor, autor wpisu (porządkuje swój wpis) albo administrator. */
fun canDeleteComment(commentAuthorId: UUID, eventAuthorId: UUID, viewerId: UUID, isAdmin: Boolean): Boolean =
    isAdmin || viewerId == commentAuthorId || viewerId == eventAuthorId

/**
 * Oznaczyć (odpowiedzieć) można autora wpisu albo osobę, która już komentowała ten wpis.
 * Dzięki temu nie da się oznaczać dowolnych użytkowników. Zwraca wyjątek do rzucenia albo null.
 */
fun replyRejection(replyToUserId: UUID?, isParticipant: Boolean): ApiException? =
    if (replyToUserId != null && !isParticipant) {
        validationError(listOf(ApiFieldError("replyToUserId", "Można odpowiedzieć tylko autorowi wpisu albo osobie, która komentowała")))
    } else {
        null
    }
