package de.autoapp.shared.domain

/**
 * A charging tariff the driver can pay with.
 *
 * The same plug costs differently depending on who bills it: ad-hoc, via the
 * operator's own subscription, or roaming through a third-party tariff. The
 * driver activates the tariffs they actually hold; price comparison happens
 * per site against exactly those.
 *
 * [homeOperatorKey] is `null` for pure roaming tariffs (Maingau-style): they
 * have no home network and price every operator the same.
 */
data class Tariff(
    val id: String,
    val displayName: String,
    /** `null` when the tariff has no base fee. */
    val monthlyFeeEuro: Double?,
    /** Operator marker as matched by the tariff source ("enbw", "shell") — see DemoTariffSource. */
    val homeOperatorKey: String?,
    /** Price at the home operator, in €/kWh. */
    val homePriceEuroPerKwh: Double?,
    /** Price at any other operator, in €/kWh. `null` = tariff unusable there. */
    val roamingPriceEuroPerKwh: Double?,
)

/** How a price came about — the UI labels the rows with this. */
enum class PriceKind { AD_HOC, SUBSCRIPTION, ROAMING }

/** One priced way to charge at a specific site. */
data class TariffPrice(
    val label: String,
    val euroPerKwh: Double,
    val kind: PriceKind,
)

/**
 * All ways to pay at one site, cheapest first.
 *
 * [isEstimate] is currently always true: there is no live price source yet,
 * only the built-in demo table. The UI must say so — a made-up price
 * presented as real would be worse than none.
 */
data class PriceQuote(
    val prices: List<TariffPrice>,
    val isEstimate: Boolean,
) {
    val best: TariffPrice? get() = prices.firstOrNull()
}

/**
 * Prices per site. Port like [ChargeSiteSource]: the demo table implements it
 * today, a real price API (Chargeprice, Eco-Movement) replaces it later
 * without the callers noticing.
 */
interface TariffSource {
    /** Quote for this site given the driver's active tariffs. Never throws — pricing must not break planning. */
    fun quote(site: ChargeSite, activeTariffIds: Set<String>): PriceQuote
}

/**
 * The tariffs the app knows, for the subscriptions screen.
 *
 * A static list with the same trade-off as the vehicle catalog: every price
 * change out there makes an entry stale (ROADMAP open item 4 discusses the
 * maintenance cost). Acceptable here because everything is labeled as an
 * estimate until a live price source exists.
 */
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
