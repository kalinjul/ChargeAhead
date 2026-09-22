package org.julakali.chargeahead.shared.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ChargeStopOptimizerTest {

    private val battery = 60.0
    private val fast = ChargeTimeTable(usableBatteryKwh = battery, peakKw = 150.0)
    private val slow = ChargeTimeTable(usableBatteryKwh = battery, peakKw = 50.0)

    private fun node(energy: Double, table: ChargeTimeTable = fast, fixed: Double = 10.0) =
        ChargeStopOptimizer.Node(km = energy * 4.0, energyFromStartSoc = energy, fixedMinutes = fixed, chargeTime = table)

    private fun ChargeStopOptimizer.Result.found() = assertIs<ChargeStopOptimizer.Result.Found>(this)

    /** Every plan the optimizer could choose on the same grid, tried one by one. */
    private fun bruteForce(
        nodes: List<ChargeStopOptimizer.Node>,
        totalEnergy: Double,
        startSoc: Double,
        reserve: Double,
        arrival: Double,
        step: Int,
    ): Double {
        fun levels(node: ChargeStopOptimizer.Node): List<Double> =
            ((0..100 step step).map { it.toDouble() } + (totalEnergy - node.energyFromStartSoc + arrival))
                .filter { it <= 100.0 }

        var best = Double.POSITIVE_INFINITY
        fun search(fromEnergy: Double, departure: Double, nextIndex: Int, cost: Double) {
            if (cost >= best) return
            if (departure - (totalEnergy - fromEnergy) >= arrival - 1e-9) best = minOf(best, cost)
            for (j in nextIndex until nodes.size) {
                val node = nodes[j]
                val arrivalSoc = departure - (node.energyFromStartSoc - fromEnergy)
                if (arrivalSoc < reserve - 1e-9) break
                for (level in levels(node)) {
                    if (level < arrivalSoc - 1e-9) continue
                    val charge = node.chargeTime.minutesBetween(arrivalSoc, level)
                    search(node.energyFromStartSoc, level, j + 1, cost + charge + node.fixedMinutes)
                }
            }
        }
        search(0.0, startSoc, 0, 0.0)
        return best
    }

    @Test
    fun `matches exhaustive search on small random instances`() {
        val random = Random(72)
        val tables = listOf(fast, slow, ChargeTimeTable(battery, 300.0), ChargeTimeTable(battery, 90.0))
        repeat(40) { round ->
            val count = random.nextInt(2, 6)
            val totalEnergy = random.nextDouble(80.0, 220.0)
            val nodes = List(count) { random.nextDouble(5.0, totalEnergy - 5.0) }.sorted().map {
                node(it, tables.random(random), fixed = random.nextDouble(2.0, 30.0))
            }
            val startSoc = random.nextDouble(30.0, 100.0)
            val arrival = random.nextDouble(10.0, 40.0)

            val expected = bruteForce(nodes, totalEnergy, startSoc, reserve = 10.0, arrival = arrival, step = 5)
            val result = ChargeStopOptimizer(gridStepPercent = 5)
                .optimize(nodes, totalEnergy, startSoc, reserveSoc = 10.0, arrivalSoc = arrival)

            if (expected.isInfinite()) {
                assertIs<ChargeStopOptimizer.Result.Unreachable>(result, "round $round")
            } else {
                val found = result.found()
                // Ties within the cost epsilon may go to the plan with fewer stops.
                assertEquals(expected, found.costMinutes, 0.1 * (found.stops.size + 1), "round $round")
                val recomputed = found.stops.sumOf { choice ->
                    val stop = nodes[choice.nodeIndex]
                    stop.fixedMinutes + stop.chargeTime.minutesBetween(choice.arrivalSoc, choice.departureSoc)
                }
                assertEquals(found.costMinutes, recomputed, 1e-6, "round $round: the reported cost is the plan's")
            }
        }
    }

    @Test
    fun `many short hops keep every constraint without drift`() {
        val nodes = List(200) { node(energy = (it + 1) * 0.9) }
        val totalEnergy = 201 * 0.9
        val result = ChargeStopOptimizer()
            .optimize(nodes, totalEnergy, startSoc = 40.0, reserveSoc = 10.0, arrivalSoc = 25.0)
            .found()

        assertTrue(result.stops.isNotEmpty())
        var departure = 40.0
        var energy = 0.0
        for (choice in result.stops) {
            val stop = nodes[choice.nodeIndex]
            assertEquals(departure - (stop.energyFromStartSoc - energy), choice.arrivalSoc, 1e-9)
            assertTrue(choice.arrivalSoc >= 10.0 - 1e-9, "reserve kept: $choice")
            assertTrue(choice.departureSoc >= choice.arrivalSoc, "never discharges at a stop: $choice")
            assertTrue(choice.departureSoc <= 100.0, "never past full: $choice")
            departure = choice.departureSoc
            energy = stop.energyFromStartSoc
        }
        assertEquals(25.0, departure - (totalEnergy - energy), 1e-9, "arrives at exactly the requested level")
    }

    @Test
    fun `a trip within reach needs no stop`() {
        val result = ChargeStopOptimizer()
            .optimize(listOf(node(20.0)), totalEnergySoc = 50.0, startSoc = 80.0, reserveSoc = 10.0, arrivalSoc = 20.0)
            .found()
        assertTrue(result.stops.isEmpty())
        assertEquals(0.0, result.costMinutes)
    }

    @Test
    fun `no reachable charger reports how far planning got`() {
        val nodes = listOf(node(30.0), node(70.0), node(200.0))
        val result = ChargeStopOptimizer()
            .optimize(nodes, totalEnergySoc = 300.0, startSoc = 50.0, reserveSoc = 10.0, arrivalSoc = 10.0)
        assertEquals(ChargeStopOptimizer.Result.Unreachable(furthestKm = 70.0 * 4.0), result)
    }

    /** A penalised charger is a price, not a ban: it wins exactly when it saves more than the penalty. */
    @Test
    fun `a penalised charger is used only when it saves more than the penalty`() {
        val savedMinutes = slow.minutesBetween(20.0, 90.0) - fast.minutesBetween(20.0, 90.0)
        fun chosenTable(penalty: Double): ChargeTimeTable {
            val nodes = listOf(node(30.0, slow), node(30.0, fast, fixed = 10.0 + penalty))
            val result = ChargeStopOptimizer()
                .optimize(nodes, totalEnergySoc = 110.0, startSoc = 50.0, reserveSoc = 10.0, arrivalSoc = 10.0)
                .found()
            return nodes[result.stops.single().nodeIndex].chargeTime
        }

        assertEquals(fast, chosenTable(penalty = savedMinutes - 5.0))
        assertEquals(slow, chosenTable(penalty = savedMinutes + 5.0))
    }

    @Test
    fun `ties always go to the later stop`() {
        val nodes = listOf(node(30.0), node(30.0))
        val runs = List(3) {
            ChargeStopOptimizer()
                .optimize(nodes, totalEnergySoc = 110.0, startSoc = 50.0, reserveSoc = 10.0, arrivalSoc = 10.0)
                .found()
        }
        assertEquals(1, runs.first().stops.single().nodeIndex)
        assertTrue(runs.all { it == runs.first() })
    }

    @Test
    fun `an excluded stop is planned around`() {
        val nodes = listOf(node(40.0), node(45.0))
        val result = ChargeStopOptimizer()
            .optimize(nodes, 110.0, startSoc = 60.0, reserveSoc = 10.0, arrivalSoc = 10.0, excluded = 1)
            .found()
        assertEquals(listOf(0), result.stops.map { it.nodeIndex })
    }
}
