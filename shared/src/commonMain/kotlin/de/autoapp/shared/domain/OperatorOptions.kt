package de.autoapp.shared.domain

/**
 * Groups the raw operator names of known sites into countable options —
 * the operators actually present in the data, as opposed to the shipped
 * [NetworkCatalog] the picker selects from.
 *
 * Alphabetical, because the reader is looking for a name they already know —
 * "where's Ionity?" is answered by order, not frequency; the count still
 * shows on each line.
 */
object OperatorOptions {

    fun fromNames(names: List<String?>): List<OperatorOption> =
        fromCounts(names.filterNotNull().map { it to 1 })

    fun fromCounts(counts: List<Pair<String, Int>>): List<OperatorOption> =
        counts.mapNotNull { (name, count) -> OperatorKey.of(name)?.let { Triple(it, name, count) } }
            .groupBy { it.first }
            .mapNotNull { (key, rows) ->
                OperatorKey.displayName(rows.map { it.second })?.let { display ->
                    OperatorOption(key, display, rows.sumOf { it.third })
                }
            }
            .sortedBy { OperatorKey.folded(it.displayName) }
}
