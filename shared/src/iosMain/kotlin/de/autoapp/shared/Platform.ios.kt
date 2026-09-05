package de.autoapp.shared

import platform.UIKit.UIDevice

// Only configured/compiled on macOS (see shared/build.gradle.kts) —
// unverified on this Linux machine, see final report.
actual fun platformName(): String {
    val device = UIDevice.currentDevice
    return "${device.systemName} ${device.systemVersion}"
}
