package org.julakali.chargeahead.android.phone

/** An age in minutes as its coarsest whole unit: the plural resource and the count to put in it. */
fun coarseDuration(minutes: Long): Pair<Int, Int> = when {
    minutes < 60 -> R.plurals.phone_duration_minutes to minutes.toInt()
    minutes < 60 * 24 -> R.plurals.phone_duration_hours to (minutes / 60).toInt()
    else -> R.plurals.phone_duration_days to (minutes / (60 * 24)).toInt()
}
