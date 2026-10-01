package org.julakali.chargeahead.android.car

import android.Manifest
import android.app.Application
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.MessageTemplate
import androidx.test.core.app.ApplicationProvider
import org.julakali.chargeahead.android.car.CarTestGraph.Companion.click
import org.julakali.chargeahead.android.car.CarTestGraph.Companion.plan
import org.julakali.chargeahead.android.car.CarTestGraph.Companion.settle
import org.julakali.chargeahead.android.phone.R
import org.julakali.chargeahead.shared.domain.EnergyState
import org.julakali.chargeahead.shared.domain.SoCSourceKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class CarHomeScreenTest {

    private var graph: CarTestGraph? = null

    private fun graph(energy: EnergyState? = null, located: Boolean = true): CarTestGraph {
        if (located) {
            shadowOf(ApplicationProvider.getApplicationContext<Application>())
                .grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        return CarTestGraph(energy = energy).also { graph = it }
    }

    @After
    fun stopGraph() {
        graph?.close()
    }

    private fun string(id: Int, vararg args: Any) = graph!!.carContext.getString(id, *args)

    private fun CarTestGraph.tiles(): List<GridItem> {
        val screen = CarHomeScreen(carContext, feature, session, permissions)
        settle()
        return (screen.onGetTemplate() as GridTemplate).singleList!!.items.map { it as GridItem }
    }

    @Test
    fun `the tiles are destination, charge now and the level, which reads unset without one`() {
        val tiles = graph().tiles()

        assertEquals(
            listOf(R.string.car_home_enter_destination, R.string.car_home_charge_now, R.string.car_home_soc).map(::string),
            tiles.map { it.title.toString() },
        )
        assertEquals(string(R.string.car_home_soc_unset), tiles.last().text.toString())
        assertEquals(R.drawable.ic_battery_0, tiles.last().image!!.icon!!.resId)
    }

    @Test
    fun `the level tile shows the percent the feature knows`() {
        val tiles = graph(energy = EnergyState(63.0, SoCSourceKind.MANUAL, 0L)).tiles()

        assertEquals(string(R.string.car_home_soc_percent, 63), tiles.last().text.toString())
        assertEquals(R.drawable.ic_battery_60, tiles.last().image!!.icon!!.resId)
    }

    @Test
    fun `a committed trip gets a tile and takes over the screen`() {
        val graph = graph()
        graph.withVehicle()
        graph.withCommittedTrip(plan(stops = 1))

        val tiles = graph.tiles()

        assertEquals(string(R.string.car_home_active_route), tiles.first().title.toString())
        assertEquals("München", tiles.first().text.toString())
        assertTrue(graph.screens.screensPushed.single() is RouteScreen)
    }

    @Test
    fun `tapping a tile opens its screen`() {
        val graph = graph()

        click(graph.tiles()[1])

        assertTrue(graph.screens.screensPushed.last() is ChargeNowScreen)
    }

    @Test
    fun `without the location permission the screen asks for it`() {
        val graph = graph(located = false)
        val screen = CarHomeScreen(graph.carContext, graph.feature, graph.session, graph.permissions)
        settle()

        val template = screen.onGetTemplate() as MessageTemplate

        assertEquals(string(R.string.car_permission_message), template.message.toString())
        assertTrue(graph.screens.screensPushed.isEmpty())
    }
}
