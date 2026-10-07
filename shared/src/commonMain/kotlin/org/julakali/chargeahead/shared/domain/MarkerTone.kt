package org.julakali.chargeahead.shared.domain

/** How a map marker reads at a glance; each platform paints a tone in its own colours. */
enum class MarkerTone {
    GOOD, WARN, BAD, MUTED;

    companion object {
        /** The bolts carry the speed, unless nothing there charges at all. */
        fun bolts(speed: ChargeSpeed, availability: SiteAvailability?): MarkerTone =
            if (availability is SiteAvailability.OutOfOrder) MUTED else speed.tone

        /** The far-zoom dot: live availability when there is some, otherwise the speed. */
        fun dot(speed: ChargeSpeed, availability: SiteAvailability?): MarkerTone = when (availability) {
            is SiteAvailability.Live -> availability.level.tone
            SiteAvailability.OutOfOrder -> MUTED
            null -> speed.tone
        }
    }
}

val ChargeSpeed.tone: MarkerTone
    get() = when (this) {
        ChargeSpeed.SLOW -> MarkerTone.BAD
        ChargeSpeed.MEDIUM -> MarkerTone.WARN
        ChargeSpeed.FAST, ChargeSpeed.ULTRA, ChargeSpeed.HYPER -> MarkerTone.GOOD
    }

val AvailabilityLevel.tone: MarkerTone
    get() = when (this) {
        AvailabilityLevel.GOOD -> MarkerTone.GOOD
        AvailabilityLevel.LOW -> MarkerTone.WARN
        AvailabilityLevel.NONE -> MarkerTone.BAD
    }
