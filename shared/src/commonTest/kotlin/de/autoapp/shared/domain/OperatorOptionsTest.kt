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

    @Test
    fun `puts selected networks first without a query`() {
        val options = OperatorOptions.fromNames(listOf("Aral pulse", "Ionity", "Fastned", "Tesla"))
        val selected = setOf("ionity", "tesla")
        val shown = OperatorOptions.forPicker(options, query = "", selected = selected)

        assertEquals(listOf("Ionity", "Tesla", "Aral pulse", "Fastned"), shown.map { it.displayName })
    }

    @Test
    fun `keeps the alphabetical order while searching`() {
        val options = OperatorOptions.fromNames(listOf("EnBW", "E-Ionity", "Ionity"))
        val shown = OperatorOptions.forPicker(options, query = " ioni ", selected = setOf("ionity"))

        assertEquals(listOf("E-Ionity", "Ionity"), shown.map { it.displayName })
    }

    @Test
    fun `search ignores case and umlauts`() {
        val options = OperatorOptions.fromNames(listOf("Ökostrom Plus", "Fastned"))
        val shown = OperatorOptions.forPicker(options, query = "OKOSTROM", selected = emptySet())

        assertEquals(listOf("Ökostrom Plus"), shown.map { it.displayName })
    }
}
