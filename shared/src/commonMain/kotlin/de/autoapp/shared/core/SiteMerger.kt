package de.autoapp.shared.core

import de.autoapp.shared.domain.ChargeSite
import de.autoapp.shared.domain.Connector
import de.autoapp.shared.domain.distanceKmTo
import kotlin.math.roundToInt

/**
 * Merges the same site reported by multiple sources into one
 * (ARCHITECTURE.md section 5.5).
 *
 * Merging happens on **spatial proximity and overlapping connector
 * signature**. The second condition is the more important one: two charge
 * points can share a parking lot and still be different devices.
 *
 * This isn't only about comparing across sources. The official charging
 * register reports per *charging facility*, not per site — on the A9, 294
 * pairs out of 98 entries were within 25 m of each other, with "mblty
 * Denkendorf" appearing seven times at the same coordinate. Without merging,
 * the same charging park would show up seven times in the list.
 *
 * Field-by-field priority per ARCHITECTURE.md:
 *
 * | Field | Priority |
 * |---|---|
 * | Position, operator, connectors | Bundesnetzagentur (officially reported) |
 * | Name/display | OpenChargeMap, falling back to Bundesnetzagentur |
 */
object SiteMerger {

    /** Spatial threshold. See ARCHITECTURE.md section 5.5. */
    const val DEFAULT_MAX_DISTANCE_METERS = 25.0

    /**
     * Source priority, descending. Determines the position, operator, and
     * connectors of the merged site, as well as its id.
     */
    val SOURCE_PRIORITY = listOf("bnetza", "ocm", "demo")

    fun merge(
        sites: List<ChargeSite>,
        maxDistanceMeters: Double = DEFAULT_MAX_DISTANCE_METERS,
    ): List<ChargeSite> {
        if (sites.size < 2) return sites

        // Sorted by id so the same input always produces the same output —
        // otherwise the list would depend on the response order of two
        // independent network requests.
        val ordered = sites.sortedBy { it.id }
        val grid = SpatialGrid(maxDistanceMeters)
        val clusters = mutableListOf<MutableList<ChargeSite>>()

        ordered.forEach { site ->
            val existing = grid.nearbyClusterIndices(site).firstOrNull { index ->
                clusters[index].any { it.belongsWith(site, maxDistanceMeters) }
            }
            if (existing != null) {
                clusters[existing] += site
                grid.add(site, existing)
            } else {
                clusters += mutableListOf(site)
                grid.add(site, clusters.lastIndex)
            }
        }

        return clusters.map(::mergeCluster)
    }

    private fun ChargeSite.belongsWith(other: ChargeSite, maxDistanceMeters: Double): Boolean {
        if (position.distanceKmTo(other.position) * 1000.0 > maxDistanceMeters) return false
        return sharesConnectorSignature(other)
    }

    /**
     * Do the connector types overlap?
     *
     * If either side doesn't know its connectors, the condition counts as
     * satisfied: a missing value is no evidence of difference, and spatial
     * distance alone then carries the decision.
     */
    private fun ChargeSite.sharesConnectorSignature(other: ChargeSite): Boolean {
        if (connectors.isEmpty() || other.connectors.isEmpty()) return true
        val own = connectors.map { it.type }.toSet()
        return other.connectors.any { it.type in own }
    }

    private fun mergeCluster(cluster: List<ChargeSite>): ChargeSite {
        if (cluster.size == 1) return cluster.single()

        val leading = cluster.minWithOrNull(compareBy({ it.priority() }, { it.id }))!!
        val sameSource = cluster.filter { it.primarySource() == leading.primarySource() }

        return ChargeSite(
            id = leading.id,
            // Name from OpenChargeMap, because that's where people name what
            // they actually found; the register carries administrative labels.
            name = cluster.preferredName() ?: leading.name,
            operator = leading.operator ?: cluster.firstNotNullOfOrNull { it.operator },
            position = leading.position,
            // Sum connectors only from the leading source: OCM and the
            // register describe the same devices, so adding both would double
            // the charging park's apparent number of charge points.
            connectors = sameSource.flatMap { it.connectors }.mergeConnectors(),
            // Address follows position and operator: officially reported
            // beats manually entered.
            address = leading.address ?: cluster.firstNotNullOfOrNull { it.address },
            sources = cluster.flatMapTo(mutableSetOf()) { it.sources },
        )
    }

    private fun List<ChargeSite>.preferredName(): String? =
        firstOrNull { it.primarySource() == "ocm" && it.name.isNotBlank() }?.name

    private fun ChargeSite.primarySource(): String =
        sources.minByOrNull { SOURCE_PRIORITY.indexOfOrLast(it) } ?: id.substringBefore(":")

    private fun ChargeSite.priority(): Int = SOURCE_PRIORITY.indexOfOrLast(primarySource())

    private fun List<String>.indexOfOrLast(value: String): Int =
        indexOf(value).takeIf { it >= 0 } ?: size

    /**
     * Group identical connectors and sum their counts.
     *
     * If even one count is unknown, the sum stays unknown — reporting a
     * partial sum as the total would be worse than "unknown".
     */
    private fun List<Connector>.mergeConnectors(): List<Connector> =
        groupBy { it.type to it.maxPowerKw }
            .map { (key, group) ->
                val (type, powerKw) = key
                Connector(
                    type = type,
                    maxPowerKw = powerKw,
                    count = if (group.any { it.count == null }) null else group.sumOf { it.count!! },
                )
            }
            .sortedByDescending { it.maxPowerKw }

    /**
     * Grid index over the sites so not every site is compared against every
     * other one. With several thousand entries per query that would be
     * quadratic; with cells sized to the threshold, the nine-cell neighborhood
     * suffices.
     */
    private class SpatialGrid(maxDistanceMeters: Double) {
        // Cell edge in degrees, computed generously: one arcminute of latitude
        // is about 1852 m, less in longitude within Germany — the cell may
        // safely be too large; too small would be a bug.
        private val cellDegrees = maxDistanceMeters / 111_000.0
        private val cells = mutableMapOf<Pair<Int, Int>, MutableSet<Int>>()

        fun add(site: ChargeSite, clusterIndex: Int) {
            cells.getOrPut(site.cell()) { mutableSetOf() } += clusterIndex
        }

        fun nearbyClusterIndices(site: ChargeSite): List<Int> {
            val (x, y) = site.cell()
            val found = mutableSetOf<Int>()
            for (dx in -1..1) {
                for (dy in -1..1) {
                    cells[x + dx to y + dy]?.let { found += it }
                }
            }
            return found.sorted()
        }

        private fun ChargeSite.cell(): Pair<Int, Int> =
            (position.lat / cellDegrees).roundToInt() to (position.lon / cellDegrees).roundToInt()
    }
}
