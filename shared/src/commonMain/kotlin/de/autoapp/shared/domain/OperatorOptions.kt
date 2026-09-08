package de.autoapp.shared.domain

/**
 * Normalizes raw operator names into the network picker's options.
 *
 * Alphabetical, because the user is looking for a name they already know —
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

    /**
     * The picker's view of [options]: those matching [query], and — only
     * without a query — the driver's own networks first.
     *
     * The reordering is deliberately tied to the empty query. While
     * searching, the hit the user is aiming at must not jump to the top
     * under their finger; and once a search has narrowed the list to a
     * handful of rows, the order barely matters anyway.
     */
    fun forPicker(
        options: List<OperatorOption>,
        query: String,
        selected: Set<String>,
    ): List<OperatorOption> {
        val needle = OperatorKey.folded(query.trim())
        if (needle.isNotEmpty()) {
            return options.filter { OperatorKey.folded(it.displayName).contains(needle) }
        }
        val (preferred, rest) = options.partition { it.key in selected }
        return preferred + rest
    }
}
