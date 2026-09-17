package org.julakali.chargeahead.shared

// The JVM target is for tests only.
actual fun platformName(): String = "JVM ${System.getProperty("java.version")}"
