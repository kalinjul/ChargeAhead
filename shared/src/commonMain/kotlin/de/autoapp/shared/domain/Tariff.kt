package de.autoapp.shared.domain

data class Tariff(
    val id: String,
    val displayName: String,
    val monthlyFeeEuro: Double?,
    /** `null` for pure roaming tariffs (Maingau-style): no home network, same price everywhere. */
    val homeOperatorKey: String?,
    val homePriceEuroPerKwh: Double?,
    val roamingPriceEuroPerKwh: Double?,
)

enum class PriceKind { AD_HOC, SUBSCRIPTION, ROAMING }

data class TariffPrice(
    val label: String,
    val euroPerKwh: Double,
    val kind: PriceKind,
)

data class PriceQuote(
    val prices: List<TariffPrice>,
    /** Always true today — no live price source yet, only the demo table. */
    val isEstimate: Boolean,
) {
    val best: TariffPrice? get() = prices.firstOrNull()
}

interface TariffSource {
    /** Never throws — pricing must not break planning. */
    fun quote(site: ChargeSite, activeTariffIds: Set<String>): PriceQuote
}

object TariffCatalog {

    val all: List<Tariff> = listOf(
        Tariff("fastned-gold", "Fastned Gold", 11.99, "fastned", 0.45, null),
        Tariff("ionity-passport", "Ionity Passport Power", 11.99, "ionity", 0.49, null),
        Tariff("enbw-m", "EnBW mobility+ M", 7.99, "enbw", 0.51, 0.60),
        Tariff("enbw-l", "EnBW mobility+ L", 17.99, "enbw", 0.39, 0.50),
        Tariff("shell-plus", "Shell Recharge Plus", 4.99, "shell", 0.54, null),
        Tariff("aral-flex", "Aral pulse flex", null, "aral", 0.57, null),
        Tariff("tesla-membership", "Tesla Supercharger-Mitgliedschaft", 9.99, "tesla", 0.44, null),
        Tariff("vattenfall-go", "Vattenfall InCharge Go", 5.99, "vattenfall", 0.52, null),
        Tariff("maingau", "Maingau EinfachStromLaden", null, null, null, 0.59),
        Tariff("elli-highway", "Elli Drive Highway", 14.99, null, null, 0.55),
    )

    fun byId(id: String): Tariff? = all.firstOrNull { it.id == id }
}
