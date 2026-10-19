package com.trailify.feature.auth

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val authModule = module {
    viewModel { LoginViewModel(get(), get()) }
    viewModel { RegisterViewModel(get()) }
    viewModel { ConsentViewModel(get(), get(), get()) }
}
