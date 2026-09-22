plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.screenshot) apply false
    alias(libs.plugins.android.kmp.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.aboutlibraries.android) apply false
}

/** Every test that runs without a device, in one go. */
tasks.register("allTests") {
    group = "verification"
    description = "Shared unit tests, phone UI behaviour tests and screenshot validation."
    dependsOn(":shared:jvmTest", ":ui-tests:testDebugUnitTest", ":ui-tests:validateDebugScreenshotTest")
}
