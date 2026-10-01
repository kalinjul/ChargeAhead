package org.julakali.chargeahead.shared

import kotlinx.serialization.json.Json

/**
 * How everything on disk is written: settings, the trip, Room's JSON columns.
 * Unknown keys are skipped and an enum name this build doesn't know becomes
 * the property's default, so a newer or older build costs a value, not a launch.
 */
internal val persistenceJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
}
