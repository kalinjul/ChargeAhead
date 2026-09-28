package org.julakali.chargeahead.android.phone

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.get
import androidx.navigation3.runtime.metadata
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import org.julakali.chargeahead.android.phone.components.AppSheet

/**
 * Draws entries marked with [sheet] in an [AppSheet] above whatever sits below
 * them on the back stack, so a sheet is an ordinary destination.
 *
 * Back for such an entry does not come from the `NavDisplay` — an overlay is
 * not its current scene — but from the sheet's own window, which dismisses it
 * and lands on [onBack].
 */
class SheetSceneStrategy<T : Any> : SceneStrategy<T> {

    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        val entry = entries.lastOrNull() ?: return null
        entry.metadata[SheetKey] ?: return null
        return SheetScene(entry, entries.dropLast(1), onBack)
    }

    companion object {
        private object SheetKey : NavMetadataKey<Unit>

        fun sheet(): Map<String, Any> = metadata { put(SheetKey, Unit) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
private class SheetScene<T : Any>(
    private val entry: NavEntry<T>,
    override val previousEntries: List<NavEntry<T>>,
    private val onBack: () -> Unit,
) : OverlayScene<T> {

    override val key: Any = entry.contentKey

    override val entries: List<NavEntry<T>> = listOf(entry)

    override val overlaidEntries: List<NavEntry<T>> = previousEntries.takeLast(1)

    private lateinit var sheetState: SheetState

    override val content: @Composable () -> Unit = {
        sheetState = rememberModalBottomSheetState()
        AppSheet(onDismissRequest = onBack, sheetState = sheetState) { entry.Content() }
    }

    // The pop drops this scene from composition, so the slide-out has to finish here.
    override suspend fun onRemove() {
        if (::sheetState.isInitialized) sheetState.hide()
    }

    override fun equals(other: Any?): Boolean =
        other is SheetScene<*> && key == other.key && entry == other.entry && previousEntries == other.previousEntries

    override fun hashCode(): Int = (key.hashCode() * 31 + entry.hashCode()) * 31 + previousEntries.hashCode()
}
