package org.julakali.chargeahead.shared.domain.usecases

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import org.julakali.chargeahead.shared.domain.AppCoroutineDispatchers
import org.julakali.chargeahead.shared.domain.NetworkRepository
import org.julakali.chargeahead.shared.domain.OperatorKey
import org.julakali.chargeahead.shared.domain.PreferencesRepository
import org.julakali.chargeahead.shared.domain.SelectableNetwork
import org.julakali.chargeahead.shared.domain.SubjectInteractor

/**
 * The network picker's rows: every listed network plus the selected ones the
 * list no longer has, narrowed by the search query, in catalog order.
 */
class SelectableNetworksObserver(
    private val networkRepository: NetworkRepository,
    private val preferences: PreferencesRepository,
    private val dispatchers: AppCoroutineDispatchers,
) : SubjectInteractor<SelectableNetworksObserver.Params, List<SelectableNetwork>>() {

    data class Params(val query: String)

    override fun createObservable(params: Params): Flow<List<SelectableNetwork>> {
        val needle = OperatorKey.folded(params.query.trim())
        return combine(networkRepository.networks, preferences.networks) { known, stored ->
            stored.selectable(known).map { SelectableNetwork(it, OperatorKey.folded(it.name)) }
        }
            .map { rows -> if (needle.isEmpty()) rows else rows.filter { needle in it.folded } }
            .distinctUntilChanged()
            .flowOn(dispatchers.computation)
    }
}
