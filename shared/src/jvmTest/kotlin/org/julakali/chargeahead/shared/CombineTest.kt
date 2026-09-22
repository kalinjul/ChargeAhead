package org.julakali.chargeahead.shared

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class CombineTest {

    @Test
    fun `six flows keep their own types`() = runBlocking<Unit> {
        val combined = combine(
            flowOf(1),
            flowOf("two"),
            flowOf(3.0),
            flowOf(true),
            flowOf('5'),
            flowOf<String?>(null),
        ) { a, b, c, d, e, f -> listOf(a, b, c, d, e, f) }
        assertEquals(listOf(1, "two", 3.0, true, '5', null), combined.first())
    }

    @Test
    fun `twenty flows reach the lambda in argument order`() = runBlocking<Unit> {
        val combined = combine(
            flowOf(1),
            flowOf(2),
            flowOf(3),
            flowOf(4),
            flowOf(5),
            flowOf(6),
            flowOf(7),
            flowOf(8),
            flowOf(9),
            flowOf(10),
            flowOf(11),
            flowOf(12),
            flowOf(13),
            flowOf(14),
            flowOf(15),
            flowOf(16),
            flowOf(17),
            flowOf(18),
            flowOf(19),
            flowOf(20),
        ) { v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18, v19, v20 -> listOf(v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, v16, v17, v18, v19, v20) }
        assertEquals((1..20).toList(), combined.first())
    }
}
