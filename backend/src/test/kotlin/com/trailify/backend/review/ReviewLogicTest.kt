package com.trailify.backend.review

import com.trailify.backend.route.PhotoInputRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.util.UUID

class ReviewLogicTest {

    private val userId = UUID.fromString("3f1c2b7e-0000-0000-0000-000000000000")
    private val otherId = UUID.fromString("9a9a9a9a-0000-0000-0000-000000000000")

    private fun photo(key: String, url: String = "https://example.supabase.co/storage/v1/object/public/review-photos/$key") =
        PhotoInputRequest(storageKey = key, url = url)

    private fun request(photos: List<PhotoInputRequest>? = null) = ReviewCreateRequest(rating = 5, comment = "Super", photos = photos)

    // --- POST /routes/{id}/reviews: zdjęcia ---

    @Test
    fun `recenzja bez zdjec jest poprawna`() {
        assertThat(validateReviewCreate(userId, request())).isEmpty()
    }

    @Test
    fun `zdjecie z folderu autora jest poprawne`() {
        assertThat(validateReviewCreate(userId, request(listOf(photo("$userId/a.jpg"))))).isEmpty()
    }

    @Test
    fun `zdjecie z cudzego folderu jest odrzucone`() {
        val errors = validateReviewCreate(userId, request(listOf(photo("$otherId/a.jpg"))))
        assertThat(errors.map { it.field }).containsExactly("photos")
    }

    @Test
    fun `adres zdjecia musi byc https`() {
        val errors = validateReviewCreate(userId, request(listOf(photo("$userId/a.jpg", url = "http://example.com/a.jpg"))))
        assertThat(errors.map { it.field }).containsExactly("photos")
    }

    @Test
    fun `to samo zdjecie dwa razy jest odrzucone`() {
        val errors = validateReviewCreate(userId, request(listOf(photo("$userId/a.jpg"), photo("$userId/a.jpg"))))
        assertThat(errors.map { it.message }).contains("Zdjęcia nie mogą się powtarzać")
    }

    // --- zasady oceniania ---

    @Test
    fun `przebyta trasa bez wczesniejszej oceny moze byc oceniona`() {
        assertThat(reviewRejection(isOwner = false, completedRoute = true, alreadyReviewed = false)).isNull()
    }

    @Test
    fun `wlasciciel nie ocenia wlasnej trasy`() {
        val e = reviewRejection(isOwner = true, completedRoute = true, alreadyReviewed = false)!!
        assertThat(e.status).isEqualTo(HttpStatus.FORBIDDEN)
        assertThat(e.code).isEqualTo(CODE_OWN_ROUTE)
    }

    @Test
    fun `nieprzebyta trasa daje ROUTE_NOT_COMPLETED`() {
        val e = reviewRejection(isOwner = false, completedRoute = false, alreadyReviewed = false)!!
        assertThat(e.status).isEqualTo(HttpStatus.FORBIDDEN)
        assertThat(e.code).isEqualTo(CODE_ROUTE_NOT_COMPLETED)
    }

    @Test
    fun `druga ocena tej samej trasy daje 409`() {
        val e = reviewRejection(isOwner = false, completedRoute = true, alreadyReviewed = true)!!
        assertThat(e.status).isEqualTo(HttpStatus.CONFLICT)
        assertThat(e.code).isEqualTo(CODE_ALREADY_REVIEWED)
    }

    // --- DELETE /reviews/{id} ---

    @Test
    fun `autor moze usunac swoja recenzje`() {
        assertThat(canDeleteReview(authorId = userId, userId = userId, isAdmin = false)).isTrue()
    }

    @Test
    fun `inny uzytkownik nie moze usunac cudzej recenzji`() {
        assertThat(canDeleteReview(authorId = otherId, userId = userId, isAdmin = false)).isFalse()
    }

    @Test
    fun `administrator moze usunac kazda recenzje`() {
        assertThat(canDeleteReview(authorId = otherId, userId = userId, isAdmin = true)).isTrue()
    }
}
