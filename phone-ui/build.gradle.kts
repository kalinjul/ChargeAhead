import java.util.Properties

/** Same key lookup as androidApp: `local.properties` first, then the environment. */
val localProperties: Properties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}
val googleMapsApiKey: String =
    (localProperties.getProperty("googleMapsApiKey") ?: System.getenv("GOOGLE_MAPS_API_KEY")).orEmpty().trim()

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
    // NavKeys are @Serializable — rememberNavBackStack saves them across process death.
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "org.julakali.chargeahead.android.phone"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        // Without a key the map slot shows a labeled placeholder instead of the map.
        buildConfigField("boolean", "HAS_GOOGLE_MAPS_KEY", googleMapsApiKey.isNotEmpty().toString())
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    }
}

dependencies {
    api(project(":shared"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    api(libs.koin.android)
    api(libs.koin.androidx.compose)
    api(libs.androidx.navigation3.runtime)
    api(libs.androidx.navigation3.ui)
    api(libs.androidx.lifecycle.viewmodel.navigation3)
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.compose.cache)
    implementation(libs.aboutlibraries.compose.m3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.play.services.location)

    // Map — the phone shows a labeled placeholder when the key is missing
    implementation(libs.maps.compose)
    implementation(libs.play.services.maps)
}
