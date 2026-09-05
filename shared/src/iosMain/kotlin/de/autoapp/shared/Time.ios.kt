package de.autoapp.shared

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

// Only compiled on macOS — unverified on this Linux machine.
actual fun currentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1000.0).toLong()
