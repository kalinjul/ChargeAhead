package org.julakali.chargeahead.android.car

import androidx.annotation.StringRes
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
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
 * The best fast chargers around the current position, cheap and near first.
 * Tapping one hands it straight to the navigation app.
 */
class ChargeNowScreen(
    carContext: CarContext,
    session: Scope,
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
                .setHeader(header(withRefresh = true))
                .build()
        }

        // The row count is dictated by the host.
        val contentLimit = carContext
            .getCarService(ConstraintManager::class.java)
            .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)

        val itemList = ItemList.Builder()
        // The relax notice costs one of the rows.
        val relaxedRow = relaxedRow(current)
        val roomForCandidates = if (relaxedRow == null) contentLimit else contentLimit - 1
        candidates.take(roomForCandidates).forEach { itemList.addItem(candidateRow(it)) }
        relaxedRow?.let { itemList.addItem(it) }

        return ListTemplate.Builder()
            .setSingleList(itemList.build())
            .setHeader(header(withRefresh = true))
            .build()
    }

    private fun loadingTemplate(@StringRes waitingText: Int): Template =
        ListTemplate.Builder()
            .setLoading(true)
            .setHeader(header(withRefresh = false, subtitle = carContext.getString(waitingText)))
            .build()

    private fun candidateRow(candidate: ChargeNowCandidate): Row = Row.Builder()
        .setTitle(candidate.site.name)
        .addText(ChargeStopFormatter.chargeNowPrimaryLine(candidate))
        .addText(ChargeStopFormatter.chargeNowSecondaryLine(candidate))
        // IMAGE_TYPE_ICON: only tintable icons get recolored by the host.
        .setImage(icon(R.drawable.ic_charge_pin), Row.IMAGE_TYPE_ICON)
        .setOnClickListener { navigateTo(carContext, candidate.site.name, candidate.site.position) }
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

    private fun header(withRefresh: Boolean, subtitle: String? = null): Header {
        val builder = Header.Builder()
            .setTitle(titleText(subtitle))
            .setStartHeaderAction(Action.BACK)

        if (withRefresh) {
            builder.addEndHeaderAction(
                Action.Builder()
                    .setIcon(icon(R.drawable.ic_refresh))
                    .setOnClickListener(viewModel::onRefresh)
                    .build(),
            )
        }
        return builder.build()
    }

    private fun titleText(subtitle: String?): String {
        val base = carContext.getString(R.string.car_home_charge_now)
        return subtitle?.let { "$base · $it" } ?: base
    }
}
