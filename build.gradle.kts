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

/** Every test that runs without a device, in one go. Always re-runs, always tells the score. */
val testTasks = listOf(":shared:jvmTest", ":ui-tests:testDebugUnitTest", ":ui-tests:validateDebugScreenshotTest")

tasks.register("testAll") {
    group = "verification"
    description = "Shared unit tests, phone UI behaviour tests and screenshot validation."
    dependsOn(testTasks)
    doLast {
        val results = listOf(
            "shared unit tests" to file("shared/build/test-results/jvmTest"),
            "UI behaviour tests" to file("ui-tests/build/test-results/testDebugUnitTest"),
            "screenshot tests" to file("ui-tests/build/test-results/validateDebugScreenshotTest"),
        )
        println()
        results.forEach { (label, dir) ->
            var tests = 0; var failures = 0
            dir.listFiles { f -> f.extension == "xml" }?.forEach { xml ->
                val head = xml.readText().substringAfter("<testsuite").substringBefore(">")
                tests += Regex("tests=\"(\\d+)\"").find(head)?.groupValues?.get(1)?.toInt() ?: 0
                failures += Regex("failures=\"(\\d+)\"").find(head)?.groupValues?.get(1)?.toInt() ?: 0
                failures += Regex("errors=\"(\\d+)\"").find(head)?.groupValues?.get(1)?.toInt() ?: 0
            }
            println("  %-20s %4d tests, %d failed".format(label, tests, failures))
        }
        println()
    }
}

// Up-to-date checking is right for builds and wrong for "run the tests": when
// asked explicitly, run them.
gradle.taskGraph.whenReady {
    if (hasTask(":testAll")) {
        testTasks.forEach { path -> tasks.findByPath(path)?.outputs?.upToDateWhen { false } }
    }
}
