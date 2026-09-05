package de.autoapp.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OperatorKeyTest {

    @Test
    fun sameOperatorWithAndWithoutLegalForm_yieldsSameKey() {
        // Observed: the register writes "IONITY GmbH", OpenChargeMap writes "Ionity".
        assertEquals(OperatorKey.of("Ionity"), OperatorKey.of("IONITY GmbH"))
    }

    @Test
    fun countrySuffixInParentheses_isDropped() {
        assertEquals(OperatorKey.of("EnBW"), OperatorKey.of("EnBW (D)"))
        assertEquals(
            OperatorKey.of("Shell Recharge Solutions"),
            OperatorKey.of("Shell Recharge Solutions (DE)"),
        )
    }

    @Test
    fun caseIsIgnored() {
        assertEquals(OperatorKey.of("ionity"), OperatorKey.of("IONITY"))
    }

    @Test
    fun legalFormChains_areStripped() {
        assertEquals(
            OperatorKey.of("Josef Geyer e-mobil"),
            OperatorKey.of("Josef Geyer e-mobil GmbH & Co. KG"),
        )
    }

    @Test
    fun differentOperators_stayDifferent() {
        val ionity = OperatorKey.of("IONITY GmbH")
        val enbw = OperatorKey.of("EnBW (D)")
        val mer = OperatorKey.of("Mer Germany GmbH")

        assertEquals(3, setOf(ionity, enbw, mer).size)
    }

    @Test
    fun similarButDistinctCompanies_staySeparate() {
        // "E.ON Drive" and "E.ON Drive Infrastructure" could be different
        // companies, and the sources don't clarify that. Better two entries
        // than one wrongly merged.
        val drive = OperatorKey.of("E.ON Drive GmbH")
        val infra = OperatorKey.of("E.ON Drive Infrastructure GmbH")

        assertEquals(2, setOf(drive, infra).size)
    }

    @Test
    fun placeholdersAreNotAnOperator() {
        assertNull(OperatorKey.of("(Unknown Operator)"))
        assertNull(OperatorKey.of("(Business Owner at Location)"))
    }

    @Test
    fun emptyOrUnknown_yieldsNull() {
        assertNull(OperatorKey.of(null))
        assertNull(OperatorKey.of("   "))
        assertNull(OperatorKey.of("GmbH"))
    }

    @Test
    fun displayName_picksTheShortest() {
        assertEquals("Ionity", OperatorKey.displayName(listOf("IONITY GmbH", "Ionity")))
    }

    @Test
    fun foldedName_ignoresCase() {
        assertEquals(OperatorKey.folded("EnBW"), OperatorKey.folded("enbw"))
    }

    @Test
    fun foldedName_sortsUmlautsByBaseLetter() {
        val names = listOf("Zunder", "Ökostrom", "Allego")

        assertEquals(
            listOf("Allego", "Ökostrom", "Zunder"),
            names.sortedBy { OperatorKey.folded(it) },
        )
    }

    @Test
    fun foldedName_makesUmlautsSearchable() {
        // Someone typing "okostrom" means "Ökostrom" — on a car keyboard the
        // umlaut is two taps away.
        assertTrue(OperatorKey.folded("Ökostrom Süd").contains(OperatorKey.folded("okostrom sud")))
        assertTrue(OperatorKey.folded("Straßenstrom").contains("strassen"))
    }
}
