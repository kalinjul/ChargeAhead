package org.julakali.chargeahead.android.car

import androidx.car.app.model.CarColor
import androidx.car.app.model.CarLocation
import androidx.car.app.model.CarText
import androidx.car.app.model.Distance
import androidx.car.app.model.DistanceSpan
import androidx.car.app.model.ForegroundCarColorSpan
import androidx.car.app.model.Metadata
import androidx.car.app.model.Place
import androidx.car.app.model.PlaceMarker
import android.text.SpannableString
import android.text.Spanned
import org.julakali.chargeahead.shared.domain.LatLon

/** A numbered brand-coloured marker at [position], as the row metadata the place map reads. */
internal fun placeMetadata(position: LatLon, label: String): Metadata =
    Metadata.Builder()
        .setPlace(
            Place.Builder(CarLocation.create(position.lat, position.lon))
                .setMarker(PlaceMarker.Builder().setLabel(label).setColor(CarColor.PRIMARY).build())
                .build(),
        )
        .build()

/**
 * A row line that starts with the distance, which the host formats in the
 * car's own unit (metres below a kilometre), optionally followed by
 * " · [suffix]", coloured by [suffixColor].
 */
internal fun distanceLine(distanceKm: Double, suffix: String? = null, suffixColor: CarColor? = null): CarText {
    val distance = if (distanceKm < 1.0) {
        Distance.create(distanceKm * 1000, Distance.UNIT_METERS)
    } else {
        Distance.create(distanceKm, Distance.UNIT_KILOMETERS_P1)
    }
    // The span replaces the placeholder character; the host decides the digits.
    val text = SpannableString(if (suffix == null) " " else " $SEPARATOR$suffix")
    text.setSpan(DistanceSpan.create(distance), 0, 1, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
    if (suffix != null && suffixColor != null) {
        text.setSpan(ForegroundCarColorSpan.create(suffixColor), text.length - suffix.length, text.length, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
    }
    return CarText.create(text)
}

private const val SEPARATOR = "· "
