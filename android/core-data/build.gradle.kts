import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
}

// Klucze z android/secrets.properties (plik nie trafia do repo).
// Brak SUPABASE_URL lub SUPABASE_PUBLISHABLE_KEY = logowanie działa na atrapie.
val secrets = Properties().apply {
    val file = rootProject.file("secrets.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun secret(name: String): String = secrets.getProperty(name, "").trim()

android {
    namespace = "com.trailify.core.data"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 26

        buildConfigField("String", "SUPABASE_URL", "\"${secret("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"${secret("SUPABASE_PUBLISHABLE_KEY")}\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${secret("GOOGLE_WEB_CLIENT_ID")}\"")
        // Adres naszego backendu. Domyślnie localhost (działa przez `adb reverse tcp:8080 tcp:8080`).
        val apiBaseUrl = secret("API_BASE_URL").ifBlank { "http://localhost:8080/api/v1" }
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core-domain"))
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    // GPS dla nagrywania aktywności
    implementation(libs.play.services.location)

    // Supabase Auth (logowanie e-mail i Google)
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.kotlinx.serialization.json)
    // czyszczenie wybranego konta Google przy wylogowaniu
    implementation(libs.androidx.credentials)
}
