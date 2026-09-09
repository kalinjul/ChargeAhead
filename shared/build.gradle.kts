import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
}

sqldelight {
    databases {
        create("ChargeSiteDatabase") {
            packageName.set("de.autoapp.shared.db")
            // verifyMigrations stays off: that check would need a schema
            // snapshot of every prior version as a .db in the source set.
            // With only one migration so far, the cost outweighs the benefit —
            // it's checked in the test instead (ChargeSiteMigrationTest).
        }
    }
}

kotlin {
    // Since AGP 9, com.android.library is incompatible with KMP; the Android
    // target is configured through the dedicated KMP library plugin inside kotlin {}.
    androidLibrary {
        namespace = "de.autoapp.shared"
        compileSdk = libs.versions.compileSdk.get().toInt()
        minSdk = libs.versions.minSdk.get().toInt()
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    // Pure JVM target so the shared logic can be tested on a machine without
    // an Android device: ./gradlew :shared:jvmTest
    jvm()

    // Apple targets are configured on Linux too, and that's not an oversight:
    // Kotlin/Native fully compiles iosMain here, checking the cinterop calls
    // against the real CoreLocation, Foundation and UIKit bindings. That's
    // the only way to verify the iOS Kotlin code at all without a Mac.
    //
    // What does NOT work on Linux is linking: linkDebugFrameworkIos* is
    // skipped automatically by the Kotlin plugin on non-macOS hosts, because
    // it needs Apple's linker. That also means no Objective-C header is
    // produced -- the Swift side stays unverified until a Mac is available.
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }
    // matching/configureEach instead of named(...): "iosMain" only exists
    // once the KMP hierarchy template applies after the target declaration.
    // A direct named("iosMain") is evaluated too early here and breaks
    // configuration with "KotlinSourceSet with name 'iosMain' not found".
    sourceSets.matching { it.name == "iosMain" }.configureEach {
        dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.sqldelight.driver.native)
        }
    }

    sourceSets {
        commonMain.dependencies {
            // api, not implementation: StateFlow is part of ChargeStopsFeature's
            // public signature, so androidApp and iosApp need the types on
            // their own compile classpath.
            api(libs.kotlinx.coroutines.core)
            // api, not implementation: the shared ViewModels are part of the
            // public surface — androidApp resolves them with viewModel<T>()
            // and needs ViewModel and its factory types on its own classpath.
            api(libs.androidx.lifecycle.viewmodel)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.sqldelight.runtime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.ktor.client.mock)
        }
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.play.services.location)
            implementation(libs.sqldelight.driver.android)
        }
        jvmTest.dependencies {
            // For the ViewModel tests only — see the version catalog.
            implementation(libs.kotlinx.coroutines.test)
        }
        jvmMain.dependencies {
            // Only for tests and development on the machine; never shipped.
            implementation(libs.ktor.client.okhttp)
            implementation(libs.sqldelight.driver.jvm)
        }
    }
}
