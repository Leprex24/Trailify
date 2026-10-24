package com.trailify.core.data

import com.trailify.core.data.mock.MockAccountAuthRepository
import com.trailify.core.data.mock.MockActivityRepository
import com.trailify.core.data.mock.MockAdminRepository
import com.trailify.core.data.mock.MockAuthRepository
import com.trailify.core.data.mock.MockPoiRepository
import com.trailify.core.data.mock.MockProfileRepository
import com.trailify.core.data.mock.MockReviewRepository
import com.trailify.core.data.mock.MockRouteRepository
import com.trailify.core.data.overpass.OverpassMapLayerRepository
import com.trailify.core.data.supabase.SupabaseAuthRepository
import com.trailify.core.domain.repository.AccountAuthRepository
import com.trailify.core.domain.repository.ActivityRepository
import com.trailify.core.domain.repository.AdminRepository
import com.trailify.core.domain.repository.AuthRepository
import com.trailify.core.domain.repository.MapLayerRepository
import com.trailify.core.domain.repository.PoiRepository
import com.trailify.core.domain.repository.ProfileRepository
import com.trailify.core.domain.repository.ReviewRepository
import com.trailify.core.domain.repository.RouteRepository
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
 * i logowaniem, które jest prawdziwe (Supabase Auth), gdy w secrets.properties są klucze.
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
    } else {
        single<AuthRepository> { MockAuthRepository() }
        single<AccountAuthRepository> { MockAccountAuthRepository() }
    }
    single<ProfileRepository> { MockProfileRepository() }
    single<RouteRepository> { MockRouteRepository() }
    single<ActivityRepository> { MockActivityRepository() }
    single<ReviewRepository> { MockReviewRepository() }
    single<PoiRepository> { MockPoiRepository() }
    single<AdminRepository> { MockAdminRepository() }
    single<MapLayerRepository> { OverpassMapLayerRepository(androidContext().cacheDir) }
}
