package com.trailify.core.data

import com.trailify.core.data.mock.MockActivityRepository
import com.trailify.core.data.mock.MockAdminRepository
import com.trailify.core.data.mock.MockAuthRepository
import com.trailify.core.data.mock.MockPoiRepository
import com.trailify.core.data.mock.MockProfileRepository
import com.trailify.core.data.mock.MockReviewRepository
import com.trailify.core.data.mock.MockRouteRepository
import com.trailify.core.data.overpass.OverpassMapLayerRepository
import com.trailify.core.domain.repository.ActivityRepository
import com.trailify.core.domain.repository.AdminRepository
import com.trailify.core.domain.repository.AuthRepository
import com.trailify.core.domain.repository.MapLayerRepository
import com.trailify.core.domain.repository.PoiRepository
import com.trailify.core.domain.repository.ProfileRepository
import com.trailify.core.domain.repository.ReviewRepository
import com.trailify.core.domain.repository.RouteRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Wiąże interfejsy repozytoriów z implementacjami.
 * Teraz wszędzie atrapy, poza warstwami mapy, które są prawdziwe (OpenStreetMap przez Overpass).
 */
val dataModule = module {
    single<AuthRepository> { MockAuthRepository() }
    single<ProfileRepository> { MockProfileRepository() }
    single<RouteRepository> { MockRouteRepository() }
    single<ActivityRepository> { MockActivityRepository() }
    single<ReviewRepository> { MockReviewRepository() }
    single<PoiRepository> { MockPoiRepository() }
    single<AdminRepository> { MockAdminRepository() }
    single<MapLayerRepository> { OverpassMapLayerRepository(androidContext().cacheDir) }
}
