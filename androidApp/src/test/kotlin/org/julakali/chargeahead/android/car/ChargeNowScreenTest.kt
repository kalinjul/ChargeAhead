package org.julakali.chargeahead.android.car

import android.app.Application
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.PlaceListMapTemplate
import androidx.car.app.model.Row
import org.julakali.chargeahead.android.car.CarTestGraph.Companion.click
import org.julakali.chargeahead.android.car.CarTestGraph.Companion.distanceOf
import org.julakali.chargeahead.android.car.CarTestGraph.Companion.settle
import org.julakali.chargeahead.android.car.CarTestGraph.Companion.hamburg
import org.julakali.chargeahead.android.phone.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ChargeNowScreenTest {

    private lateinit var graph: CarTestGraph

    @Before
    fun startGraph() {
        graph = CarTestGraph()
        graph.withVehicle()
    }

    @After
    fun stopGraph() = graph.close()

    private fun string(id: Int, vararg args: Any) = graph.carContext.getString(id, *args)

    private fun screen(): ChargeNowScreen = ChargeNowScreen(graph.carContext, graph.session).also { settle() }

    /** Roughly [index] kilometres north of the fix, inside the charge-now radius. */
    private fun site(index: Int) = CarTestGraph.site(index).copy(position = hamburg.copy(lat = hamburg.lat + index * 0.009))

    @Test
    fun `nothing stored nearby is a message`() {
        val template = screen().onGetTemplate() as MessageTemplate

        assertEquals(string(R.string.car_now_empty), template.message.toString())
    }

    @Test
    fun `candidates are numbered markers with the distance from here, nearest first`() {
        graph.sites.stored.value = listOf(site(3), site(1), site(2))

        val template = screen().onGetTemplate() as PlaceListMapTemplate

        val rows = template.itemList!!.items.map { it as Row }
        assertEquals(listOf("Lader 1", "Lader 2", "Lader 3"), rows.map { it.title.toString() })
        assertEquals(listOf("1", "2", "3"), rows.map { it.metadata!!.place!!.marker!!.label.toString() })
        assertEquals(1.0, distanceOf(rows.first().texts.first())!!, 0.2)
        assertTrue(rows.first().texts.first().toString().endsWith("300 kW"))
        assertTrue(template.isCurrentLocationEnabled)
        assertNotNull(template.onContentRefreshDelegate)
    }

    @Test
    fun `the strip's refresh reloads the stored sites`() {
        graph.sites.stored.value = listOf(site(1))
        val template = screen().onGetTemplate() as PlaceListMapTemplate
        val before = graph.sites.loads

        click(template.actionStrip!!.actions.single())

        assertEquals(before + 1, graph.sites.loads)
    }

    @Test
    fun `tapping a candidate opens its detail`() {
        graph.sites.stored.value = listOf(site(1))
        val template = screen().onGetTemplate() as PlaceListMapTemplate

        click(template.itemList!!.items.first() as Row)

        assertTrue(graph.screens.screensPushed.last() is SiteDetailScreen)
    }
}
