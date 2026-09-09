package de.autoapp.android.car

import androidx.car.app.Screen
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.core.graphics.drawable.IconCompat

internal fun Screen.icon(resId: Int, tint: CarColor? = null): CarIcon =
    CarIcon.Builder(IconCompat.createWithResource(carContext, resId))
        .apply { if (tint != null) setTint(tint) }
        .build()
