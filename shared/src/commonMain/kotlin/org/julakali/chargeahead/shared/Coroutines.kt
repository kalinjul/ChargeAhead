package org.julakali.chargeahead.shared

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.plus
import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.named

/** The Koin qualifier of the process-wide [CoroutineScope], cancelled when the graph closes. */
val AppScope: Qualifier = named("AppScope")

/** A scope that can be cancelled on its own and is cancelled along with this one. */
fun CoroutineScope.childScope(): CoroutineScope = this + SupervisorJob(coroutineContext[Job])
