package de.autoapp.shared.core

/**
 * The time-optimal set of charging stops along a fixed route, as a shortest
 * path over (stop, departure level). Nodes are visited in route order, so the
 * graph is a DAG and one sweep is exact up to the departure grid.
 *
 * Only departure levels are gridded — whole percents, plus per stop the level
 * that finishes the trip exactly. Arrival levels stay continuous, so short hops
 * never accumulate rounding and the reserve is never silently undercut.
 *
 * Everything is in percent of the usable battery. Driving time is left out:
 * on a fixed route every plan drives the same total.
 */
internal class ChargeStopOptimizer(
    private val maxDepartureSoc: Double = 100.0,
    /** Coarser grids only make exhaustive tests affordable. */
    private val gridStepPercent: Int = 1,
) {

    class Node(
        val km: Double,
        /** Energy from the route start to this stop. Non-decreasing along the node list. */
        val energyFromStartSoc: Double,
        /** Everything a stop costs besides charging: overhead, detour, penalties. */
        val fixedMinutes: Double,
        val chargeTime: ChargeTimeTable,
    )

    data class Choice(val nodeIndex: Int, val arrivalSoc: Double, val departureSoc: Double)

    sealed interface Result {
        data class Found(val stops: List<Choice>, val costMinutes: Double) : Result

        /** [furthestKm] is the last stop any plan can reach, 0 when none can. */
        data class Unreachable(val furthestKm: Double) : Result
    }

    fun optimize(
        nodes: List<Node>,
        totalEnergySoc: Double,
        startSoc: Double,
        reserveSoc: Double,
        arrivalSoc: Double,
        excluded: Int? = null,
    ): Result {
        val n = nodes.size
        // Slot 0 is the start, slot t is nodes[t - 1].
        val energy = DoubleArray(n + 1) { if (it == 0) 0.0 else nodes[it - 1].energyFromStartSoc }
        val levels = Array(n + 1) { slot ->
            if (slot == 0) doubleArrayOf(startSoc) else departureLevels(totalEnergySoc - energy[slot] + arrivalSoc)
        }
        val cost = Array(n + 1) { DoubleArray(levels[it].size) { Double.POSITIVE_INFINITY } }
        val stops = Array(n + 1) { IntArray(levels[it].size) }
        val prevSlot = Array(n + 1) { IntArray(levels[it].size) { -1 } }
        val prevLevel = Array(n + 1) { IntArray(levels[it].size) { -1 } }
        val arrival = Array(n + 1) { DoubleArray(levels[it].size) }
        cost[0][0] = 0.0

        val maxLegSoc = maxOf(maxDepartureSoc, startSoc) - reserveSoc
        var furthestKm = 0.0

        for (j in 1..n) {
            if (excluded == j - 1) continue
            val node = nodes[j - 1]
            val levelsJ = levels[j]
            val chargeJ = node.chargeTime
            val minutesAtLevel = DoubleArray(levelsJ.size) { chargeJ.minutesTo(levelsJ[it]) }

            // Later predecessors first: on a full tie the first one found stays.
            var i = j - 1
            while (i >= 0) {
                val leg = energy[j] - energy[i]
                if (leg > maxLegSoc) break
                if (i == 0 || excluded != i - 1) {
                    relax(
                        levelsI = levels[i], costI = cost[i], stopsI = stops[i],
                        leg = leg, reserveSoc = reserveSoc, chargeJ = chargeJ,
                        levelsJ = levelsJ, minutesAtLevel = minutesAtLevel, fixedMinutes = node.fixedMinutes,
                        costJ = cost[j], stopsJ = stops[j], prevSlotJ = prevSlot[j], prevLevelJ = prevLevel[j],
                        arrivalJ = arrival[j], fromSlot = i,
                    )
                }
                i--
            }
            if (cost[j].any { it.isFinite() }) furthestKm = maxOf(furthestKm, node.km)
        }

        var bestSlot = -1
        var bestLevel = -1
        for (slot in n downTo 0) {
            if (slot > 0 && excluded == slot - 1) continue
            val finalLeg = totalEnergySoc - energy[slot]
            val levelsS = levels[slot]
            for (k in levelsS.indices) {
                val c = cost[slot][k]
                if (!c.isFinite() || levelsS[k] - finalLeg < arrivalSoc - SOC_TOLERANCE) continue
                if (bestSlot < 0 || isBetter(c, stops[slot][k], cost[bestSlot][bestLevel], stops[bestSlot][bestLevel])) {
                    bestSlot = slot
                    bestLevel = k
                }
            }
        }
        if (bestSlot < 0) return Result.Unreachable(furthestKm)

        val chosen = ArrayList<Choice>()
        var slot = bestSlot
        var k = bestLevel
        while (slot > 0) {
            chosen += Choice(nodeIndex = slot - 1, arrivalSoc = arrival[slot][k], departureSoc = levels[slot][k])
            val previous = prevSlot[slot][k]
            k = prevLevel[slot][k]
            slot = previous
        }
        chosen.reverse()
        return Result.Found(chosen, cost[bestSlot][bestLevel])
    }

    /**
     * Relaxes every label of j from every label of i in O(S) instead of O(S²):
     * the cost splits as `F_j(d) − F_j(d_i − leg)`, so sweeping d upward only
     * needs a running minimum of `best_i − F_j(arrival)` over the arrivals at
     * or below d.
     */
    private fun relax(
        levelsI: DoubleArray, costI: DoubleArray, stopsI: IntArray,
        leg: Double, reserveSoc: Double, chargeJ: ChargeTimeTable,
        levelsJ: DoubleArray, minutesAtLevel: DoubleArray, fixedMinutes: Double,
        costJ: DoubleArray, stopsJ: IntArray, prevSlotJ: IntArray, prevLevelJ: IntArray,
        arrivalJ: DoubleArray, fromSlot: Int,
    ) {
        var p = 0
        while (p < levelsI.size && levelsI[p] - leg < reserveSoc - SOC_TOLERANCE) p++
        if (p == levelsI.size) return

        var runValue = Double.POSITIVE_INFINITY
        var runStops = 0
        var runLevel = -1
        var runArrival = 0.0
        for (k in levelsJ.indices) {
            val departure = levelsJ[k]
            while (p < levelsI.size && levelsI[p] - leg <= departure + SOC_TOLERANCE) {
                val c = costI[p]
                if (c.isFinite()) {
                    val arrivalSoc = levelsI[p] - leg
                    val value = c - chargeJ.minutesTo(arrivalSoc)
                    if (runLevel < 0 || isBetter(value, stopsI[p], runValue, runStops)) {
                        runValue = value
                        runStops = stopsI[p]
                        runLevel = p
                        runArrival = arrivalSoc
                    }
                }
                p++
            }
            if (runLevel < 0) continue
            val candidate = runValue + minutesAtLevel[k] + fixedMinutes
            val candidateStops = runStops + 1
            if (prevLevelJ[k] < 0 || isBetter(candidate, candidateStops, costJ[k], stopsJ[k])) {
                costJ[k] = candidate
                stopsJ[k] = candidateStops
                prevSlotJ[k] = fromSlot
                prevLevelJ[k] = runLevel
                arrivalJ[k] = minOf(runArrival, departure)
            }
        }
    }

    /** The grid up to the cap, plus [finishingLevel] when it is off-grid and allowed. */
    private fun departureLevels(finishingLevel: Double): DoubleArray {
        val grid = DoubleArray(maxDepartureSoc.toInt() / gridStepPercent + 1) { (it * gridStepPercent).toDouble() }
        val offGrid = finishingLevel > 0.0 && finishingLevel <= maxDepartureSoc &&
            grid.none { it == finishingLevel }
        if (!offGrid) return grid
        val insertAt = grid.count { it < finishingLevel }
        return DoubleArray(grid.size + 1) { index ->
            when {
                index < insertAt -> grid[index]
                index == insertAt -> finishingLevel
                else -> grid[index - 1]
            }
        }
    }

    /** Cheaper by more than [COST_EPSILON_MINUTES], or as cheap with fewer stops. */
    private fun isBetter(cost: Double, stops: Int, otherCost: Double, otherStops: Int): Boolean =
        cost < otherCost - COST_EPSILON_MINUTES ||
            (cost <= otherCost + COST_EPSILON_MINUTES && stops < otherStops)

    private companion object {
        const val COST_EPSILON_MINUTES = 0.1
        const val SOC_TOLERANCE = 1e-9
    }
}
