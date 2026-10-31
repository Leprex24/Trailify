package com.trailify.feature.community.publish

import com.trailify.core.domain.model.ActivityType
import com.trailify.core.domain.model.AppException
import com.trailify.core.domain.model.Page
import com.trailify.core.domain.model.PhotoRef
import com.trailify.core.domain.model.Route
import com.trailify.core.domain.model.RouteDraft
import com.trailify.core.domain.model.RouteQuery
import com.trailify.core.domain.model.RouteSummary
import com.trailify.core.domain.model.RouteUpdate
import com.trailify.core.domain.model.UserSummary
import com.trailify.core.domain.repository.AuthErrorCodes
import com.trailify.core.domain.repository.PhotoUploader
import com.trailify.core.domain.repository.RouteRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class PublishRouteViewModelTest {

    private val routes = FakeRouteRepository()
    private val uploader = FakeUploader()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = PublishRouteViewModel(
        activityId = 42L,
        initialType = ActivityType.CYCLING,
        routes = routes,
        uploader = uploader,
    )

    @Test
    fun typAktywnosciPodpowiadanyZNagrania() {
        assertEquals(ActivityType.CYCLING, viewModel().ui.value.activityType)
    }

    @Test
    fun bezNazwyNieWysylaTrasy() {
        val vm = viewModel()
        vm.publish()
        assertEquals("Podaj nazwę trasy", vm.ui.value.nameError)
        assertNull(routes.created)
    }

    @Test
    fun publikacjaWysylaDaneZFormularza() {
        uploader.autoComplete = true
        val vm = viewModel()
        vm.onNameChange("  Wokół Zalewu  ")
        vm.onDescriptionChange("   ")
        vm.onScenicChange(true)
        vm.onPublicChange(false)
        vm.onPhotosPicked(listOf("content://a", "content://b"))
        vm.publish()

        val draft = routes.created!!
        assertEquals("Wokół Zalewu", draft.name)
        assertNull(draft.description) // pusty opis wysyłamy jako null
        assertEquals(42L, draft.activityId)
        assertEquals(ActivityType.CYCLING, draft.activityType)
        assertTrue(draft.isScenic)
        assertEquals(false, draft.isPublic)
        assertEquals(listOf("key/content://a", "key/content://b"), draft.photos.map { it.storageKey })
        assertEquals(7L, vm.ui.value.publishedRouteId)
    }

    @Test
    fun niePublikujePodczasWysylaniaZdjec() {
        val vm = viewModel()
        vm.onNameChange("Trasa")
        vm.onPhotosPicked(listOf("content://a"))
        assertTrue(vm.ui.value.isUploading)

        vm.publish()
        assertEquals("Poczekaj, aż zdjęcia się wyślą", vm.ui.value.generalError)
        assertNull(routes.created)

        uploader.pending.getValue("content://a").complete(PhotoRef("k", "u"))
        vm.publish()
        assertNotNull(routes.created)
    }

    @Test
    fun nieudaneZdjecieMoznaPonowic() {
        val vm = viewModel()
        vm.onPhotosPicked(listOf("content://a"))
        uploader.pending.getValue("content://a").completeExceptionally(AppException(AuthErrorCodes.NETWORK, "x"))
        assertEquals("Brak połączenia z internetem", vm.ui.value.photos.single().error)

        uploader.autoComplete = true
        vm.retryPhoto("content://a")
        assertNotNull(vm.ui.value.photos.single().uploaded)
    }

    @Test
    fun maksymalnieDziesiecZdjec() {
        uploader.autoComplete = true
        val vm = viewModel()
        vm.onPhotosPicked((1..12).map { "content://$it" })
        assertEquals(ROUTE_PHOTOS_MAX, vm.ui.value.photos.size)
        assertEquals("Można dodać maksymalnie 10 zdjęć", vm.ui.value.info)
    }

    @Test
    fun bladSerweraPokazujeKomunikat() {
        routes.failWith = AppException(AuthErrorCodes.NETWORK, "offline")
        val vm = viewModel()
        vm.onNameChange("Trasa")
        vm.publish()
        assertEquals("Brak połączenia z internetem", vm.ui.value.generalError)
        assertNull(vm.ui.value.publishedRouteId)
    }

    private class FakeUploader : PhotoUploader {
        var autoComplete = false
        val pending = mutableMapOf<String, CompletableDeferred<PhotoRef>>()

        override suspend fun upload(imageUri: String): PhotoRef {
            if (autoComplete) return PhotoRef("key/$imageUri", "https://example.com/$imageUri")
            return CompletableDeferred<PhotoRef>().also { pending[imageUri] = it }.await()
        }
    }

    private class FakeRouteRepository : RouteRepository {
        var created: RouteDraft? = null
        var failWith: Exception? = null

        override suspend fun create(draft: RouteDraft): Route {
            failWith?.let { throw it }
            created = draft
            return Route(
                summary = RouteSummary(
                    id = 7L, name = draft.name, activityType = draft.activityType,
                    isTourist = draft.isTourist, isScenic = draft.isScenic, isPublic = draft.isPublic,
                    totalDistance = null, durationSeconds = null, averageRating = null, reviewCount = 0,
                    recommendationScore = null, startPoint = null, thumbnailUrl = null, distanceFromQuery = null,
                    owner = UserSummary("me", "Ja"), createdAt = Instant.EPOCH,
                ),
                description = draft.description,
                geometry = emptyList(),
                photos = emptyList(),
            )
        }

        override suspend fun search(query: RouteQuery): Page<RouteSummary> = error("nieużywane")
        override suspend fun get(id: Long): Route = error("nieużywane")
        override suspend fun update(id: Long, update: RouteUpdate): Route = error("nieużywane")
        override suspend fun delete(id: Long) = error("nieużywane")
        override suspend fun getMine(page: Int, size: Int): Page<RouteSummary> = error("nieużywane")
    }
}
