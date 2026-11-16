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
class FeedViewModelTest {

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun items(count: Int) = (1..count).map { feedItem(it.toLong()) }

    @Test
    fun wczytujePierwszaStroneIInformujeOKolejnych() {
        val vm = FeedViewModel(FakeFeed(items(FeedViewModel.PAGE_SIZE + 3)))
        val state = vm.state.value
        assertEquals(FeedViewModel.PAGE_SIZE, state.items.size)
        assertTrue(state.hasMore)
        assertFalse(state.isLoading)
    }

    @Test
    fun pustaOsKoncziSieBezBledu() {
        val vm = FeedViewModel(FakeFeed(emptyList()))
        assertTrue(vm.state.value.items.isEmpty())
        assertFalse(vm.state.value.isLoading)
        assertNull(vm.state.value.error)
    }

    @Test
    fun bladPierwszegoWczytaniaPokazujeKomunikat() {
        val feed = FakeFeed(items(2)).apply { fail = true }
        val vm = FeedViewModel(feed)
        assertNotNull(vm.state.value.error)
        feed.fail = false
        vm.load()
        assertNull(vm.state.value.error)
        assertEquals(2, vm.state.value.items.size)
    }

    @Test
    fun loadMoreDokladaStroneIPomijaWpisyJuzWidoczne() {
        val feed = FakeFeed(items(23))
        val vm = FeedViewModel(feed)
        // nowy wpis na początku przesuwa strony: ostatni wpis pierwszej strony wraca na początku drugiej
        feed.items = listOf(feedItem(999)) + items(23)
        vm.loadMore()
        assertEquals((1L..23L).toList(), vm.state.value.items.map { it.id })
        assertFalse(vm.state.value.hasMore)
        assertEquals(listOf(0, 1), feed.requestedPages)
    }

    @Test
    fun polubienieZmieniaWpisOdRazuIWolaSerwer() {
        val feed = FakeFeed(listOf(feedItem(1, likes = 2)))
        val vm = FeedViewModel(feed)
        vm.toggleLike(1)
        val item = vm.state.value.items.single()
        assertTrue(item.likedByMe)
        assertEquals(3, item.likeCount)
        assertEquals(listOf(1L), feed.likeCalls)
    }

    @Test
    fun cofnieciePolubieniaWolaUnlike() {
        val feed = FakeFeed(listOf(feedItem(1, likes = 2, liked = true)))
        val vm = FeedViewModel(feed)
        vm.toggleLike(1)
        val item = vm.state.value.items.single()
        assertFalse(item.likedByMe)
        assertEquals(1, item.likeCount)
        assertEquals(listOf(1L), feed.unlikeCalls)
    }

    @Test
    fun bladSieciCofaPolubienieIPokazujeKomunikat() {
        val feed = FakeFeed(listOf(feedItem(1, likes = 2)))
        val vm = FeedViewModel(feed)
        feed.fail = true
        vm.toggleLike(1)
        val item = vm.state.value.items.single()
        assertFalse(item.likedByMe)
        assertEquals(2, item.likeCount)
        assertNotNull(vm.state.value.message)
        vm.dismissMessage()
        assertNull(vm.state.value.message)
    }

    @Test
    fun licznikKomentarzyAktualizujeSieZEkranuKomentarzy() {
        val vm = FeedViewModel(FakeFeed(listOf(feedItem(1))))
        vm.onCommentCount(1, 4)
        assertEquals(4, vm.state.value.items.single().commentCount)
    }
}
