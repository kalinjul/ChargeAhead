package de.autoapp.shared

// The JVM target only exists so the shared logic can be tested without an
// Android device. It is never shipped.
actual fun platformName(): String = "JVM ${System.getProperty("java.version")}"
