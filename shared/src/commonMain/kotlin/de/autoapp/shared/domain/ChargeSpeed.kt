package de.autoapp.shared.domain

/**
 * How fast a charging site is, as the map marker shows it: a class that
 * carries a color, and a number of bolts that grows with the power.
 *
 * The classification lives here and not in the marker composable because both
 * phone UIs draw the same badge, and a threshold that drifts apart between
 * Android and iOS would be a silent lie about the same site.
 *
 * The lower bound is deliberately open: the design names 22 kW as the AC
 * ceiling for [SLOW], but nothing sits between that and the 50 kW step, and a
 * 43 kW site is not worth a class of its own. Everything below [MEDIUM_KW] is
 * slow.
 */
enum class ChargeSpeed(val bolts: Int) {
    SLOW(1),
    MEDIUM(1),
    FAST(1),
    ULTRA(2),
    HYPER(3),
    ;

    companion object {
        const val MEDIUM_KW = 50.0
        const val FAST_KW = 100.0
        const val ULTRA_KW = 150.0
        const val HYPER_KW = 300.0

        fun of(maxPowerKw: Double): ChargeSpeed = when {
            maxPowerKw >= HYPER_KW -> HYPER
            maxPowerKw >= ULTRA_KW -> ULTRA
            maxPowerKw >= FAST_KW -> FAST
            maxPowerKw >= MEDIUM_KW -> MEDIUM
            else -> SLOW
        }
    }
}
