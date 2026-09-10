package de.autoapp.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class OperatorOptionsTest {

    @Test
    fun `groups spelling variants under one key`() {
        val options = OperatorOptions.fromNames(listOf("IONITY GmbH", "Ionity", "Fastned", null))
        assertEquals(listOf("Fastned", "Ionity"), options.map { it.displayName })
        assertEquals(2, options.single { it.displayName == "Ionity" }.siteCount)
    }

    @Test
    fun `sums provided counts`() {
        val options = OperatorOptions.fromCounts(listOf("IONITY GmbH" to 3, "Ionity" to 2))
        assertEquals(5, options.single().siteCount)
    }
}
