package com.trailify.feature.profile

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val profileModule = module {
    viewModel { ProfileViewModel(get(), get(), get(), get()) }
    viewModel { ActivityDetailsViewModel(get()) }
    viewModel { AccountViewModel(get(), get(), get()) }
}
