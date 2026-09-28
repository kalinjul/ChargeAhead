package org.julakali.chargeahead.android.phone.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween

/**
 * The chrome's animation specs, one per purpose, so no screen picks its own
 * number. Durations are Material 3's motion tokens (short4 = 200 ms,
 * medium1 = 250 ms, medium4 = 300 ms, fade-through 90 out / 210 in); swap for `MaterialTheme.motionScheme` once it is public.
 */
object ChargeAheadMotion {
    private const val SHORT_MILLIS = 200
    private const val MEDIUM_MILLIS = 250
    private const val LONG_MILLIS = 300
    private const val FADE_THROUGH_OUT_MILLIS = 90
    private const val FADE_THROUGH_IN_MILLIS = 210

    /** Something moves or changes size: a bar growing, a button folding away, a list sliding. */
    fun <T> spatial(): FiniteAnimationSpec<T> = tween(SHORT_MILLIS)

    /** Something appears or disappears in place: fades. */
    fun <T> effects(): FiniteAnimationSpec<T> = tween(SHORT_MILLIS)

    /** Fade-through, outgoing half: the old content is gone before the new one starts. */
    fun <T> fadeThroughOut(): FiniteAnimationSpec<T> = tween(FADE_THROUGH_OUT_MILLIS)

    /** Fade-through, incoming half: waits for the outgoing half, then takes its time. */
    fun <T> fadeThroughIn(): FiniteAnimationSpec<T> = tween(FADE_THROUGH_IN_MILLIS, delayMillis = FADE_THROUGH_OUT_MILLIS)

    /** A whole page sliding in or out: Material's medium4. */
    fun <T> page(): FiniteAnimationSpec<T> = tween(LONG_MILLIS)

    /** A larger surface settling, like the trip sheet's peek. */
    fun <T> surface(): FiniteAnimationSpec<T> = tween(MEDIUM_MILLIS)
}
