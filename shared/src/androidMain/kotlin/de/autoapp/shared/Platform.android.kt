package de.autoapp.shared

actual fun platformName(): String = "Android ${android.os.Build.VERSION.SDK_INT}"
