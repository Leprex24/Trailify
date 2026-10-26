package com.trailify.app

import android.app.Application
import com.trailify.core.data.dataModule
import com.trailify.feature.auth.authModule
import com.trailify.feature.community.communityModule
import com.trailify.feature.discover.discoverModule
import com.trailify.feature.map.mapModule
import com.trailify.feature.profile.profileModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class TrailifyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@TrailifyApp)
            // Nowy moduł Koin z funkcji dopisujemy tutaj.
            modules(
                dataModule,
                authModule,
                communityModule,
                discoverModule,
                mapModule,
                profileModule,
            )
        }
    }
}
