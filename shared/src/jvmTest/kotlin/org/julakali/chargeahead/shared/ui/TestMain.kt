package org.julakali.chargeahead.shared.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlin.reflect.KClass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout

/**
 * Dispatchers.Main for ViewModel tests. [tearDown] clears every [track]ed
 * ViewModel and waits for its scope before resetting Main: a coroutine still
 * running, such as `uiState`'s stop timeout, would otherwise touch Main from
 * another thread while the next test sets it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TestMain {

    private val store = ViewModelStore()
    private val scopes = mutableListOf<Job>()

    fun setUp() = Dispatchers.setMain(Dispatchers.Unconfined)

    fun <VM : ViewModel> track(viewModel: VM): VM {
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T = viewModel as T
        }
        ViewModelProvider.create(store, factory)["${scopes.size}", viewModel::class]
        scopes += viewModel.viewModelScope.coroutineContext.job
        return viewModel
    }

    fun tearDown() {
        store.clear()
        runBlocking { withTimeout(5_000L) { scopes.joinAll() } }
        Dispatchers.resetMain()
    }
}
