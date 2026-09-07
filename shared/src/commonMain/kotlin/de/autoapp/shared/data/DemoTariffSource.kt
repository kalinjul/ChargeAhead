package de.autoapp.shared.data

import de.autoapp.shared.domain.ChargeSite
import de.autoapp.shared.domain.OperatorKey
import de.autoapp.shared.domain.PriceKind
import de.autoapp.shared.domain.PriceQuote
import de.autoapp.shared.domain.Tariff
import de.autoapp.shared.domain.TariffCatalog
import de.autoapp.shared.domain.TariffPrice
import de.autoapp.shared.domain.TariffSource

/**
 * Prices from a built-in table — the stand-in until a real price API is
 * connected (ROADMAP: Chargeprice or Eco-Movement, decision pending).
 *
 * Same philosophy as [DemoSiteSource]: better labeled estimates than an empty
 * screen, and better an empty row than an invented number. Every quote from
 * here carries `isEstimate = true`, and the UI is obliged to show it.
 *
 * Operators are recognized by marker containment, not by exact key:
 * [OperatorKey] deliberately doesn't unify subsidiaries, so the same network
 * arrives as "enbw mobility" or "shell recharge solutions" depending on the
 * source. A contained marker ("enbw", "shell") is robust against that;
 * a wrong merge here only mislabels an estimated price, not a real one.
 *
 * Prices are stable per operator, not jittered per site: a made-up variance
 * would suggest a precision the table doesn't have.
 */
class DemoTariffSource : TariffSource {

    override fun quote(site: ChargeSite, activeTariffIds: Set<String>): PriceQuote {
        val marker = markerFor(OperatorKey.of(site.operator))
        val adHoc = TariffPrice(
            label = AD_HOC_LABEL,
            euroPerKwh = AD_HOC_PRICES[marker] ?: DEFAULT_AD_HOC_PRICE,
            kind = PriceKind.AD_HOC,
        )

        val viaTariffs = activeTariffIds
            .mapNotNull(TariffCatalog::byId)
            .mapNotNull { tariff -> priceVia(tariff, marker) }

        return PriceQuote(
            prices = (viaTariffs + adHoc).sortedBy { it.euroPerKwh },
            isEstimate = true,
        )
    }

    private fun priceVia(tariff: Tariff, marker: String?): TariffPrice? {
        val atHome = tariff.homeOperatorKey != null && tariff.homeOperatorKey == marker
        return when {
            atHome && tariff.homePriceEuroPerKwh != null ->
                TariffPrice(tariff.displayName, tariff.homePriceEuroPerKwh, PriceKind.SUBSCRIPTION)

            tariff.roamingPriceEuroPerKwh != null ->
                TariffPrice(tariff.displayName, tariff.roamingPriceEuroPerKwh, PriceKind.ROAMING)

            // Tariff doesn't work at this operator (Fastned Gold at Ionity):
            // no row, rather than a fantasy roaming price.
            else -> null
        }
    }

    private fun markerFor(operatorKey: String?): String? =
        operatorKey?.let { key -> AD_HOC_PRICES.keys.firstOrNull { key.contains(it) } }

    private companion object {
        const val AD_HOC_LABEL = "Ad-hoc"
        const val DEFAULT_AD_HOC_PRICE = 0.64

        /** Keyed by marker; matched by containment in the normalized operator key. */
        val AD_HOC_PRICES: Map<String, Double> = mapOf(
            "ionity" to 0.79,
            "fastned" to 0.69,
            "enbw" to 0.65,
            "tesla" to 0.55,
            "allego" to 0.69,
            "shell" to 0.64,
            "aral" to 0.69,
            "total" to 0.62,
            "vattenfall" to 0.59,
            "ewe" to 0.64,
        )
    }
}
