package com.trailify.feature.community

import com.trailify.feature.community.publish.PublishRouteViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val communityModule = module {
    // activityId i activityType przychodzą z ekranu (parametersOf), repozytoria z dataModule
    viewModel { params -> PublishRouteViewModel(params.get(), params.get(), get(), get()) }
}
