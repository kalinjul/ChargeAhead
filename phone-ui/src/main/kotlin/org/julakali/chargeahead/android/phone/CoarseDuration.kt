package org.julakali.chargeahead.android.phone

import org.jetbrains.compose.resources.PluralStringResource
import org.julakali.chargeahead.shared.resources.Res
import org.julakali.chargeahead.shared.resources.phone_duration_days
import org.julakali.chargeahead.shared.resources.phone_duration_hours
import org.julakali.chargeahead.shared.resources.phone_duration_minutes

/** An age in minutes as its coarsest whole unit: the plural resource and the count to put in it. */
fun coarseDuration(minutes: Long): Pair<PluralStringResource, Int> = when {
    minutes < 60 -> Res.plurals.phone_duration_minutes to minutes.toInt()
    minutes < 60 * 24 -> Res.plurals.phone_duration_hours to (minutes / 60).toInt()
    else -> Res.plurals.phone_duration_days to (minutes / (60 * 24)).toInt()
}
