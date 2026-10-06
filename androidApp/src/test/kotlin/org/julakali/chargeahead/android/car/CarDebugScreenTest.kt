package org.julakali.chargeahead.android.car

import android.app.Application
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import kotlinx.coroutines.runBlocking
import org.julakali.chargeahead.android.car.CarTestGraph.Companion.settle
import org.julakali.chargeahead.android.phone.R
import org.julakali.chargeahead.shared.domain.CarDataKind
import org.julakali.chargeahead.shared.domain.CarDataPoint
import org.julakali.chargeahead.shared.domain.CarDataStatus
import org.julakali.chargeahead.shared.domain.CarDiagnosticsRepository
import org.julakali.chargeahead.shared.domain.EnergyState
import org.julakali.chargeahead.shared.domain.SoCSourceKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class CarDebugScreenTest {

    private lateinit var graph: CarTestGraph

    @Before
    fun startGraph() {
        graph = CarTestGraph(energy = EnergyState(42.0, SoCSourceKind.CAR_HARDWARE, 0L))
    }

    @After
    fun stopGraph() = graph.close()

    private fun string(id: Int, vararg args: Any) = graph.carContext.getString(id, *args)

    @Test
    fun `the car's own charge reading, the fix and the recorded points are rows`() {
        runBlocking {
            GlobalContext.get().get<CarDiagnosticsRepository>()
                .recordCarDataPoint(CarDataPoint(CarDataKind.SPEED, CarDataStatus.AVAILABLE, "97 km/h", observedAtMillis = 0L))
        }
        val screen = CarDebugScreen(graph.carContext, graph.session)
        settle()

        val rows = (screen.onGetTemplate() as ListTemplate).singleList!!.items.map { it as Row }

        assertEquals(string(R.string.car_debug_energy), rows[0].title.toString())
        assertTrue(rows[0].texts.single().toString().startsWith(string(R.string.car_debug_energy_value, 42, string(R.string.car_debug_source_car))))
        assertEquals(string(R.string.car_debug_fix), rows[1].title.toString())
        assertTrue(rows[1].texts.single().toString().startsWith("53.5500, 9.9900"))
        val speed = rows.first { it.title.toString() == string(R.string.cardata_kind_speed) }
        assertTrue(speed.texts.single().toString().startsWith("97 km/h"))
        val battery = rows.first { it.title.toString() == string(R.string.cardata_kind_battery) }
        assertEquals(string(R.string.cardata_never), battery.texts.single().toString())
    }
}
