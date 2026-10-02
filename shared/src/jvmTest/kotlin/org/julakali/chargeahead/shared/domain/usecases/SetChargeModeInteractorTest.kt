package org.julakali.chargeahead.shared.domain.usecases

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.julakali.chargeahead.shared.domain.ChargeFilters
import org.julakali.chargeahead.shared.domain.ChargeMode
import org.julakali.chargeahead.shared.domain.NetworkPreferences
import org.julakali.chargeahead.shared.settings.DataStorePreferencesRepository
import org.julakali.chargeahead.shared.settings.InMemoryPreferencesDataStore
import kotlin.test.Test
import kotlin.test.assertEquals

class SetChargeModeInteractorTest {

    private val preferences = DataStorePreferencesRepository(InMemoryPreferencesDataStore())
    private val setMode = SetChargeModeInteractor(preferences, UpdateChargeFiltersInteractor(preferences), UpdateNetworksInteractor(preferences))

    private suspend fun mode() = ChargeMode.of(preferences.chargeFilters.first(), preferences.networks.first())

    @Test
    fun `ac mode goes on and off again`() = runBlocking<Unit> {
        setMode(SetChargeModeInteractor.Params(ChargeMode.AC)).getOrThrow()
        assertEquals(ChargeMode.AC, mode())

        setMode(SetChargeModeInteractor.Params(ChargeMode.NORMAL)).getOrThrow()
        assertEquals(ChargeMode.NORMAL, mode())
    }

    @Test
    fun `stoebermodus goes on and off again`() = runBlocking<Unit> {
        setMode(SetChargeModeInteractor.Params(ChargeMode.BROWSE)).getOrThrow()
        assertEquals(ChargeMode.BROWSE, mode())

        setMode(SetChargeModeInteractor.Params(ChargeMode.NORMAL)).getOrThrow()
        assertEquals(ChargeMode.NORMAL, mode())
    }

    /** The modes are a view on two settings; switching one must not clear the other's. */
    @Test
    fun `the picked networks and the power filter survive every mode`() = runBlocking<Unit> {
        preferences.setChargeFilters(ChargeFilters(minPowerKw = 300.0))
        preferences.setNetworks(NetworkPreferences(onlyPreferred = true, preferredOperators = setOf("ionity")))

        for (target in listOf(ChargeMode.AC, ChargeMode.NORMAL, ChargeMode.BROWSE, ChargeMode.NORMAL)) {
            setMode(SetChargeModeInteractor.Params(target)).getOrThrow()
            assertEquals(300.0, preferences.chargeFilters.first().minPowerKw)
            assertEquals(setOf("ionity"), preferences.networks.first().preferredOperators)
        }
    }

    /** Both on can only come from an older preference file; leaving to normal has to clear both. */
    @Test
    fun `leaving normal clears both settings at once`() = runBlocking<Unit> {
        preferences.setChargeFilters(ChargeFilters(slowMode = true))
        preferences.setNetworks(NetworkPreferences(onlyPreferred = false))

        setMode(SetChargeModeInteractor.Params(ChargeMode.NORMAL)).getOrThrow()

        assertEquals(false, preferences.chargeFilters.first().slowMode)
        assertEquals(true, preferences.networks.first().onlyPreferred)
    }
}
