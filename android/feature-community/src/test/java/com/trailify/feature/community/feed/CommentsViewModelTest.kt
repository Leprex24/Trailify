package com.trailify.feature.community.feed

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CommentsViewModelTest {

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(feed: FakeFeed, eventAuthorId: String = ANNA.id) = CommentsViewModel(1, eventAuthorId, feed, FakeAuth())

    @Test
    fun wczytujeKomentarzeILiczbeLaczna() {
        val vm = viewModel(FakeFeed(comments = listOf(comment(1), comment(2))))
        assertEquals(2, vm.state.value.comments.size)
        assertEquals(2, vm.state.value.total)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun wysylaKomentarzIZwiekszaLiczbe() {
        val feed = FakeFeed(comments = listOf(comment(1)))
        val vm = viewModel(feed)
        vm.onDraftChange("  Ładna trasa  ")
        vm.send()
        assertEquals(listOf(Triple(1L, "Ładna trasa", null as String?)), feed.added)
        assertEquals(2, vm.state.value.comments.size)
        assertEquals(2, vm.state.value.total)
        assertEquals("", vm.state.value.draft)
    }

    @Test
    fun pustyKomentarzNieJestWysylany() {
        val feed = FakeFeed()
        val vm = viewModel(feed)
        vm.onDraftChange("   ")
        vm.send()
        assertTrue(feed.added.isEmpty())
    }

    @Test
    fun odpowiedzOznaczaOsobeIKasujeSieZeSzkicem() {
        val feed = FakeFeed(comments = listOf(comment(1, author = JAN)))
        val vm = viewModel(feed)
        vm.startReply(JAN)
        assertEquals(JAN, vm.state.value.replyTo)
        vm.onDraftChange("Zgadzam się")
        vm.send()
        assertEquals("jan", feed.added.single().third)
        assertNull(vm.state.value.replyTo)
    }

    @Test
    fun nieMoznaOdpowiadacSobie() {
        val vm = viewModel(FakeFeed())
        vm.startReply(ME)
        assertNull(vm.state.value.replyTo)
    }

    @Test
    fun bladWysylaniaZostawiaSzkicIPokazujeKomunikat() {
        val feed = FakeFeed()
        val vm = viewModel(feed)
        vm.onDraftChange("Treść")
        feed.fail = true
        vm.send()
        assertEquals("Treść", vm.state.value.draft)
        assertNotNull(vm.state.value.message)
        assertFalse(vm.state.value.isSending)
    }

    @Test
    fun autorKomentarzaIAutorWpisuMogaUsuwac_innyNie() {
        val own = comment(1, author = ME)
        val foreign = comment(2, author = JAN)
        assertTrue(canDeleteComment(own, ME.id, ANNA.id))
        assertFalse(canDeleteComment(foreign, ME.id, ANNA.id))
        assertTrue(canDeleteComment(foreign, ME.id, ME.id))
        assertFalse(canDeleteComment(own, null, ANNA.id))
    }

    @Test
    fun usuniecieZdejmujeKomentarzIZmniejszaLiczbe() {
        val feed = FakeFeed(comments = listOf(comment(1, author = ME), comment(2, author = JAN)))
        val vm = viewModel(feed)
        vm.delete(comment(1, author = ME))
        assertEquals(listOf(1L), feed.deleted)
        assertEquals(listOf(2L), vm.state.value.comments.map { it.id })
        assertEquals(1, vm.state.value.total)
    }

    @Test
    fun cudzegoKomentarzaNieUsuwaBezUprawnien() {
        val feed = FakeFeed(comments = listOf(comment(2, author = JAN)))
        val vm = viewModel(feed)
        vm.delete(comment(2, author = JAN))
        assertTrue(feed.deleted.isEmpty())
        assertEquals(1, vm.state.value.comments.size)
    }

    @Test
    fun komentarzDluzszyNizLimitJestObcinany() {
        val vm = viewModel(FakeFeed())
        vm.onDraftChange("a".repeat(COMMENT_MAX + 50))
        assertEquals(COMMENT_MAX, vm.state.value.draft.length)
    }
}
