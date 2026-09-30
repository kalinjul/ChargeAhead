package org.julakali.chargeahead.shared

import org.julakali.chargeahead.shared.domain.AppCoroutineDispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** The production dispatchers, except Main, which a plain test JVM lacks. */
val testDispatchers = AppCoroutineDispatchers(
    io = Dispatchers.IO,
    computation = Dispatchers.Default,
    main = Dispatchers.Unconfined,
)

val testAppScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
