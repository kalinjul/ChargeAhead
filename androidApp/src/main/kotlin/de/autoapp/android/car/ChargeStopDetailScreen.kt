package de.autoapp.android.car

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import de.autoapp.android.R
import de.autoapp.shared.ChargeStopFormatter
import de.autoapp.shared.domain.ChargeStop

/**
 * A single charging stop in the detail the list doesn't have: all
 * connectors instead of just the strongest, address, source of the data.
 *
 * And the button this screen exists for in the first place: **start
 * navigation.** Up to this point the app could show a charging site, but not lead to it.
 */
class ChargeStopDetailScreen(
    carContext: CarContext,
    private val stop: ChargeStop,
) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val paneLimit = carContext
            .getCarService(ConstraintManager::class.java)
            .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_PANE)

        val pane = Pane.Builder()
        // Numbers that matter while driving come first, extras after — the
        // host dictates the row count, and whatever comes last gets dropped.
        rows().take(paneLimit).forEach { pane.addRow(it) }
        pane.addAction(
            Action.Builder()
                .setTitle(carContext.getString(R.string.car_detail_navigate))
                .setOnClickListener(::startNavigation)
                .build(),
        )

        return PaneTemplate.Builder(pane.build())
            .setHeader(
                Header.Builder()
                    .setTitle(stop.site.name)
                    .setStartHeaderAction(Action.BACK)
                    .build(),
            )
            .build()
    }

    private fun rows(): List<Row> = buildList {
        add(
            Row.Builder()
                .setTitle(ChargeStopFormatter.primaryLine(stop))
                .addText(ChargeStopFormatter.secondaryLine(stop))
                .build(),
        )

        val connectorLines = ChargeStopFormatter.connectorLines(stop)
        add(
            Row.Builder()
                .setTitle(carContext.getString(R.string.car_detail_connectors))
                .apply {
                    if (connectorLines.isEmpty()) {
                        addText(carContext.getString(R.string.car_detail_unknown_connectors))
                    } else {
                        // Row accepts at most two lines of text; the connector
                        // lines are already sorted by power, so the weakest ones get dropped.
                        connectorLines.take(2).forEach { addText(it) }
                    }
                }
                .build(),
        )

        stop.site.operator?.let { operator ->
            add(Row.Builder().setTitle(operator).build())
        }

        ChargeStopFormatter.addressLine(stop)?.let { address ->
            add(Row.Builder().setTitle(address).build())
        }

        ChargeStopFormatter.sourceLine(stop)?.let { source ->
            add(
                Row.Builder()
                    .setTitle(carContext.getString(R.string.car_detail_source))
                    .addText(source)
                    .build(),
            )
        }
    }

    /**
     * Hands off to the host's navigation app.
     *
     * `geo:` with coordinates **and** name in parentheses: the coordinates
     * lead exactly there, the name appears to the driver as the destination.
     * Name only would be a search with an uncertain outcome; coordinates
     * only would show up nameless in navigation.
     */
    private fun startNavigation() {
        val position = stop.site.position
        val label = Uri.encode(stop.site.name)
        val uri = Uri.parse("geo:${position.lat},${position.lon}?q=${position.lat},${position.lon}($label)")

        try {
            carContext.startCarApp(Intent(CarContext.ACTION_NAVIGATE, uri))
        } catch (notFound: ActivityNotFoundException) {
            // Without a navigation app, the screen stays put instead of
            // silently doing nothing.
            CarToast.makeText(
                carContext,
                carContext.getString(R.string.car_detail_no_navigation),
                CarToast.LENGTH_LONG,
            ).show()
        }
    }
}
