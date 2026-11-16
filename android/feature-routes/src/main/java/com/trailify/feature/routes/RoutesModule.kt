package com.trailify.feature.routes

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val routesModule = module {
    viewModel { RoutesViewModel(get(), get(), get()) }
}
