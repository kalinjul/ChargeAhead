package org.julakali.chargeahead.shared

/**
 * Wall-clock time in milliseconds since the Unix epoch.
 */
// TODO use kotlin.time instead
expect fun currentTimeMillis(): Long
