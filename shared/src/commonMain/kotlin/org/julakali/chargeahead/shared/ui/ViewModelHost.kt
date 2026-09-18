package org.julakali.chargeahead.shared.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

/** Holds ViewModels for a host without a `ViewModelStoreOwner`: a car screen, the Swift bridge. */
class ViewModelHost {
    @PublishedApi
    internal val store = ViewModelStore()

    /** The host's [VM], created by [create] on first use. */
    inline fun <reified VM : ViewModel> get(noinline create: () -> VM): VM =
        ViewModelProvider.create(store, viewModelFactory { initializer { create() } })[VM::class]

    /** Ends every ViewModel, cancelling its `viewModelScope`. */
    fun clear() {
        store.clear()
    }
}
