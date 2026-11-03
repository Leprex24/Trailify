package com.trailify.feature.discover.reviews

import com.trailify.core.domain.model.AppException
import com.trailify.core.ui.UiState
import com.trailify.feature.discover.FakeActivityRepository
import com.trailify.feature.discover.FakeAuth
import com.trailify.feature.discover.FakeReviewRepository
import com.trailify.feature.discover.InstantUploader
import com.trailify.feature.discover.ME
import com.trailify.feature.discover.review
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReviewsViewModelTest {

    private val reviews = FakeReviewRepository()
    private val activities = FakeActivityRepository(completedRouteIds = setOf(ROUTE))

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(ownerId: String = "owner", userId: String? = ME) =
        ReviewsViewModel(ROUTE, ownerId, reviews, activities, FakeAuth(userId), InstantUploader())

    // --- kto może ocenić (czysta funkcja) ---

    @Test
    fun zasadyOceniania() {
        assertEquals(ReviewEligibility.LOGGED_OUT, decideEligibility(null, "o", emptyList(), true))
        assertEquals(ReviewEligibility.OWN_ROUTE, decideEligibility("o", "o", emptyList(), true))
        assertEquals(ReviewEligibility.ALREADY_REVIEWED, decideEligibility(ME, "o", listOf(review(1, ME)), true))
        assertEquals(ReviewEligibility.NOT_COMPLETED, decideEligibility(ME, "o", emptyList(), false))
        assertEquals(ReviewEligibility.CAN_REVIEW, decideEligibility(ME, "o", emptyList(), true))
        // nie udało się sprawdzić aktywności: pozwalamy spróbować, serwer zdecyduje
        assertEquals(ReviewEligibility.CAN_REVIEW, decideEligibility(ME, "o", emptyList(), null))
    }

    @Test
    fun przebytaTrasaMozeBycOceniona() {
        assertEquals(ReviewEligibility.CAN_REVIEW, viewModel().ui.value.eligibility)
    }

    @Test
    fun nieprzebytaTrasaNieMozeBycOceniona() {
        activities.completedRouteIds = emptySet()
        assertEquals(ReviewEligibility.NOT_COMPLETED, viewModel().ui.value.eligibility)
    }

    @Test
    fun bezGwiazdekNieWysyla() {
        val vm = viewModel()
        vm.openForm()
        vm.submit()
        assertEquals("Wybierz ocenę od 1 do 5 gwiazdek", vm.ui.value.form?.ratingError)
        assertTrue(reviews.created.isEmpty())
    }

    @Test
    fun wyslanieOcenyZamykaFormularzIOdswiezaListe() {
        val vm = viewModel()
        vm.openForm()
        vm.onRatingChange(5)
        vm.onCommentChange("  Piękne widoki  ")
        vm.onPhotosPicked(listOf("content://1"))
        vm.submit()

        val draft = reviews.created.single()
        assertEquals(5, draft.rating)
        assertEquals("Piękne widoki", draft.comment)
        assertEquals(listOf("me/content://1"), draft.photos.map { it.storageKey })
        assertNull(vm.ui.value.form)
        assertEquals(1, vm.ui.value.changeCount)
        assertEquals(ReviewEligibility.ALREADY_REVIEWED, vm.ui.value.eligibility)
        assertEquals(1, (vm.ui.value.reviews as UiState.Success).data.size)
    }

    @Test
    fun maksymalniePiecZdjec() {
        val vm = viewModel()
        vm.openForm()
        vm.onPhotosPicked((1..7).map { "content://$it" })
        assertEquals(REVIEW_PHOTOS_MAX, vm.ui.value.form!!.photos.size)
    }

    @Test
    fun odmowaSerweraAktualizujeStan() {
        reviews.failCreateWith = AppException("ROUTE_NOT_COMPLETED", "nie")
        val vm = viewModel()
        vm.openForm()
        vm.onRatingChange(3)
        vm.submit()
        assertEquals("Możesz ocenić trasę dopiero po jej przejściu", vm.ui.value.form?.generalError)
        assertEquals(ReviewEligibility.NOT_COMPLETED, vm.ui.value.eligibility)
    }

    @Test
    fun usuniecieWlasnejRecenzji() {
        reviews.reviews += review(7, ME, ROUTE)
        val vm = viewModel()
        assertEquals(ReviewEligibility.ALREADY_REVIEWED, vm.ui.value.eligibility)
        vm.deleteReview(7)
        assertTrue((vm.ui.value.reviews as UiState.Success).data.isEmpty())
        assertEquals(ReviewEligibility.CAN_REVIEW, vm.ui.value.eligibility)
        assertEquals("Recenzja usunięta", vm.ui.value.message)
    }

    private companion object {
        const val ROUTE = 1L
    }
}
