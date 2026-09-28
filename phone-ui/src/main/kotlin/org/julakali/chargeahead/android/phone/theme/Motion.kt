package org.julakali.chargeahead.android.phone.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween

/**
 * The chrome's animation specs, one per purpose, so no screen picks its own
 * number. Durations are Material 3's motion tokens (short4 = 200 ms,
 * medium1 = 250 ms); swap for `MaterialTheme.motionScheme` once it is public.
 */
object ChargeAheadMotion {
    private const val SHORT_MILLIS = 200
    private const val MEDIUM_MILLIS = 250

    /** Something moves or changes size: a bar growing, a button folding away, a list sliding. */
    fun <T> spatial(): FiniteAnimationSpec<T> = tween(SHORT_MILLIS)

    /** Something appears or disappears in place: fades. */
    fun <T> effects(): FiniteAnimationSpec<T> = tween(SHORT_MILLIS)

    /** A larger surface settling, like the trip sheet's peek. */
    fun <T> surface(): FiniteAnimationSpec<T> = tween(MEDIUM_MILLIS)
}
