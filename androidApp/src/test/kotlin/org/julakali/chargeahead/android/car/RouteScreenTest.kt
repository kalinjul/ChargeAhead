package org.julakali.chargeahead.android.car

import android.app.Application
import androidx.car.app.CarContext
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.CarColor
import androidx.car.app.model.ForegroundCarColorSpan
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.PlaceListMapTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.testing.ScreenController
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.sync.Mutex
import org.julakali.chargeahead.android.car.CarTestGraph.Companion.click
import org.julakali.chargeahead.android.car.CarTestGraph.Companion.distanceOf
import org.julakali.chargeahead.android.car.CarTestGraph.Companion.munich
import org.julakali.chargeahead.android.car.CarTestGraph.Companion.plan
import org.julakali.chargeahead.android.car.CarTestGraph.Companion.settle
import org.julakali.chargeahead.android.phone.R
import org.julakali.chargeahead.shared.ChargeStopFormatter
import org.julakali.chargeahead.shared.domain.EnergyState
import org.julakali.chargeahead.shared.domain.SoCSourceKind
import org.julakali.chargeahead.shared.domain.TripPlanResult
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The route screen on the production graph with the planner, the location
 * and the settings faked. Every dispatcher is unconfined, so a plan is on
 * the screen once the main looper has drained.
 */
@RunWith(RobolectricTestRunner::class)
// The real Application starts the production Koin graph; this test brings its own.
@Config(application = Application::class)
class RouteScreenTest {

    private var graph: CarTestGraph? = null

    private fun graph(energy: EnergyState? = null): CarTestGraph = CarTestGraph(energy = energy).also { graph = it }

    @After
    fun stopGraph() {
        graph?.close()
    }

    private fun string(id: Int, vararg args: Any) = graph!!.carContext.getString(id, *args)

    private fun message(template: Template): String = (template as MessageTemplate).message.toString()

    /** The screen's template once the plan the fake returns has landed. */
    private fun CarTestGraph.templateFor(result: TripPlanResult): Template {
        planner.result = result
        val screen = RouteScreen(carContext, session, munich, permissions = permissions)
        settle()
        return screen.onGetTemplate()
    }

    @Test
    fun `without a vehicle the screen says so`() {
        val graph = graph()
        assertEquals(string(R.string.car_route_no_vehicle), message(graph.templateFor(TripPlanResult.NoRoute)))
    }

    @Test
    fun `no route is a message`() {
        val graph = graph()
        graph.withVehicle()

        assertEquals(string(R.string.car_route_no_route), message(graph.templateFor(TripPlanResult.NoRoute)))
    }

    @Test
    fun `no charger in reach names the distance`() {
        val graph = graph()
        graph.withVehicle()

        val template = graph.templateFor(TripPlanResult.NoChargerInReach(afterKm = 312.4))

        assertEquals(string(R.string.car_route_no_charger, ChargeStopFormatter.distanceLabel(312.4)), message(template))
    }

    @Test
    fun `a plan without stops is the direct message`() {
        val graph = graph()
        graph.withVehicle()

        assertEquals(string(R.string.car_route_direct), message(graph.templateFor(TripPlanResult.Planned(plan(stops = 0)))))
    }

    @Test
    fun `every stop is a numbered marker in its operator colour, the whole-route send takes the last slot`() {
        val graph = graph()
        graph.withVehicle()
        val limit = graph.carContext.getCarService(ConstraintManager::class.java).getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_PLACE_LIST)
        val plan = plan(stops = limit + 3)

        val template = graph.templateFor(TripPlanResult.Planned(plan)) as PlaceListMapTemplate

