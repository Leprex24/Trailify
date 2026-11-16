package com.trailify.feature.community

import com.trailify.feature.community.admin.AdminViewModel
import com.trailify.feature.community.feed.CommentsViewModel
import com.trailify.feature.community.feed.FeedViewModel
import com.trailify.feature.community.feed.UserSearchViewModel
import com.trailify.feature.community.poi.PoiFormViewModel
import com.trailify.feature.community.publish.PublishRouteViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val communityModule = module {
    // activityId i activityType przychodzą z ekranu (parametersOf), repozytoria z dataModule
    viewModel { params -> PublishRouteViewModel(params.get(), params.get(), get(), get()) }
    viewModel { FeedViewModel(get()) }
    // eventId i eventAuthorId przychodzą z ekranu komentarzy
    viewModel { params -> CommentsViewModel(params.get(), params.get(), get(), get()) }
    viewModel { UserSearchViewModel(get()) }
    // asAdmin przychodzi z ekranu: zgłoszenie użytkownika albo dodanie przez administratora
    viewModel { params -> PoiFormViewModel(params.get(), get(), get(), get(), get()) }
    viewModel { AdminViewModel(get()) }
}
