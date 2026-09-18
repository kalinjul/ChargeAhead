package org.julakali.chargeahead.shared.domain

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class SubjectInteractorTest {

    /** Emits its param after [delayMillis], then once more. */
    private class Echo(private val delayMillis: Long) : SubjectInteractor<Int, String>() {
        val timings = mutableListOf<Pair<Int, Duration>>()

        override fun createObservable(params: Int): Flow<String> = flow {
            delay(delayMillis)
            emit("$params")
            emit("$params again")
        }

        override fun onFirstResult(params: Int, took: Duration) {
            timings += params to took
        }
    }

    @Test
    fun `the time until the first result is reported once per param`() = runBlocking<Unit> {
        val echo = Echo(delayMillis = 50)
        echo(1)

        withTimeout(5_000) { echo.flow.take(2).toList() }

        assertEquals(listOf(1), echo.timings.map { it.first })
        assertTrue(echo.timings.single().second >= 50.milliseconds)
    }
}
