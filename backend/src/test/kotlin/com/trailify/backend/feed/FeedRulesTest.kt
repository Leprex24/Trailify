package com.trailify.backend.feed

import com.trailify.backend.web.ApiException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.util.UUID

class FeedRulesTest {

    private val author = UUID.randomUUID()
    private val eventAuthor = UUID.randomUUID()
    private val other = UUID.randomUUID()

    // --- usuwanie komentarzy ---

    @Test
    fun `komentarz moze usunac jego autor`() {
        assertThat(canDeleteComment(author, eventAuthor, viewerId = author, isAdmin = false)).isTrue()
    }

    @Test
    fun `komentarz pod wlasnym wpisem moze usunac autor wpisu`() {
        assertThat(canDeleteComment(author, eventAuthor, viewerId = eventAuthor, isAdmin = false)).isTrue()
    }

    @Test
    fun `administrator moze usunac kazdy komentarz`() {
        assertThat(canDeleteComment(author, eventAuthor, viewerId = other, isAdmin = true)).isTrue()
    }

    @Test
    fun `postronna osoba nie usunie cudzego komentarza`() {
        assertThat(canDeleteComment(author, eventAuthor, viewerId = other, isAdmin = false)).isFalse()
    }

    // --- oznaczenie osoby w odpowiedzi ---

    @Test
    fun `zwykly komentarz bez oznaczenia jest poprawny`() {
        assertThat(replyRejection(replyToUserId = null, isParticipant = false)).isNull()
    }

    @Test
    fun `mozna odpowiedziec autorowi wpisu albo komentujacemu`() {
        assertThat(replyRejection(replyToUserId = other, isParticipant = true)).isNull()
    }

    @Test
    fun `nie mozna oznaczyc kogos, kto nie bierze udzialu w rozmowie`() {
        val e: ApiException = replyRejection(replyToUserId = other, isParticipant = false)!!
        assertThat(e.status).isEqualTo(HttpStatus.BAD_REQUEST)
        assertThat(e.code).isEqualTo("VALIDATION_ERROR")
        assertThat(e.fieldErrors!!.map { it.field }).containsExactly("replyToUserId")
    }
}
