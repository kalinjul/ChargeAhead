package org.julakali.chargeahead.uitests

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * DataStore without a file, for tests. Unlike the real one, several stores
 * may share an instance, which is how the tests "restart the app".
 */
class InMemoryPreferencesDataStore(
    initial: Map<String, String> = emptyMap(),
) : DataStore<Preferences> {

    private val state = MutableStateFlow(
        mutablePreferencesOf().apply {
            initial.forEach { (key, value) -> this[stringPreferencesKey(key)] = value }
        }.toPreferences(),
    )
    private val mutex = Mutex()

    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        mutex.withLock { transform(state.value).also { state.value = it } }
}
