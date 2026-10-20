plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Release signing comes ONLY from environment variables / CI secrets. Never commit release keys.
val rs = listOf("MV_RELEASE_STORE_FILE", "MV_RELEASE_STORE_PASSWORD", "MV_RELEASE_KEY_ALIAS", "MV_RELEASE_KEY_PASSWORD").map { System.getenv(it) }
val hasReleaseSigning = rs.all { !it.isNullOrBlank() }

// Supabase endpoint + PUBLISHABLE key (public by design; access is enforced by RLS).
// Override with -PMV_SUPABASE_URL=... / env MV_SUPABASE_URL. NEVER put a service_role/secret key here.
fun cfg(name: String, default: String) = (findProperty(name) as String?) ?: System.getenv(name) ?: default
val supabaseUrl = cfg("MV_SUPABASE_URL", "https://swcotjvqumzgszzhnmks.supabase.co")
val supabaseKey = cfg("MV_SUPABASE_PUBLISHABLE_KEY", "sb_publishable_iKqJke5ky7TSBqKmmeWMKw_e4kQdEF9")
// FCM is enabled only when app/google-services.json exists (never committed; CI may write it from a secret).
val pushConfigured = file("google-services.json").exists()
if (pushConfigured) apply(plugin = "com.google.gms.google-services")
require(!supabaseKey.startsWith("sb_secret_") && !supabaseKey.contains("service_role")) { "Refusing to embed a secret Supabase key" }

android {
    namespace = "com.victorious.match"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.victorious.match"
        minSdk = 26
        targetSdk = 36
        versionCode = 6
        versionName = "0.6.0"
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_KEY", "\"$supabaseKey\"")
        buildConfigField("boolean", "PUSH_CONFIGURED", pushConfigured.toString())
    }
    signingConfigs {
        getByName("debug") {
            // Project-owned DEBUG key, password "android". Not a release key.
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (hasReleaseSigning) create("release") {
            storeFile = file(rs[0]!!); storePassword = rs[1]; keyAlias = rs[2]; keyPassword = rs[3]
        }
    }
    buildTypes {
        debug { signingConfig = signingConfigs.getByName("debug") }
        release {
            isMinifyEnabled = false
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
    sourceSets.getByName("main").assets.srcDir(rootProject.file("docs"))
    testOptions { unitTests.isReturnDefaultValues = true }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.security.crypto)
    implementation(libs.kotlinx.coroutines.android)
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.realtime)
    implementation(libs.supabase.storage)
    implementation(libs.supabase.functions)
    implementation(libs.coil.compose)
    implementation(libs.coil.network)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.ktor.client.okhttp)
    testImplementation(libs.junit)
    testImplementation(libs.org.json)
}
