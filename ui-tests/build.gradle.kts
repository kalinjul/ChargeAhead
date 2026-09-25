plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.screenshot)
    jacoco
}

/**
 * Every phone UI test lives here, on top of :phone-ui: behaviour tests run on
 * Robolectric from `test`, screenshot tests from `screenshotTest` via the
 * Compose Preview Screenshot Testing plugin.
 */
// TODO once AGP 9.5 is stable: drop libs.plugins.screenshot and declare the
//  suite with testOptions.screenshotTests.create("screenshotTest") { engineVersion = ... };
//  the source set, the tests and the goldens stay as they are.
//  https://developer.android.com/studio/preview/compose-screenshot-testing-with-testsuites
android {
    namespace = "org.julakali.chargeahead.uitests"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    buildFeatures {
        compose = true
    }

    experimentalProperties["android.experimental.enableScreenshotTest"] = true

    // 0.1 %: a single recoloured 24dp badge in a sheet-sized image is ~0.3 %,
    // and CI renders with the same JDK as Studio's JBR, so glyph noise stays
    // below this.
    screenshotTests {
        imageDifferenceThreshold = 0.001f
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    }
}

// Robolectric pokes at JDK internals that newer JDKs seal by default.
tasks.withType<Test>().configureEach {
    jvmArgs(
        "--add-opens", "java.base/jdk.internal.access=ALL-UNNAMED",
        "--add-opens", "java.base/java.lang=ALL-UNNAMED",
        "--add-opens", "java.base/java.io=ALL-UNNAMED",
    )
}

dependencies {
    implementation(project(":phone-ui"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui.tooling.preview)

    screenshotTestImplementation(libs.screenshot.validation.api)
    screenshotTestImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.androidx.activity.compose)
    // The PhoneApp flow test stubs CameraUpdateFactory; the map SDK itself never starts under Robolectric.
    testImplementation(libs.play.services.maps)
}
