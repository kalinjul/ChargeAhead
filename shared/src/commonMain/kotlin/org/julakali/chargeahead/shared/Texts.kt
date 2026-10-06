package org.julakali.chargeahead.shared

import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import org.julakali.chargeahead.shared.resources.Res
import org.julakali.chargeahead.shared.resources.allStringResources

/**
 * Strings for callers that can't suspend or compose: car templates, the formatter, Swift.
 * Blocking is what CMP's own stringResource does on Android and iOS; files are cached after the first read.
 */
object Texts {
    fun string(resource: StringResource, vararg args: Any): String =
        runBlocking { getString(resource, *args) }

    fun plural(resource: PluralStringResource, quantity: Int, vararg args: Any): String =
        runBlocking { getPluralString(resource, quantity, *args) }

    /** Swift's way in: the generated accessors are extension properties it can't reach comfortably. */
    fun byKey(key: String, args: List<Any> = emptyList()): String =
        string(requireNotNull(Res.allStringResources[key]) { "no string $key" }, *args.toTypedArray())
}
