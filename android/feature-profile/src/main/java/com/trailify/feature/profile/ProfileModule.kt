package com.trailify.feature.profile

import com.trailify.core.domain.repository.AuthRepository
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val profileModule = module {
    viewModel { ProfileViewModel(get(), get(), get()) }
    viewModel { EditProfileViewModel(get(), get()) }
    viewModel { (userId: String) -> OtherProfileViewModel(userId, get()) }
    viewModel { (userId: String, kind: UserListKind) ->
        UserListViewModel(userId, kind, get(), get<AuthRepository>().currentUser.value?.id)
    }
    viewModel { ActivityDetailsViewModel(get()) }
    viewModel { AccountViewModel(get(), get(), get(), get()) }
}
