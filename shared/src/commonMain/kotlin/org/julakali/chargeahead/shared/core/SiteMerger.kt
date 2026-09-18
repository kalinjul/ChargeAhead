package org.julakali.chargeahead.shared.core

import org.julakali.chargeahead.shared.domain.ChargeSite
import org.julakali.chargeahead.shared.domain.Connector
import org.julakali.chargeahead.shared.domain.distanceKmTo
import kotlin.math.roundToInt

/**
 * Merges the same site reported by multiple sources (or several times by the
 * same source) into one, based on **spatial proximity and overlapping
 * connector signature**.
 *
 * Field-by-field priority:
 *
 * | Field | Priority |
 * |---|---|
 * | Position, operator, connectors | Bundesnetzagentur (officially reported) |
 * | Name/display | OpenChargeMap, falling back to Bundesnetzagentur |
 */
object SiteMerger {

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

        // Sorted by id so the same input always produces the same output.
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
     * If either side doesn't know its connectors, the condition counts as satisfied.
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
            name = cluster.preferredName() ?: leading.name,
            operator = leading.operator ?: cluster.firstNotNullOfOrNull { it.operator },
            position = leading.position,
            // Sum connectors only from the leading source: the sources describe the same devices.
            connectors = sameSource.flatMap { it.connectors }.mergeConnectors(),
            address = leading.address ?: cluster.firstNotNullOfOrNull { it.address },
            sources = cluster.flatMapTo(mutableSetOf()) { it.sources },
            liveStatusId = leading.liveStatusId ?: cluster.firstNotNullOfOrNull { it.liveStatusId },
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
     * If even one count is unknown, the sum stays unknown.
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

    /** Grid index with cells sized to the threshold, so the nine-cell neighborhood suffices. */
    private class SpatialGrid(maxDistanceMeters: Double) {
        // Cell edge in degrees, generously: too large is safe, too small would be a bug.
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
