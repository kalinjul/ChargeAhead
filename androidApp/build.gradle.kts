import java.util.Properties

/**
 * The OpenChargeMap key does not belong in the repository (ARCHITECTURE.md
 * section 6). It is read from `local.properties` — if missing, it stays
 * empty and the app falls back to demo data instead of breaking the build.
 *
 * A key in a distributed app is fundamentally extractable; a dedicated proxy
 * is the right approach for production.
 *
 * `trim()` is not decoration: a trailing space in `local.properties`
 * otherwise ends up URL-encoded in the request, and OCM responds with
 * "Invalid API key" — with no hint that a single space is to blame.
 */
val openChargeMapApiKey: String = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}.getProperty("openChargeMapApiKey").orEmpty().trim()

/** Same mechanism, same reasoning: `googleMapsApiKey` in `local.properties`. Empty = placeholder map. */
val googleMapsApiKey: String = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}.getProperty("googleMapsApiKey").orEmpty().trim()

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "de.autoapp.android"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "de.autoapp.android"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 2
        versionName = "0.2.0-M1"

        buildConfigField(
            "String",
            "OPEN_CHARGE_MAP_API_KEY",
            "\"${openChargeMapApiKey.replace("\\", "\\\\").replace("\"", "\\\"")}\"",
        )

        // The Maps SDK reads its key from the manifest, not from code.
        manifestPlaceholders["googleMapsApiKey"] = googleMapsApiKey
        buildConfigField("boolean", "HAS_GOOGLE_MAPS_KEY", (googleMapsApiKey.isNotEmpty()).toString())
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
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
    implementation(project(":shared"))

    // Android Auto
    implementation(libs.car.app)
    implementation(libs.car.app.projected)

    // Phone UI
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.play.services.location)

    // Map — the phone shows a labeled placeholder when the key is missing
    implementation(libs.maps.compose)
    implementation(libs.play.services.maps)
}
