// Copyright 2018, Google LLC, Christopher Banes and the Tivi project contributors
// SPDX-License-Identifier: Apache-2.0
// Adapted from https://github.com/chrisbanes/tivi (domain/Interactor.kt).

package org.julakali.chargeahead.shared.domain

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource
import org.julakali.chargeahead.shared.logDebug
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeout

abstract class Interactor<in P, R> {
    private val running = MutableStateFlow(0)

    /** True from the moment a call starts until the last running one ends — no debounce. */
    val inProgress: Flow<Boolean> = running
        .map { it > 0 }
        .distinctUntilChanged()

    /**
     * A timeout comes back as a failure. Cancelling the caller still throws,
     * and either way the call stops counting as in progress.
     */
    suspend operator fun invoke(
        params: P,
        timeout: Duration = DefaultTimeout,
    ): Result<R> {
        running.update { it + 1 }
        try {
            return cancellableRunCatching {
                try {
                    withTimeout(timeout) { doWork(params) }
                } catch (timedOut: TimeoutCancellationException) {
                    throw InteractorTimeoutException(timeout, timedOut)
                }
            }
        } finally {
            running.update { it - 1 }
        }
    }

    protected abstract suspend fun doWork(params: P): R

    companion object {
        internal val DefaultTimeout = 5.minutes
    }
}

/** [Interactor.invoke] ran longer than its timeout. */
class InteractorTimeoutException(timeout: Duration, cause: Throwable) :
    Exception("Timed out after $timeout", cause)

suspend operator fun <R> Interactor<Unit, R>.invoke(
    timeout: Duration = Interactor.DefaultTimeout,
) = invoke(Unit, timeout)

@OptIn(ExperimentalCoroutinesApi::class)
abstract class SubjectInteractor<P : Any, T> {
    // Ideally this would be buffer = 0, since we use flatMapLatest below, BUT invoke is not
    // suspending. This means that we can't suspend while flatMapLatest cancels any
    // existing flows. The buffer of 1 means that we can use tryEmit() and buffer the value
    // instead, resulting in mostly the same result.
    private val paramState = MutableSharedFlow<P>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val flow: Flow<T> = paramState
        .distinctUntilChanged()
        .flatMapLatest { params -> createObservable(params).timedUntilFirst(params) }
        .distinctUntilChanged()

    operator fun invoke(params: P) {
        paramState.tryEmit(params)
    }

    protected abstract fun createObservable(params: P): Flow<T>

    /** How long [params] took from being picked up to their first result. Superseded params report nothing. */
    protected open fun onFirstResult(params: P, took: Duration) {
        logDebug("${this::class.simpleName}: first result after ${took.inWholeMilliseconds} ms for $params")
    }

    private fun Flow<T>.timedUntilFirst(params: P): Flow<T> = flow {
        val start = TimeSource.Monotonic.markNow()
        var reported = false
        collect { value ->
            if (!reported) {
                reported = true
                onFirstResult(params, start.elapsedNow())
            }
            emit(value)
        }
    }
}

/** [runCatching] that lets cancellation through. */
inline fun <R> cancellableRunCatching(block: () -> R): Result<R> = try {
    Result.success(block())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (failure: Throwable) {
    Result.failure(failure)
}
