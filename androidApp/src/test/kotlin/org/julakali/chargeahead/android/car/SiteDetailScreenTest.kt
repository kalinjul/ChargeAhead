package org.julakali.chargeahead.android.car

import android.app.Application
import androidx.car.app.CarContext
import androidx.car.app.model.CarColor
import androidx.car.app.model.PaneTemplate
import org.julakali.chargeahead.android.car.CarTestGraph.Companion.click
import org.julakali.chargeahead.android.car.CarTestGraph.Companion.distanceOf
import org.julakali.chargeahead.android.car.CarTestGraph.Companion.plan
import org.julakali.chargeahead.android.car.CarTestGraph.Companion.settle
import org.julakali.chargeahead.android.car.CarTestGraph.Companion.site
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.julakali.chargeahead.shared.resources.Res
import org.julakali.chargeahead.shared.resources.car_detail_charge
import org.julakali.chargeahead.shared.resources.car_detail_navigate
import org.julakali.chargeahead.shared.Texts
import org.jetbrains.compose.resources.StringResource

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class SiteDetailScreenTest {

    private lateinit var graph: CarTestGraph

    @Before
    fun startGraph() {
        graph = CarTestGraph()
    }

    @After
    fun stopGraph() = graph.close()

    private fun string(resource: StringResource, vararg args: Any) = Texts.string(resource, *args)

    @Test
    fun `a planned stop shows the distance with its charge and hands off to navigation`() {
        val stop = plan(stops = 1).stops.single()
        val screen = SiteDetailScreen(graph.carContext, graph.session, stop.site, stop)
        settle()

        val template = screen.onGetTemplate() as PaneTemplate

        assertEquals("Lader 1", template.header!!.title.toString())
        val chargeRow = template.pane.rows.first { distanceOf(it.title) != null }
        assertEquals(56.0, distanceOf(chargeRow.title)!!, 1.0)
        assertTrue(chargeRow.title.toString().endsWith(string(Res.string.car_detail_charge, 15, 80, "24 min")))
        assertTrue(template.pane.rows.none { it.title.toString() == "Operator 1" })

        val navigate = template.pane.actions.first()
        assertEquals(string(Res.string.car_detail_navigate), navigate.title.toString())
        assertEquals(CarColor.PRIMARY, navigate.backgroundColor)
        click(navigate)
        assertEquals(CarContext.ACTION_NAVIGATE, graph.carContext.startCarAppIntents.single().action)
    }

    @Test
    fun `a plain site shows its power instead of a charge`() {
        val screen = SiteDetailScreen(graph.carContext, graph.session, site(1))
        settle()

        val rows = (screen.onGetTemplate() as PaneTemplate).pane.rows

        assertTrue(rows.any { it.title.toString().endsWith("300 kW") })
    }
}
