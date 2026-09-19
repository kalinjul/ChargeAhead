package org.julakali.chargeahead.shared.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.julakali.chargeahead.shared.domain.DataSource
import org.julakali.chargeahead.shared.domain.DataSourceDirectory
import org.julakali.chargeahead.shared.domain.LoadDataSources
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class LicensesViewModelTest {

    @BeforeTest
    fun setUpMainDispatcher() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @AfterTest
    fun tearDownMainDispatcher() {
        Dispatchers.resetMain()
    }

    private val osm = DataSource("osm", "OpenStreetMap", url = null, datasets = emptyList())

    @Test
    fun `the sources load on open`() = runBlocking<Unit> {
        val vm = LicensesViewModel(LoadDataSources(directory { listOf(osm) }))

        val state = vm.uiState.await { it.dataSources is DataSourcesState.Loaded }

        assertEquals(DataSourcesState.Loaded(listOf(osm)), state.dataSources)
    }

    @Test
    fun `a failed load can be retried`() = runBlocking<Unit> {
        var fail = true
        val vm = LicensesViewModel(
            LoadDataSources(directory { if (fail) error("offline") else listOf(osm) }),
        )
        vm.uiState.await { it.dataSources == DataSourcesState.Failed }

        fail = false
        vm.onRetry()

        vm.uiState.await { it.dataSources == DataSourcesState.Loaded(listOf(osm)) }
    }

    private fun directory(load: () -> List<DataSource>) = object : DataSourceDirectory {
        override suspend fun dataSources(): List<DataSource> = load()
    }

    private suspend fun <T> StateFlow<T>.await(matching: (T) -> Boolean): T =
        withTimeout(5_000) { first(matching) }
}
