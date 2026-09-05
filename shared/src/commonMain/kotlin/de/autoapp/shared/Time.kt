package de.autoapp.shared

/**
 * Wall-clock time in milliseconds since the Unix epoch.
 *
 * Custom expect/actual instead of kotlinx-datetime or kotlin.time: this
 * project's time handling is limited to durations and expiry checks, and the
 * time API names are currently shifting between both libraries. A Long stays
 * put.
 */
expect fun currentTimeMillis(): Long
