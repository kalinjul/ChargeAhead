package org.julakali.chargeahead.android.car

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Header
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.lifecycleScope
import org.julakali.chargeahead.android.phone.R
import org.julakali.chargeahead.shared.ChargeStopFormatter
import org.julakali.chargeahead.shared.domain.ChargeSite
import org.julakali.chargeahead.shared.domain.ChargeStop
import org.julakali.chargeahead.shared.domain.LiveConnectorGroup
import org.julakali.chargeahead.shared.domain.Reachability
import org.julakali.chargeahead.shared.domain.PlannedStop
import org.julakali.chargeahead.shared.ui.car.CarSiteDetailViewModel
import org.koin.core.parameter.parametersOf
import org.koin.core.scope.Scope
import kotlinx.coroutines.launch

/**
 * One charging site: where it is, how far, what it offers right now, and
 * the hand-off to navigation. For a planned [stop] the charge at it too.
 */
class SiteDetailScreen(
    carContext: CarContext,
    session: Scope,
    private val site: ChargeSite,
    private val stop: PlannedStop? = null,
) : Screen(carContext) {

    private val viewModel = screenViewModel { session.get<CarSiteDetailViewModel> { parametersOf(site) } }

    init {
        // onGetTemplate() is synchronous; changes are picked up via invalidate().
        lifecycleScope.launch { viewModel.uiState.collect { invalidate() } }
    }

    override fun onGetTemplate(): Template {
        val state = viewModel.uiState.value
        // The pane's row count is dictated by the host.
        val rowLimit = carContext
            .getCarService(ConstraintManager::class.java)
            .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_PANE)

        val pane = Pane.Builder()
        rows(state.distanceKm, state.live).take(rowLimit).forEach(pane::addRow)
        pane.setImage(paneLogo())
        pane.addAction(
            Action.Builder()
                .setTitle(carContext.getString(R.string.car_detail_navigate))
                .setBackgroundColor(CarColor.PRIMARY)
                .setOnClickListener { navigateTo(carContext, site.name, site.position) }
                .build(),
        )
        pane.addAction(
            Action.Builder()
                .setTitle(carContext.getString(R.string.car_detail_back))
                .setOnClickListener { screenManager.pop() }
                .build(),
        )

        return PaneTemplate.Builder(pane.build())
            .setHeader(
                Header.Builder()
                    .setTitle(site.name)
                    .setStartHeaderAction(Action.BACK)
                    .build(),
            )
            .build()
    }

    /** Address, distance with the charge, the charge points (live when known). The title names the site. */
    private fun rows(distanceKm: Double?, live: List<LiveConnectorGroup>?): List<Row> {
        val rows = mutableListOf<Row>()
        ChargeStopFormatter.addressLine(site)?.let { rows += Row.Builder().setTitle(it).build() }

        val charge = stop?.let {
            carContext.getString(
                R.string.car_detail_charge,
                it.arrivalSocPercent.toInt(),
                it.departureSocPercent.toInt(),
                ChargeStopFormatter.minutesLabel(it.chargeMinutes),
            )
        } ?: ChargeStopFormatter.powerKwLabel(strongestPowerKw())
        rows += Row.Builder()
            .apply { if (distanceKm != null) setTitle(distanceLine(distanceKm, charge)) else setTitle(charge) }
            .build()

        val connectorLines = if (live != null) {
            ChargeStopFormatter.liveConnectorLines(live)
        } else {
            ChargeStopFormatter.connectorLines(ChargeStop(site, distanceKm ?: 0.0, Reachability.UNKNOWN, socOnArrivalPercent = null))
        }
        connectorLines.firstOrNull()?.let { first ->
            rows += Row.Builder().setTitle(first).apply { connectorLines.drop(1).take(2).forEach(::addText) }.build()
        }

        return rows
    }

    /**
     * The stacked logo as pixels, not a resource id: the host caches icons by
     * id across installs and once served a stale render of a renamed
     * drawable here, and it mangled the vector before that. Scaled down,
     * because the pixels cross the binder with every template.
     */
    private fun paneLogo(): CarIcon {
        val full = BitmapFactory.decodeResource(carContext.resources, R.drawable.logo_powertrip_stacked)
        val scaled = Bitmap.createScaledBitmap(full, PANE_LOGO_PX, PANE_LOGO_PX, true)
        return CarIcon.Builder(IconCompat.createWithBitmap(scaled)).build()
    }

    private fun strongestPowerKw(): Double = stop?.maxPowerKw ?: (site.connectors.maxOfOrNull { it.maxPowerKw } ?: 0.0)
}

private const val PANE_LOGO_PX = 256
