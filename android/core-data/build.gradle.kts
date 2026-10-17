plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.trailify.core.data"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":core-domain"))
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
}
