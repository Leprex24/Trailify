package com.trailify.feature.discover

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val discoverModule = module {
    viewModel { DiscoverViewModel(get()) }
    viewModel { RouteDetailsViewModel(get()) }
}
