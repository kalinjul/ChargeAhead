package de.autoapp.shared.data

import de.autoapp.shared.domain.ChargeSite
import de.autoapp.shared.domain.Connector
import de.autoapp.shared.domain.ConnectorType
import de.autoapp.shared.domain.LatLon
import de.autoapp.shared.domain.PriceKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DemoTariffSourceTest {

    private val source = DemoTariffSource()

    private fun site(operator: String?) = ChargeSite(
        id = "demo:x",
        name = "Testpark",
        operator = operator,
        position = LatLon(50.0, 8.0),
        connectors = listOf(Connector(ConnectorType.CCS2, 300.0, 4)),
    )

    @Test
    fun `every quote is labeled an estimate and contains ad-hoc`() {
        val quote = source.quote(site("Ionity"), activeTariffIds = emptySet())
        assertTrue(quote.isEstimate)
        assertEquals(listOf(PriceKind.AD_HOC), quote.prices.map { it.kind })
    }

    @Test
    fun `home subscription beats ad-hoc at its own network`() {
        val quote = source.quote(site("IONITY GmbH"), activeTariffIds = setOf("ionity-passport"))
        val best = quote.best!!
        assertEquals(PriceKind.SUBSCRIPTION, best.kind)
        assertEquals(0.49, best.euroPerKwh)
    }

    @Test
    fun `roaming tariff prices foreign networks, home-only tariff yields no row there`() {
        // Fastned Gold has no roaming; EnBW M does.
        val quote = source.quote(site("Ionity"), activeTariffIds = setOf("fastned-gold", "enbw-m"))
        assertTrue(quote.prices.none { it.label == "Fastned Gold" })
        assertEquals(0.60, quote.prices.first { it.label == "EnBW mobility+ M" }.euroPerKwh)
    }

    @Test
    fun `subsidiary spellings still match the operator`() {
        // OperatorKey keeps "shell recharge solutions" distinct; the marker
        // matching must recognize it anyway.
        val quote = source.quote(site("Shell Recharge Solutions (DE)"), activeTariffIds = setOf("shell-plus"))
        assertEquals(0.54, quote.best?.euroPerKwh)
    }

    @Test
    fun `unknown operator falls back to the default ad-hoc price`() {
        val quote = source.quote(site("Ladeverein Hintertupfingen"), activeTariffIds = emptySet())
        assertEquals(0.64, quote.best?.euroPerKwh)
    }
}
