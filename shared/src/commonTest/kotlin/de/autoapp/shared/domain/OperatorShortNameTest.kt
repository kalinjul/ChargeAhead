package de.autoapp.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OperatorShortNameTest {

    @Test
    fun realWorldSpellingsAreShortened() {
        // All observed in OpenChargeMap and the register, see OperatorKey.
        assertEquals("Ionity", OperatorShortName.of("IONITY GmbH"))
        assertEquals("EnBW", OperatorShortName.of("EnBW (D)"))
        assertEquals("Shell", OperatorShortName.of("Shell Recharge Solutions (DE)"))
        assertEquals("E.ON", OperatorShortName.of("E.ON Drive Infrastructure GmbH"))
        assertEquals("Aral", OperatorShortName.of("Aral pulse"))
        assertEquals("EWE Go", OperatorShortName.of("EWE Go GmbH"))
        assertEquals("Tesla", OperatorShortName.of("Tesla Motors"))
    }

    @Test
    fun unknownNetworksStayUnlabeled() {
        // A guessed abbreviation would be worse than the bolts alone.
        assertNull(OperatorShortName.of("Stadtwerke Musterstadt"))
        assertNull(OperatorShortName.of(null))
        assertNull(OperatorShortName.of(""))
    }

    @Test
    fun aPrefixMustBeAWholeWord() {
        assertNull(OperatorShortName.of("Shellhaus Parkhaus"))
        assertNull(OperatorShortName.of("Teslafan Ladepark"))
    }
}
