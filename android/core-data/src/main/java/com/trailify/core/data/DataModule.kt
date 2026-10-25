package com.trailify.core.data

import com.trailify.core.data.mock.MockAccountAuthRepository
import com.trailify.core.data.api.ApiActivityRepository
import com.trailify.core.data.api.ApiClient
import com.trailify.core.data.api.ApiProfileRepository
import com.trailify.core.data.api.ApiRoutePlanner
import com.trailify.core.data.api.ApiRouteRepository
import com.trailify.core.data.mock.MockPhotoUploader
import com.trailify.core.data.mock.MockRoutePlanner
import com.trailify.core.data.supabase.SupabasePhotoUploader
import com.trailify.core.domain.repository.PhotoUploader
import com.trailify.core.domain.repository.RouteFollowController
import com.trailify.core.domain.repository.RoutePlanner
import com.trailify.core.data.mock.MockActivityRepository
import com.trailify.core.data.mock.MockAdminRepository
import com.trailify.core.data.mock.MockAuthRepository
import com.trailify.core.data.mock.MockPoiRepository
import com.trailify.core.data.mock.MockProfileRepository
import com.trailify.core.data.mock.MockReviewRepository
import com.trailify.core.data.mock.MockRouteRepository
import com.trailify.core.data.overpass.OverpassMapLayerRepository
import com.trailify.core.data.recording.FusedLocationSource
import com.trailify.core.data.recording.RecordingStore
import com.trailify.core.data.supabase.SupabaseAuthRepository
import com.trailify.core.domain.repository.AccountAuthRepository
import com.trailify.core.domain.repository.ActivityRepository
import com.trailify.core.domain.repository.AdminRepository
import com.trailify.core.domain.repository.AuthRepository
import com.trailify.core.domain.repository.MapLayerRepository
import com.trailify.core.domain.repository.PoiRepository
import com.trailify.core.domain.repository.ProfileRepository
import com.trailify.core.domain.repository.RecordingController
import com.trailify.core.domain.repository.ReviewRepository
import com.trailify.core.domain.repository.RouteRepository
import com.trailify.core.domain.repository.UserLocationSource
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** true, gdy w android/secrets.properties są klucze Supabase. Inaczej logowanie działa na atrapie. */
val isSupabaseConfigured: Boolean =
    BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_PUBLISHABLE_KEY.isNotBlank()

/**
 * Wiąże interfejsy repozytoriów z implementacjami.
 * Teraz wszędzie atrapy, poza warstwami mapy, które są prawdziwe (OpenStreetMap przez Overpass),
 * oraz logowaniem i profilem (/me), które są prawdziwe (Supabase Auth + nasz backend), gdy w secrets.properties są klucze.
 */
val dataModule = module {
    if (isSupabaseConfigured) {
        single {
            createSupabaseClient(
                supabaseUrl = BuildConfig.SUPABASE_URL,
                supabaseKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
            ) {
                install(Auth)
            }
        }
        single {
            SupabaseAuthRepository(
                supabase = get(),
                context = androidContext(),
                scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
                googleWebClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID,
            )
        }
        single<AuthRepository> { get<SupabaseAuthRepository>() }
        single<AccountAuthRepository> { get<SupabaseAuthRepository>() }
        single { ApiClient(supabase = get(), baseUrl = BuildConfig.API_BASE_URL) }
        single<ProfileRepository> { ApiProfileRepository(get()) }
        single<ActivityRepository> { ApiActivityRepository(get()) }
        single<RoutePlanner> { ApiRoutePlanner(get()) }
        single<RouteRepository> { ApiRouteRepository(get()) }
        single<PhotoUploader> {
            SupabasePhotoUploader(
                context = androidContext(),
                supabase = get(),
                supabaseUrl = BuildConfig.SUPABASE_URL,
                publishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
            )
        }
    } else {
        single<AuthRepository> { MockAuthRepository() }
        single<AccountAuthRepository> { MockAccountAuthRepository() }
        single<ProfileRepository> { MockProfileRepository() }
        single<ActivityRepository> { MockActivityRepository() }
        single<RoutePlanner> { MockRoutePlanner() }
        single<RouteRepository> { MockRouteRepository() }
        single<PhotoUploader> { MockPhotoUploader() }
    }
    single<ReviewRepository> { MockReviewRepository() }
    single<PoiRepository> { MockPoiRepository() }
    single<AdminRepository> { MockAdminRepository() }
    single { FusedLocationSource(androidContext()) }
    single<UserLocationSource> { get<FusedLocationSource>() }
    single { RecordingStore(androidContext()) }
    single<RecordingController> { get<RecordingStore>() }
    single<RouteFollowController> { InMemoryRouteFollowController() }
    single<MapLayerRepository> { OverpassMapLayerRepository(androidContext().cacheDir) }
}
