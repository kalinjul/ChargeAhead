package org.julakali.chargeahead.shared.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.julakali.chargeahead.shared.domain.DataSource
import org.julakali.chargeahead.shared.domain.usecases.LoadDataSourcesInteractor
import org.julakali.chargeahead.shared.domain.invoke

sealed interface DataSourcesState {
    data object Loading : DataSourcesState

    data class Loaded(val sources: List<DataSource>) : DataSourcesState

    data object Failed : DataSourcesState
}

data class LicensesUiState(
    val dataSources: DataSourcesState = DataSourcesState.Loading,
)

/** The licence page. The library list is generated into the app and needs no state here. */
class LicensesViewModel(
    private val loadDataSources: LoadDataSourcesInteractor,
) : ViewModel() {

    // A one-shot load has no upstream flow to combine.
    private val state = MutableStateFlow(LicensesUiState())
    val uiState: StateFlow<LicensesUiState> = state.asStateFlow()

    init {
        load()
    }

    fun onRetry() = load()

    private fun load() {
        state.value = LicensesUiState(DataSourcesState.Loading)
        viewModelScope.launch {
            val result = loadDataSources()
                .fold({ DataSourcesState.Loaded(it) }, { DataSourcesState.Failed })
            state.value = LicensesUiState(result)
        }
    }
}
