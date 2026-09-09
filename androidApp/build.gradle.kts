import java.util.Properties

/**
 * `local.properties` is the developer machine's source for keys and signing
 * data; it is not in the repository (ARCHITECTURE.md section 6). CI has no
 * such file — there the same values arrive as environment variables from
 * GitHub secrets (see docs/ci-cd.md).
 */
val localProperties: Properties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}

/**
 * local.properties wins over the environment: a developer machine keeps
 * working exactly as before, without exported variables, even when a stale
 * value happens to sit in the shell.
 *
 * `trim()` is not decoration: a trailing space in `local.properties`
 * otherwise ends up URL-encoded in the request, and OCM responds with
 * "Invalid API key" — with no hint that a single space is to blame.
 */
fun secret(propertyName: String, environmentName: String): String =
    (localProperties.getProperty(propertyName) ?: System.getenv(environmentName)).orEmpty().trim()

/**
 * The OpenChargeMap key does not belong in the repository. If it is missing,
 * it stays empty and the app falls back to demo data instead of breaking the
 * build — which is what CI builds of pull requests from forks do, since those
 * never see secrets.
 *
 * A key in a distributed app is fundamentally extractable; a dedicated proxy
 * is the right approach for production.
 */
val openChargeMapApiKey: String = secret("openChargeMapApiKey", "OPEN_CHARGE_MAP_API_KEY")

/** Same mechanism, same reasoning. Empty = placeholder map. */
val googleMapsApiKey: String = secret("googleMapsApiKey", "GOOGLE_MAPS_API_KEY")

/**
 * Play refuses any upload whose versionCode is not strictly higher than
 * everything already in the account — across all tracks. The release
 * pipeline therefore determines the number (fastlane, from the Play API) and
 * passes it in; the checked-in value is only what a local build gets.
 */
val appVersionCode: Int = System.getenv("VERSION_CODE")?.trim()?.takeIf { it.isNotEmpty() }?.toInt() ?: 3
val appVersionName: String = System.getenv("VERSION_NAME")?.trim()?.takeIf { it.isNotEmpty() } ?: "0.2.0-M1"

/**
 * Signing data comes from the environment in CI (keystore decoded to a file
 * beforehand) or from `local.properties` locally. If no keystore is
 * configured, no signingConfig is created at all and `assembleRelease` yields
 * an unsigned artifact — that is deliberate: a release build has to stay
 * possible without the production keystore.
 */
val releaseKeystorePath: String = secret("releaseKeystoreFile", "KEYSTORE_FILE")
val releaseKeystore: File? = releaseKeystorePath.takeIf { it.isNotEmpty() }?.let(::file)?.takeIf { it.exists() }

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    // NavKeys are @Serializable — rememberNavBackStack saves them across process death.
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "de.autoapp.android"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "org.julakali.chargeahead"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = appVersionCode
        versionName = appVersionName

        buildConfigField(
            "String",
            "OPEN_CHARGE_MAP_API_KEY",
            "\"${openChargeMapApiKey.replace("\\", "\\\\").replace("\"", "\\\"")}\"",
        )

        // The Maps SDK reads its key from the manifest, not from code.
        manifestPlaceholders["googleMapsApiKey"] = googleMapsApiKey
        buildConfigField("boolean", "HAS_GOOGLE_MAPS_KEY", (googleMapsApiKey.isNotEmpty()).toString())
    }

    signingConfigs {
        // Created only when a keystore is actually there. Declaring it
        // unconditionally would make every release build fail on a machine
        // without the keystore, instead of just producing an unsigned build.
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = secret("releaseKeystorePassword", "KEYSTORE_PASSWORD")
                keyAlias = secret("releaseKeyAlias", "KEY_ALIAS")
                keyPassword = secret("releaseKeyPassword", "KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // null without a keystore -- the artifact is then unsigned and
            // Play would reject it, which is the honest outcome.
            signingConfig = signingConfigs.findByName("release")
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
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.play.services.location)

    // Map — the phone shows a labeled placeholder when the key is missing
    implementation(libs.maps.compose)
    implementation(libs.play.services.maps)
}
