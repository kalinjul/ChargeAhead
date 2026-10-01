package org.julakali.chargeahead.android.car

import androidx.annotation.StringRes
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.PlaceListMapTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import org.julakali.chargeahead.android.phone.R
import org.julakali.chargeahead.shared.ChargeStopFormatter
import org.julakali.chargeahead.shared.domain.ChargeNowCandidate
import org.julakali.chargeahead.shared.domain.ChargeNowResult
import org.julakali.chargeahead.shared.domain.RelaxedFilter
import org.julakali.chargeahead.shared.ui.ChargeNowUiState
import org.julakali.chargeahead.shared.ui.car.CarChargeNowViewModel
import org.koin.core.scope.Scope
import kotlinx.coroutines.launch

/**
 * The best fast chargers around the current position on the host's map,
 * numbered in ranking order, cheap and near first. A row opens the detail.
 */
class ChargeNowScreen(
    carContext: CarContext,
    private val session: Scope,
) : Screen(carContext) {

    private val viewModel = screenViewModel { session.get<CarChargeNowViewModel>() }

    init {
        // onGetTemplate() is synchronous; changes are picked up via invalidate().
        lifecycleScope.launch { viewModel.uiState.collect { invalidate() } }
    }

    override fun onGetTemplate(): Template {
        val current = when (val state = viewModel.uiState.value) {
            ChargeNowUiState.NoPosition -> return loadingTemplate(R.string.car_waiting_for_location)
            ChargeNowUiState.Loading -> return loadingTemplate(R.string.car_now_loading)
            is ChargeNowUiState.Ready -> state.result
        }

        val candidates = current.candidates + current.more
        if (candidates.isEmpty()) {
            return MessageTemplate.Builder(carContext.getString(R.string.car_now_empty))
                .setHeader(
                    Header.Builder()
                        .setTitle(title())
                        .setStartHeaderAction(Action.BACK)
                        .addEndHeaderAction(refreshAction())
                        .build(),
                )
                .build()
        }

        // The row count is dictated by the host.
        val contentLimit = carContext
            .getCarService(ConstraintManager::class.java)
            .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_PLACE_LIST)

        val itemList = ItemList.Builder()
        // The relax notice costs one of the rows.
        val relaxedRow = relaxedRow(current)
        val roomForCandidates = if (relaxedRow == null) contentLimit else contentLimit - 1
        candidates.take(roomForCandidates).forEachIndexed { index, candidate ->
            itemList.addItem(candidateRow(index + 1, candidate))
        }
        relaxedRow?.let { itemList.addItem(it) }

        return PlaceListMapTemplate.Builder()
            .setItemList(itemList.build())
            .setTitle(title())
            .setHeaderAction(Action.BACK)
            .setCurrentLocationEnabled(true)
            // The host's own refresh affordance, after the driver moved the map.
            .setOnContentRefreshListener(viewModel::onRefresh)
            .setActionStrip(ActionStrip.Builder().addAction(refreshAction()).build())
            .build()
    }

    private fun loadingTemplate(@StringRes waitingText: Int): Template =
        PlaceListMapTemplate.Builder()
            .setLoading(true)
            .setTitle("${title()} · ${carContext.getString(waitingText)}")
            .setHeaderAction(Action.BACK)
            .build()

    /** Distance and power first, the operator and its charge points below. */
    private fun candidateRow(ordinal: Int, candidate: ChargeNowCandidate): Row = Row.Builder()
        .setTitle(candidate.site.name)
        .addText(distanceLine(candidate.distanceKm, ChargeStopFormatter.powerKwLabel(candidate.maxPowerKw)))
        .addText(ChargeStopFormatter.chargeNowSecondaryLine(candidate))
        .setMetadata(placeMetadata(candidate.site.position, ordinal.toString()))
        .setBrowsable(true)
        .setOnClickListener { screenManager.push(SiteDetailScreen(carContext, session, candidate.site)) }
        .build()

    private fun relaxedRow(result: ChargeNowResult): Row? {
        if (result.relaxed.isEmpty()) return null

        val names = result.relaxed.joinToString(", ") { relaxed ->
            carContext.getString(
                when (relaxed) {
                    RelaxedFilter.MIN_POWER -> R.string.car_relax_power
                    RelaxedFilter.NETWORKS -> R.string.car_relax_networks
                    RelaxedFilter.MAX_DISTANCE -> R.string.car_relax_distance
                },
            )
        }
        return Row.Builder()
            .setTitle(carContext.getString(R.string.car_now_relaxed, names))
            .build()
    }

    private fun refreshAction(): Action = Action.Builder()
        .setIcon(icon(R.drawable.ic_refresh))
        .setOnClickListener(viewModel::onRefresh)
        .build()

    private fun title(): String = carContext.getString(R.string.car_home_charge_now)
}
