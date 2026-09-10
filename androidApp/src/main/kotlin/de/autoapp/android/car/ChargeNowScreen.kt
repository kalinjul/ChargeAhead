package de.autoapp.android.car

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
import de.autoapp.android.R
import de.autoapp.shared.ChargeStopFormatter
import de.autoapp.shared.ChargeStopsFeature
import de.autoapp.shared.core.ChargeNowCandidate
import de.autoapp.shared.core.ChargeNowResult
import de.autoapp.shared.core.RelaxedFilter
import de.autoapp.shared.domain.Fix
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The best fast chargers around the current position, cheap and near first.
 * Tapping one hands it straight to the navigation app.
 */
class ChargeNowScreen(
    carContext: CarContext,
    private val feature: ChargeStopsFeature,
) : Screen(carContext) {

    // onGetTemplate() is synchronous and therefore only reads the last
    // remembered state; changes are picked up via invalidate().
    private var result: ChargeNowResult? = null

    init {
        lifecycleScope.launch {
            load(feature.currentFix.filterNotNull().first())
        }
    }

    private suspend fun load(fix: Fix) {
        result = null
        invalidate()
        result = feature.planning?.chargeNow(fix.position)
        invalidate()
    }

    override fun onGetTemplate(): Template {
        val current = result ?: return loadingTemplate()

        val candidates = current.candidates + current.more
        if (candidates.isEmpty()) {
            return MessageTemplate.Builder(carContext.getString(R.string.car_now_empty))
                .setHeader(header(withRefresh = true))
                .build()
        }

        // The row count is dictated by the host, not the app (AGENTS.md).
        val contentLimit = carContext
            .getCarService(ConstraintManager::class.java)
            .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)

        val itemList = ItemList.Builder()
        // The relax notice costs one of the precious rows — deliberately: a
        // list that silently ignores the driver's filters would be worse.
        val relaxedRow = relaxedRow(current)
        val roomForCandidates = if (relaxedRow == null) contentLimit else contentLimit - 1
        candidates.take(roomForCandidates).forEach { itemList.addItem(candidateRow(it)) }
        relaxedRow?.let { itemList.addItem(it) }

        return ListTemplate.Builder()
            .setSingleList(itemList.build())
            .setHeader(header(withRefresh = true))
            .build()
    }

    private fun loadingTemplate(): Template {
        val waitingText = if (feature.currentFix.value == null) {
            R.string.car_waiting_for_location
        } else {
            R.string.car_now_loading
        }
        return ListTemplate.Builder()
            .setLoading(true)
            .setHeader(header(withRefresh = false, subtitle = carContext.getString(waitingText)))
            .build()
    }

    private fun candidateRow(candidate: ChargeNowCandidate): Row = Row.Builder()
        .setTitle(candidate.site.name)
        .addText(ChargeStopFormatter.chargeNowPrimaryLine(candidate))
        .addText(ChargeStopFormatter.chargeNowSecondaryLine(candidate))
        // IMAGE_TYPE_ICON: only icons declared tintable get recolored by the
        // host — untinted ones stay black on a dark theme.
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
                    .setOnClickListener {
                        lifecycleScope.launch { feature.currentFix.value?.let { load(it) } }
                    }
                    .build(),
            )
        }
        return builder.build()
    }

    private fun titleText(subtitle: String?): String {
        val base = carContext.getString(R.string.car_home_charge_now)
        val withSubtitle = subtitle?.let { "$base · $it" } ?: base
        return if (feature.currentState.isDemo) {
            carContext.getString(R.string.car_title_suffix_demo, withSubtitle)
        } else {
            withSubtitle
        }
    }
}
