package org.julakali.chargeahead.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class InteractorTest {

    private class Waiting : Interactor<Unit, Unit>() {
        val release = CompletableDeferred<Unit>()
        override suspend fun doWork(params: Unit) = release.await()
    }

    @Test
    fun inProgress_turnsOnRightAwayAndOffWhenDone() = runTest {
        val interactor = Waiting()
        val job = launch { interactor(Unit) }
        runCurrent()

        assertTrue(interactor.inProgress.first())

        interactor.release.complete(Unit)
        job.join()
        assertEquals(false, interactor.inProgress.first())
    }

    @Test
    fun inProgress_turnsOffWhenTheCallerIsCancelled() = runTest {
        val interactor = Waiting()
        val job = launch { interactor(Unit) }
        runCurrent()

        job.cancel()
        job.join()

        assertEquals(false, interactor.inProgress.first())
    }

    @Test
    fun timeout_isAFailureAndEndsProgress() = runTest {
        val interactor = Waiting()

        val result = interactor(Unit, timeout = 1.seconds)

        assertIs<InteractorTimeoutException>(result.exceptionOrNull())
        assertEquals(false, interactor.inProgress.first())
    }
}
