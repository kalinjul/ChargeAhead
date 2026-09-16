package org.julakali.chargeahead.shared

actual fun platformName(): String = "Android ${android.os.Build.VERSION.SDK_INT}"