        val rows = template.itemList!!.items.map { it as Row }
        assertEquals(limit, rows.size)
        assertEquals(string(R.string.car_route_send_all), rows.last().title.toString())
        assertEquals(string(R.string.car_route_send_all_hint), rows.last().texts.single().toString())
        assertNull(rows.last().metadata?.place)
        val stops = rows.dropLast(1)
        assertEquals(
            plan.stops.take(limit - 1).mapIndexed { index, stop -> ChargeStopFormatter.plannedStopTitle(index + 1, stop) },
            stops.map { it.title.toString() },
        )
        val markers = stops.map { it.metadata!!.place!!.marker!! }
        assertEquals((1 until limit).map(Int::toString), markers.map { it.label.toString() })
        assertEquals(operatorCarColor(plan.stops.first().site), markers.first().color)
        assertEquals(2, template.actionStrip!!.actions.size)
        assertTrue(template.isCurrentLocationEnabled)
        assertEquals(munich.position.lat, template.anchor!!.location!!.latitude, 1e-9)
        assertTrue(template.isLoading.not())
    }

    @Test
    fun `a stop row leads with the distance from here and colours a low arrival level`() {
        val graph = graph()
        graph.withVehicle()

        val template = graph.templateFor(TripPlanResult.Planned(plan(stops = 1, arrivalSocPercent = 8.0))) as PlaceListMapTemplate

        val stop = template.itemList!!.items.first() as Row
        val line = stop.texts.first()
        // Stop 1 sits 0.5° south and 0.1° east of the fix: a little under 56 km.
        assertEquals(56.0, distanceOf(line)!!, 1.0)
        assertTrue(line.toString().endsWith(string(R.string.car_route_arrival, 8)))
        val colour = line.spans.map { it.carSpan }.filterIsInstance<ForegroundCarColorSpan>().single().color
        assertEquals(CarColor.RED, colour)
        assertEquals(string(R.string.car_route_charge, "300 kW", "24 min"), stop.texts[1].toString())
    }

    @Test
    fun `starting navigation commits the plan, hands the first stop to the host and returns to the grid`() {
        val graph = graph()
        graph.withVehicle()
        val plan = plan(stops = 2)
        graph.planner.result = TripPlanResult.Planned(plan)
        // The grid is the root; the route sits on top of it, as after a destination search.
        graph.screens.push(CarHomeScreen(graph.carContext, graph.feature, graph.session, graph.permissions))
        val screen = RouteScreen(graph.carContext, graph.session, munich, permissions = graph.permissions)
        graph.screens.push(screen)
        // The test manager pops only screens that have a lifecycle, as the host's does.
        ScreenController(screen).moveToState(Lifecycle.State.STARTED)
        settle()
        val template = screen.onGetTemplate() as PlaceListMapTemplate
        assertNull(graph.trips.state.value.committed)

        click(template.actionStrip!!.actions.first())

        assertEquals(plan, graph.trips.state.value.committed!!.plan)
        val intent = graph.carContext.startCarAppIntents.single()
        assertEquals(CarContext.ACTION_NAVIGATE, intent.action)
        assertTrue(intent.data.toString().contains("Lader%201"))
        assertTrue(graph.screens.screensRemoved.contains(screen))
    }

    @Test
    fun `tapping a stop opens its detail`() {
        val graph = graph()
        graph.withVehicle()
        val template = graph.templateFor(TripPlanResult.Planned(plan(stops = 1))) as PlaceListMapTemplate

        click(template.itemList!!.items.first() as Row)

        assertTrue(graph.screens.screensPushed.last() is SiteDetailScreen)
    }

    @Test
    fun `neu planen without a car reading asks for the level, and the pick re-plans with it`() {
        val graph = graph()
        graph.withVehicle()
        graph.planner.result = TripPlanResult.Planned(plan(stops = 1))
        graph.withCommittedTrip(plan(stops = 1))
        val screen = RouteScreen(graph.carContext, graph.session, munich, activeRoute = true, permissions = graph.permissions)
        settle()
        val replan = (screen.onGetTemplate() as PlaceListMapTemplate).actionStrip!!.actions.last()

        click(replan)

        val picker = graph.screens.screensPushed.last()
        assertTrue(picker is SoCScreen)
        assertTrue(graph.planner.startLevels.isEmpty())

        val step = (picker.onGetTemplate() as androidx.car.app.model.ListTemplate).singleList!!.items.map { it as Row }
            .first { it.title.toString() == string(R.string.car_soc_percent, 90) }
        click(step)

        assertEquals(listOf(90.0), graph.planner.startLevels)
    }

    @Test
    fun `neu planen with the car's reading re-plans right away`() {
        val graph = graph(energy = EnergyState(42.0, SoCSourceKind.CAR_HARDWARE, 0L))
        graph.withVehicle()
        graph.planner.result = TripPlanResult.Planned(plan(stops = 1))
        graph.withCommittedTrip(plan(stops = 1))
        val screen = RouteScreen(graph.carContext, graph.session, munich, activeRoute = true, permissions = graph.permissions)
        settle()
        val replan = (screen.onGetTemplate() as PlaceListMapTemplate).actionStrip!!.actions.last()

        click(replan)

        assertEquals(listOf(42.0), graph.planner.startLevels)
        assertTrue(graph.screens.screensPushed.none { it is SoCScreen })
    }

    @Test
    fun `while planning the map is loading`() {
        val graph = graph()
        graph.withVehicle()
        graph.planner.result = TripPlanResult.NoRoute
        graph.planner.gate = Mutex(locked = true)
        val screen = RouteScreen(graph.carContext, graph.session, munich, permissions = graph.permissions)
        settle()

        assertTrue((screen.onGetTemplate() as PlaceListMapTemplate).isLoading)
        assertNotNull((screen.onGetTemplate() as PlaceListMapTemplate).headerAction)

        graph.planner.gate!!.unlock()
        settle()
        assertEquals(string(R.string.car_route_no_route), message(screen.onGetTemplate()))
    }
}
