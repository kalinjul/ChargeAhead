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
import androidx.compose.ui.graphics.toArgb
import org.julakali.chargeahead.android.phone.operatorColor
import org.julakali.chargeahead.shared.domain.ChargeSite
import org.julakali.chargeahead.shared.domain.LatLon

/** A numbered marker at [position] in [color], as the row metadata the place map reads. */
internal fun placeMetadata(position: LatLon, label: String, color: CarColor = CarColor.PRIMARY): Metadata =
    Metadata.Builder()
        .setPlace(
            Place.Builder(CarLocation.create(position.lat, position.lon))
                .setMarker(PlaceMarker.Builder().setLabel(label).setColor(color).build())
                .build(),
        )
        .build()

/** The badge colour the phone's trip map gives this site, so both maps tell the same story. */
internal fun operatorCarColor(site: ChargeSite): CarColor {
    val argb = operatorColor(site).toArgb()
    return CarColor.createCustom(argb, argb)
}

/**
 * A row line around the distance, which the host formats in the car's own
 * unit (metres below a kilometre): "[leading] · <distance> · [suffix] ·
 * [trailing]", the suffix coloured by [suffixColor].
 */
internal fun distanceLine(
    distanceKm: Double,
    suffix: String? = null,
    suffixColor: CarColor? = null,
    trailing: String? = null,
    leading: String? = null,
): CarText {
    val distance = if (distanceKm < 1.0) {
        Distance.create(distanceKm * 1000, Distance.UNIT_METERS)
    } else {
        Distance.create(distanceKm, Distance.UNIT_KILOMETERS_P1)
    }
    // The span replaces the placeholder character; the host decides the digits.
    val head = leading?.let { it + SEPARATOR }.orEmpty()
    val text = SpannableString(head + listOfNotNull(" ", suffix, trailing).joinToString(SEPARATOR))
    text.setSpan(DistanceSpan.create(distance), head.length, head.length + 1, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
    if (suffix != null && suffixColor != null) {
        val start = head.length + 1 + SEPARATOR.length
        text.setSpan(ForegroundCarColorSpan.create(suffixColor), start, start + suffix.length, Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
    }
    return CarText.create(text)
}

private const val SEPARATOR = " · "
