package org.julakali.chargeahead.shared.core

import org.julakali.chargeahead.shared.domain.ConnectorType
import org.julakali.chargeahead.shared.domain.LatLon
import org.julakali.chargeahead.shared.domain.RoadLoad
import org.julakali.chargeahead.shared.domain.Route
import org.julakali.chargeahead.shared.domain.RouteSegment
import org.julakali.chargeahead.shared.domain.VehicleProfile
import org.julakali.chargeahead.vehicle.ConsumptionModel as VehiclePhysics
import org.julakali.chargeahead.vehicle.RoadLoad as ForceCurve
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConsumptionModelTest {

    private val handTyped = VehicleProfile("Eigenbau", 60.0, 16.0, setOf(ConnectorType.CCS2))

    // The backend's generic curve for a 15.3 kWh/100 km WLTP figure.
    private val mg4Curve = RoadLoad(132.344, 0.547632, 0.0269253, 2000.0, 0.94, 1.0, 0.65, wltpKwhPer100Km = 15.3)
    private val mg4 = VehicleProfile("MG4", 52.8, 15.3, setOf(ConnectorType.CCS2), modelId = "mg4", roadLoad = mg4Curve)

    private val model = RoadLoadConsumption(handTyped)

    private fun route(distanceKm: Double, averageSpeedKmh: Double, segments: List<RouteSegment> = emptyList()) = Route(
        points = listOf(LatLon(52.37, 4.90), LatLon(48.14, 11.58)),
        distanceKm = distanceKm,
        durationMinutes = distanceKm / averageSpeedKmh * 60.0,
        segments = segments,
    )

    private fun segment(fromKm: Double, distanceKm: Double, speedKmh: Double, ascentM: Double = 0.0, descentM: Double = 0.0) =
        RouteSegment(fromKm, distanceKm, distanceKm / speedKmh * 60.0, ascentM, descentM)

    /** Battery energy to lift [model]'s generic 2000 kg by [metres]. */
    private fun liftKwh(metres: Double) = 2000.0 * 9.81 * metres / 3_600_000.0 / 0.94

    @Test
    fun `a car without a curve gets a generic one fitted to its consumption`() {
        val generic = genericRoadLoad(16.0)

        assertEquals(16.0, generic.wltpKwhPer100Km, 1e-9)
        assertEquals(16.0, VehiclePhysics.wltpKwhPer100Km(ForceCurve(generic.f0, generic.f1, generic.f2), generic.massKg), 1e-6)
    }

    @Test
    fun `the backend's WLTP figure for the curve is what the driver's figure is measured against`() {
        val served = RoadLoadConsumption(mg4.copy(roadLoad = mg4Curve.copy(wltpKwhPer100Km = 17.0)))
        val simulated = RoadLoadConsumption(mg4)

        assertEquals(15.3 / 17.0, served.kwhPer100KmAt(100.0) / simulated.kwhPer100KmAt(100.0), 1e-9)
    }

    @Test
    fun `the catalog curve is used as it is when the driver keeps the catalog figure`() {
        val consumption = RoadLoadConsumption(mg4)
        val curve = mg4Curve.forceN(120.0) / (36.0 * 0.94) + 100.0 / 120.0

        // Rolling, aero and auxiliary at 120 km/h, plus a little for speed changes.
        assertTrue(consumption.kwhPer100KmAt(120.0) in curve..curve * 1.1, "${consumption.kwhPer100KmAt(120.0)} vs $curve")
    }

    @Test
    fun `the driver's figure scales the whole curve`() {
        val own = RoadLoadConsumption(mg4.copy(consumptionKwhPer100Km = 18.36))
        val catalog = RoadLoadConsumption(mg4)

        assertEquals(1.2, own.kwhPer100KmAt(130.0) / catalog.kwhPer100KmAt(130.0), 1e-9)
        assertEquals(1.2, own.kwhPer100KmAt(30.0) / catalog.kwhPer100KmAt(30.0), 1e-9)
    }

    @Test
    fun `the motorway costs more than the WLTP average`() {
        assertTrue(model.kwhPer100KmAt(130.0) > handTyped.consumptionKwhPer100Km * 1.2)
        assertTrue(model.kwhPer100KmAt(100.0) > handTyped.consumptionKwhPer100Km)
    }

    @Test
    fun `town driving is not discounted far below the WLTP average`() {
        val town = model.kwhPer100KmAt(35.0)

        assertTrue(town in handTyped.consumptionKwhPer100Km * 0.8..handTyped.consumptionKwhPer100Km * 1.1, "town: $town")
    }

    @Test
    fun `the cheapest speed lies between town and motorway`() {
        val rural = model.kwhPer100KmAt(55.0)

        assertTrue(rural < model.kwhPer100KmAt(30.0))
        assertTrue(rural < model.kwhPer100KmAt(120.0))
    }

    @Test
    fun `the segments decide and not the route average`() {
        val mixed = route(
            distanceKm = 100.0,
            averageSpeedKmh = 100.0,
            segments = listOf(segment(0.0, 50.0, 60.0), segment(50.0, 50.0, 160.0)),
        )

        val perSegment = model.energyKwh(mixed, 0.0, 100.0)
        val flat = model.energyKwh(route(100.0, 100.0), 0.0, 100.0)

        assertTrue(perSegment > flat, "A mix of slow and fast costs more: $perSegment vs $flat")
    }

    @Test
    fun `a stretch inside one segment is priced at that segment's speed`() {
        val mixed = route(
            distanceKm = 100.0,
            averageSpeedKmh = 100.0,
            segments = listOf(segment(0.0, 50.0, 60.0), segment(50.0, 50.0, 130.0)),
        )

        assertEquals(model.kwhPer100KmAt(60.0) / 10.0, model.energyKwh(mixed, 10.0, 20.0), 1e-9)
        assertEquals(model.kwhPer100KmAt(130.0) / 10.0, model.energyKwh(mixed, 60.0, 70.0), 1e-9)
    }

    @Test
    fun `a route without segments is priced at its average speed`() {
        val plain = route(distanceKm = 100.0, averageSpeedKmh = 90.0)

        assertEquals(model.kwhPer100KmAt(90.0), model.energyKwh(plain, 0.0, 100.0), 1e-9)
    }

    @Test
    fun `reach is the inverse of energy`() {
        val mixed = route(
            distanceKm = 400.0,
            averageSpeedKmh = 110.0,
            segments = listOf(segment(0.0, 100.0, 70.0), segment(100.0, 300.0, 125.0)),
        )

        val budget = model.energyKwh(mixed, 20.0, 260.0)

        assertEquals(260.0, model.reachKm(mixed, 20.0, budget), 1e-6)
    }

    @Test
    fun `reach runs past the end of the route at its average speed`() {
        val short = route(distanceKm = 50.0, averageSpeedKmh = 100.0)

        val reach = model.reachKm(short, 0.0, availableKwh = 40.0)

        assertEquals(40.0 / model.kwhPer100KmAt(100.0) * 100.0, reach, 1e-6)
    }

    @Test
    fun `an empty budget gets nowhere`() {
        val plain = route(100.0, 100.0)

        assertEquals(0.0, model.reachKm(plain, 0.0, 0.0), 1e-9)
        assertEquals(30.0, model.reachKm(plain, 30.0, -5.0), 1e-9)
    }

    @Test
    fun `a crawling segment is priced like a jam and not like standing still`() {
        assertEquals(model.kwhPer100KmAt(10.0), model.kwhPer100KmAt(3.0), 1e-9)
    }

    @Test
    fun `nothing is spent on a stretch of zero length`() {
        val plain = route(100.0, 100.0)

        assertEquals(0.0, model.energyKwh(plain, 40.0, 40.0), 1e-9)
        assertEquals(0.0, model.energyKwh(plain, 60.0, 40.0), 1e-9)
    }

    @Test
    fun `climbing costs the lift on top of the level road`() {
        val level = route(50.0, 100.0, listOf(segment(0.0, 50.0, 100.0)))
        val uphill = route(50.0, 100.0, listOf(segment(0.0, 50.0, 100.0, ascentM = 600.0)))

        assertEquals(liftKwh(600.0), model.energyKwh(uphill, 0.0, 50.0) - model.energyKwh(level, 0.0, 50.0), 1e-9)
    }

    @Test
    fun `a gentle descent saves what the motor would have spent`() {
        val level = route(50.0, 100.0, listOf(segment(0.0, 50.0, 100.0)))
        val downhill = route(50.0, 100.0, listOf(segment(0.0, 50.0, 100.0, descentM = 100.0)))

        assertEquals(liftKwh(100.0), model.energyKwh(level, 0.0, 50.0) - model.energyKwh(downhill, 0.0, 50.0), 1e-9)
    }

    @Test
    fun `a steep descent charges the battery but only by the recuperated share`() {
        val pass = route(20.0, 60.0, listOf(segment(0.0, 20.0, 60.0, descentM = 1200.0)))

        val energy = model.energyKwh(pass, 0.0, 20.0)

        assertTrue(energy < 0.0, "a long way down recharges: $energy")
        assertTrue(-energy < liftKwh(1200.0) * 0.94 * 0.65, "never more than the recuperated share: $energy")
    }

    @Test
    fun `going over a mountain costs more than going around it`() {
        val level = route(40.0, 60.0, listOf(segment(0.0, 40.0, 60.0)))
        val overThePass = route(40.0, 60.0, listOf(segment(0.0, 20.0, 60.0, ascentM = 1000.0), segment(20.0, 20.0, 60.0, descentM = 1000.0)))

        assertTrue(model.energyKwh(overThePass, 0.0, 40.0) > model.energyKwh(level, 0.0, 40.0))
    }

    @Test
    fun `the peak is at the summit and not at the end`() {
        val overThePass = route(40.0, 60.0, listOf(segment(0.0, 20.0, 60.0, ascentM = 1000.0), segment(20.0, 20.0, 60.0, descentM = 1000.0)))

        val toSummit = model.energyKwh(overThePass, 0.0, 20.0)

        assertEquals(toSummit, model.peakEnergyKwh(overThePass, 0.0, 40.0), 1e-9)
        assertTrue(model.energyKwh(overThePass, 0.0, 40.0) < toSummit)
    }

    @Test
    fun `a segment that climbs and drops is assumed to climb first`() {
        val rolling = route(20.0, 60.0, listOf(segment(0.0, 20.0, 60.0, ascentM = 600.0, descentM = 300.0)))

        val peak = model.peakEnergyKwh(rolling, 0.0, 20.0)

        // Two thirds of the height is climbing, so the first two thirds of the distance climb.
        assertEquals(model.energyKwh(rolling, 0.0, 20.0 * 2.0 / 3.0), peak, 1e-9)
        assertTrue(peak > model.energyKwh(rolling, 0.0, 20.0))
    }

    @Test
    fun `the peak of a level road is its energy`() {
        val plain = route(100.0, 100.0)

        assertEquals(model.energyKwh(plain, 10.0, 70.0), model.peakEnergyKwh(plain, 10.0, 70.0), 1e-9)
    }

    @Test
    fun `reach ends below the summit when the battery runs out on the climb`() {
        val overThePass = route(40.0, 60.0, listOf(segment(0.0, 20.0, 60.0, ascentM = 1000.0), segment(20.0, 20.0, 60.0, descentM = 1000.0)))
        val enoughForTheTotal = model.energyKwh(overThePass, 0.0, 40.0) + 0.1

        assertTrue(model.reachKm(overThePass, 0.0, enoughForTheTotal) < 20.0)
    }

    @Test
    fun `reach is the inverse of energy over hills`() {
        val hilly = route(
            distanceKm = 300.0,
            averageSpeedKmh = 100.0,
            segments = listOf(
                segment(0.0, 100.0, 90.0, ascentM = 800.0, descentM = 200.0),
                segment(100.0, 100.0, 120.0, ascentM = 50.0, descentM = 400.0),
                segment(200.0, 100.0, 110.0, ascentM = 300.0, descentM = 300.0),
            ),
        )

        val budget = model.energyKwh(hilly, 10.0, 250.0)

        assertEquals(250.0, model.reachKm(hilly, 10.0, budget), 1e-6)
    }
}
