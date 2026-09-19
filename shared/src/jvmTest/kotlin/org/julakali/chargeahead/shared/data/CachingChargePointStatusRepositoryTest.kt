package org.julakali.chargeahead.shared.data

import org.julakali.chargeahead.shared.domain.ChargePointState
import org.julakali.chargeahead.shared.domain.ChargePointStatus
import org.julakali.chargeahead.shared.domain.ChargePointStatusSource
import org.julakali.chargeahead.shared.domain.TimeProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CachingChargePointStatusRepositoryTest {

    private var now = 0L
    private val asked = mutableListOf<List<String>>()
    private var failing = false
    private var state = ChargePointState.AVAILABLE

    private val source = ChargePointStatusSource { ids ->
        asked += ids
        if (failing) error("backend down")
        ids.filter { it != "unknown" }.associateWith { listOf(ChargePointStatus(state)) }
    }

    private fun repository(maxEntries: Int = CachingChargePointStatusRepository.MAX_ENTRIES) =
        CachingChargePointStatusRepository(source, TimeProvider { now }, maxEntries)

    @Test
    fun `fetched statuses are served, unknown ids as empty`() = runBlocking {
        val repository = repository()

        repository.refresh(listOf("a", "unknown"))

        assertEquals(
            mapOf("a" to listOf(ChargePointStatus(ChargePointState.AVAILABLE)), "unknown" to emptyList()),
            repository.statuses.first(),
        )
    }

    @Test
    fun `only missing or stale ids are fetched`() = runBlocking {
        val repository = repository()
        repository.refresh(listOf("a"))

        now = CachingChargePointStatusRepository.STALE_AFTER_MILLIS
        repository.refresh(listOf("a", "b"))
        now = CachingChargePointStatusRepository.STALE_AFTER_MILLIS + 1
        repository.refresh(listOf("a", "b"))

        assertEquals(listOf(listOf("a"), listOf("b"), listOf("a")), asked)
    }

    @Test
    fun `the oldest fetch is dropped once the cache is full`() = runBlocking {
        val repository = repository(maxEntries = 2)

        repository.refresh(listOf("a"))
        repository.refresh(listOf("b"))
        now = CachingChargePointStatusRepository.STALE_AFTER_MILLIS + 1
        repository.refresh(listOf("a"))
        repository.refresh(listOf("c"))

        assertEquals(setOf("a", "c"), repository.statuses.first().keys)
    }

    @Test
    fun `a failed fetch keeps the stale statuses`() = runBlocking {
        val repository = repository()
        repository.refresh(listOf("a"))

        now = CachingChargePointStatusRepository.STALE_AFTER_MILLIS + 1
        failing = true
        state = ChargePointState.OCCUPIED
        assertFailsWith<IllegalStateException> { repository.refresh(listOf("a")) }

        assertEquals(listOf(ChargePointStatus(ChargePointState.AVAILABLE)), repository.statuses.first().getValue("a"))
    }
}
