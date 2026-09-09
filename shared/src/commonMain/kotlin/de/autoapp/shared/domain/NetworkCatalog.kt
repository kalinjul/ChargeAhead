package de.autoapp.shared.domain

data class Network(
    val key: String,
    val name: String,
    val operatorIds: Set<Long>,
    val nameKeywords: Set<String>,
)

/**
 * The curated, shipped list of selectable charging networks — bundled so the
 * picker is populated on first launch, offline. Mirrors [VehicleCatalog]: a
 * maintained in-source list, updated by app release.
 *
 * Not the same as [de.autoapp.shared.data.OperatorCatalog], which reads
 * operator names already in the local cache.
 */
object NetworkCatalog {

    /** Coverage/selection sentinel: no filter, fetch everything (as today). */
    const val UNFILTERED = "*"

    val all: List<Network> = listOf(
        Network("shell-recharge", "Shell Recharge",
            setOf(47L, 156L, 157L, 3392L, 3508L, 3709L),
            setOf("shell", "recharge")),
        Network("enel-x", "Enel X",
            setOf(80L),
            setOf("enel", "enelx")),
        Network("tesla", "Tesla",
            setOf(23L, 3534L),
            setOf("tesla", "supercharger")),
        Network("evbox", "EVBox",
            setOf(73L),
            setOf("evbox")),
        Network("evnet-nl", "EVnetNL",
            setOf(178L),
            setOf("evnet", "evnetnl")),
        Network("bp-pulse", "BP Pulse",
            setOf(32L, 2247L),
            setOf("bp", "pulse", "bpulse", "iberdrola")),
        Network("allego", "Allego",
            setOf(103L),
            setOf("allego")),
        Network("power-dot", "Power Dot",
            setOf(3538L, 3550L),
            setOf("powerdot", "power dot")),
        Network("izivia", "Izivia (Sodetrel)",
            setOf(213L),
            setOf("izivia", "sodetrel")),
        Network("be-charge", "Be Charge",
            setOf(3327L),
            setOf("becharge", "be charge")),
        Network("surecharge", "SureCharge (FM Conway)",
            setOf(3612L),
            setOf("surecharge", "conway")),
        Network("repsol-ibil", "Repsol-Ibil",
            setOf(91L),
            setOf("repsol", "ibil")),
        Network("char-gy", "Char.gy",
            setOf(3345L),
            setOf("chargy", "char.gy")),
        Network("totalenergies", "TotalEnergies",
            setOf(25L, 3447L, 3571L),
            setOf("total", "totalenergies")),
        Network("endesa", "Endesa",
            setOf(207L),
            setOf("endesa")),
        Network("mobive", "MObiVE",
            setOf(3407L),
            setOf("mobive")),
        Network("ladenetz", "ladenetz.de",
            setOf(69L),
            setOf("ladenetz")),
        Network("enbw", "EnBW",
            setOf(86L),
            setOf("enbw")),
        Network("pod-point", "POD Point",
            setOf(3L),
            setOf("podpoint", "pod point")),
        Network("electra", "Electra",
            setOf(3489L),
            setOf("electra")),
        Network("innogy-rwe", "Innogy SE (RWE eMobility)",
            setOf(105L),
            setOf("innogy", "rwe")),
        Network("zunder", "Zunder",
            setOf(3324L),
            setOf("zunder")),
        Network("ionity", "Ionity",
            setOf(3299L),
            setOf("ionity")),
        Network("eon-drive", "E.ON Drive",
            setOf(46L, 3251L, 3359L, 3403L, 3422L, 3814L),
            setOf("eon", "e.on", "eondrive")),
        Network("osprey", "Osprey Charging",
            setOf(203L),
            setOf("osprey")),
        Network("atlante", "Atlante",
            setOf(3588L),
            setOf("atlante")),
        Network("lidl", "Lidl",
            setOf(38L),
            setOf("lidl")),
        Network("ewe", "EWE",
            setOf(127L),
            setOf("ewe")),
        Network("chargepoint", "ChargePoint",
            setOf(5L),
            setOf("chargepoint")),
        Network("freshmile", "Freshmile",
            setOf(3379L),
            setOf("freshmile")),
        Network("alperia", "Alperia",
            setOf(164L),
            setOf("alperia")),
        Network("smatrics", "SMATRICS",
            setOf(3253L),
            setOf("smatrics")),
        Network("ewiva", "Ewiva",
            setOf(3720L),
            setOf("ewiva")),
        Network("fastned", "FastNed",
            setOf(74L),
            setOf("fastned")),
        Network("mer", "MER (Vattenfall InCharge)",
            setOf(108L, 3492L),
            setOf("mer", "vattenfall", "incharge")),
        Network("fortum-recharge", "Recharge (Fortum)",
            setOf(198L, 202L),
            setOf("recharge", "fortum", "charge and drive")),
        Network("grønn-kontakt", "Grønn Kontakt",
            setOf(3247L),
            setOf("grønn", "gronn", "gronnkontakt")),
    )

    private val byKeyMap: Map<String, Network> = all.associateBy { it.key }
    private val byOperatorId: Map<Long, Network> =
        all.flatMap { n -> n.operatorIds.map { it to n } }.toMap()

    fun byKey(key: String): Network? = byKeyMap[key]

    fun selection(keys: Set<String>): List<Network> = all.filter { it.key in keys }

    /** The network a site belongs to: by OCM operator id first, else by folded keyword. */
    fun resolve(site: ChargeSite): String? {
        site.operatorId?.let { id -> byOperatorId[id]?.let { return it.key } }
        val name = site.operator ?: return null
        val folded = OperatorKey.folded(name)
        return all.firstOrNull { n -> n.nameKeywords.any { folded.contains(it) } }?.key
    }
}
