plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release signing comes ONLY from environment variables / CI secrets. Never commit release keys.
val rs = listOf("MV_RELEASE_STORE_FILE", "MV_RELEASE_STORE_PASSWORD", "MV_RELEASE_KEY_ALIAS", "MV_RELEASE_KEY_PASSWORD").map { System.getenv(it) }
val hasReleaseSigning = rs.all { !it.isNullOrBlank() }

android {
    namespace = "com.victorious.match"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.victorious.match"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
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
    buildFeatures { compose = true }
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
    testImplementation(libs.junit)
    testImplementation(libs.org.json)
}
