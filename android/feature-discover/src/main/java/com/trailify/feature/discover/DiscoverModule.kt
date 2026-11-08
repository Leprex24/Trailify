package com.trailify.feature.discover

import com.trailify.feature.discover.reviews.ReviewsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val discoverModule = module {
    viewModel { DiscoverViewModel(get()) }
    viewModel { RouteDetailsViewModel(get(), get()) }
    // routeId i ownerId przychodzą z ekranu (parametersOf), repozytoria z dataModule
    viewModel { params -> ReviewsViewModel(params.get(), params.get(), get(), get(), get(), get()) }
}
