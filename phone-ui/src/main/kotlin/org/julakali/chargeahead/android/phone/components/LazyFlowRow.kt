package org.julakali.chargeahead.android.phone.components

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.lazy.layout.LazyLayout
import androidx.compose.foundation.lazy.layout.LazyLayoutItemProvider
import androidx.compose.foundation.lazy.layout.getDefaultLazyLayoutKey
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * A vertically scrolling flow row that composes only the lines it shows.
 *
 * `FlowRow` wraps items beautifully but composes every one of them, which is too
 * much for a few hundred. A `LazyColumn` of chunked `FlowRow`s composes lazily but
 * forces a line break at every chunk boundary, leaving ragged half-filled rows.
 * This lays every item out in one continuous flow and composes only the lines
 * inside the viewport.
 *
 * Items are measured against the container width, so none can be wider than it.
 * Each item must emit exactly one composable, or none at all.
 *
 * Give this a bounded height — with an unbounded one every line counts as visible
 * and it degrades to composing everything, just like a plain `FlowRow`.
 *
 * Line breaks depend on the container width and on the item count. An item-count change
 * sends the scroll position back to the top — for a searchable list that is what you want
 * anyway: new results start at the first line. A width-only change instead keeps the item
 * that was on top in view.
 */
@Composable
fun LazyFlowRow(
    modifier: Modifier = Modifier,
    state: LazyFlowRowState = rememberLazyFlowRowState(),
    horizontalSpacing: Dp = 0.dp,
    verticalSpacing: Dp = 0.dp,
    content: LazyFlowRowScope.() -> Unit,
) {
    val latestContent = rememberUpdatedState(content)
    // Referential, as LazyColumn does it: every rebuild counts as a change. The rebuilt
    // provider holds freshly-captured item lambdas, and what they draw can differ while
    // every key stays the same — a pill that just became selected — so comparing providers
    // by their keys would leave the old lambdas, and the old drawing, in place.
    val itemProvider = remember {
        val provider = derivedStateOf(referentialEqualityPolicy()) {
            FlowItemProvider(FlowScope().apply(latestContent.value).intervals)
        }
        provider::value
    }
    val overscroll = rememberOverscrollEffect()

    LazyLayout(
        itemProvider = itemProvider,
        modifier = modifier
            .clipToBounds()
            .overscroll(overscroll)
            .scrollable(state, Orientation.Vertical, overscrollEffect = overscroll),
    ) { constraints ->
        require(constraints.hasBoundedWidth) { "LazyFlowRow needs a bounded width to break lines at." }

        val itemCount = itemProvider().itemCount
        val width = constraints.maxWidth
        val viewport = if (constraints.hasBoundedHeight) constraints.maxHeight else Int.MAX_VALUE
        val horizontalGap = horizontalSpacing.roundToPx()
        val verticalGap = verticalSpacing.roundToPx()
        val itemConstraints = Constraints(maxWidth = width)
        val anchorItemIndex = state.discardCacheIfStale(width, itemCount)

        // Both caches live for this measure pass only, so no item is composed twice.
        val placeables = HashMap<Int, Placeable?>()
        fun placeableAt(index: Int): Placeable? {
            if (index in placeables) return placeables[index]
            val measurables = compose(index)
            require(measurables.size <= 1) {
                "A LazyFlowRow item must emit at most one composable, but item $index emitted ${measurables.size}."
            }
            return measurables.firstOrNull()?.measure(itemConstraints).also { placeables[index] = it }
        }

        val lines = HashMap<Int, FlowLine>()
        fun lineAt(lineIndex: Int): FlowLine = lines.getOrPut(lineIndex) {
            val start = state.lineStarts[lineIndex]
            val row = mutableListOf<Placeable>()
            var index = start
            var used = 0
            var height = 0
            while (index < itemCount) {
                val placeable = placeableAt(index)
                if (placeable != null) {
                    val advance = if (row.isEmpty()) placeable.width else horizontalGap + placeable.width
                    if (row.isNotEmpty() && used + advance > width) break
                    used += advance
                    height = maxOf(height, placeable.height)
                    row += placeable
                }
                index++
            }
            // A later boundary already cached from a previous pass may no longer match what
            // this line actually wraps to — the list can reorder at the same width and item
            // count (e.g. NetworksViewModel re-freezing display order after a search clears).
            // That boundary, and everything cached past it, was computed from a packing that
            // no longer holds, so drop it and let it be rediscovered from the live content.
            if (lineIndex + 1 <= state.lineStarts.lastIndex && state.lineStarts[lineIndex + 1] != index) {
                while (state.lineStarts.size > lineIndex + 1) state.lineStarts.removeAt(state.lineStarts.lastIndex)
            }
            // Remember where the next line starts, so scrolling back can find this one again.
            if (lineIndex == state.lineStarts.lastIndex && index < itemCount) state.lineStarts.add(index)
            FlowLine(endIndex = index, placeables = row, height = height)
        }

        var line = state.firstVisibleLine
        var offset = state.firstVisibleLineScrollOffset - state.pendingScroll.roundToInt()

        // Width changed but the list didn't: find the line the previously-top item now
        // falls on, so the viewport keeps showing roughly the same content instead of
        // resetting to the very first line.
        if (anchorItemIndex != null && anchorItemIndex > 0) {
            while (true) {
                val current = lineAt(line)
                if (current.endIndex > anchorItemIndex || current.endIndex >= itemCount) break
                line++
            }
        }

        // Scrolled back above the anchor line: walk to earlier lines until it fits again.
        while (offset < 0 && line > 0) {
            line--
            offset += lineAt(line).height + verticalGap
        }
        if (offset < 0) offset = 0

        // Scrolled forward past whole lines: move the anchor down over them.
        while (true) {
            val current = lineAt(line)
            if (current.endIndex >= itemCount) break
            val advance = current.height + verticalGap
            if (offset < advance) break
            offset -= advance
            line++
        }

        val visible = mutableListOf<FlowLine>()
        var contentBottom = 0
        var atEnd = false
        fun fillViewport() {
            visible.clear()
            var bottom = -offset
            var lineIndex = line
            while (bottom < viewport) {
                val current = lineAt(lineIndex)
                visible += current
                bottom += current.height + verticalGap
                if (current.endIndex >= itemCount) break
                lineIndex++
            }
            contentBottom = bottom - verticalGap
            atEnd = (visible.lastOrNull()?.endIndex ?: itemCount) >= itemCount
        }
        fillViewport()

        // Scrolled past the last line: pull the content back down and lay it out again.
        // Only with a real viewport to align against — an unbounded one shows everything anyway.
        if (constraints.hasBoundedHeight && atEnd && contentBottom < viewport && (line > 0 || offset > 0)) {
            offset -= viewport - contentBottom
            while (offset < 0 && line > 0) {
                line--
                offset += lineAt(line).height + verticalGap
            }
            if (offset < 0) offset = 0
            fillViewport()
        }
        state.onMeasured(
            firstVisibleLine = line,
            firstVisibleLineScrollOffset = offset,
            canScrollBackward = line > 0 || offset > 0,
            canScrollForward = !atEnd || contentBottom > viewport,
        )

        layout(width, constraints.constrainHeight(maxOf(contentBottom, 0))) {
            var y = -offset
            visible.forEach { current ->
                var x = 0
                current.placeables.forEach { placeable ->
                    // Shorter items ride the middle of their line.
                    placeable.place(x, y + (current.height - placeable.height) / 2)
                    x += placeable.width + horizontalGap
                }
                y += current.height + verticalGap
            }
        }
    }
}

/** One wrapped line of a [LazyFlowRow], ending just before [endIndex]. */
private class FlowLine(val endIndex: Int, val placeables: List<Placeable>, val height: Int)

/** Remembers the scroll position of a [LazyFlowRow]. */
@Composable
fun rememberLazyFlowRowState(): LazyFlowRowState = remember { LazyFlowRowState() }

/**
 * Where a [LazyFlowRow] is scrolled to: the line at the top of the viewport and
 * how far that line has moved out of sight.
 */
@Stable
class LazyFlowRowState : ScrollableState {

    /** The line drawn at the top of the viewport. */
    var firstVisibleLine by mutableIntStateOf(0)
        private set

    /** How much of [firstVisibleLine] sits above the viewport, in pixels. */
    var firstVisibleLineScrollOffset by mutableIntStateOf(0)
        private set

    override var canScrollForward by mutableStateOf(false)
        private set

    override var canScrollBackward by mutableStateOf(false)
        private set

    /** Scroll gathered since the last measure pass. Read while measuring, so writing it re-measures. */
    internal var pendingScroll by mutableFloatStateOf(0f)
        private set

    /**
     * The first item index of every line discovered so far, contiguous from line 0.
     *
     * Greedy wrapping only runs forwards, so scrolling back up has no way to find
     * where the previous line began — this remembers it. Not snapshot state: it is
     * filled in during the measure pass and never read from composition.
     */
    internal val lineStarts = mutableListOf(0)
    private var cachedWidth = -1
    private var cachedItemCount = -1

    private val scrolling = ScrollableState { delta ->
        if (delta < 0f && !canScrollForward) return@ScrollableState 0f
        if (delta > 0f && !canScrollBackward) return@ScrollableState 0f
        pendingScroll += delta
        delta
    }

    override suspend fun scroll(scrollPriority: MutatePriority, block: suspend ScrollScope.() -> Unit) =
        scrolling.scroll(scrollPriority, block)

    override fun dispatchRawDelta(delta: Float): Float = scrolling.dispatchRawDelta(delta)

    override val isScrollInProgress: Boolean get() = scrolling.isScrollInProgress

    /**
     * Line breaks hold only for one width and one list. A list-size change starts over at
     * the top — for a searchable list that's what you want anyway: new results start at the
     * first line. A width-only change (rotation, split-screen resize) instead returns the
     * item that was at the top of the viewport, so the caller can keep it in view rather than
     * silently jumping back to the top of a possibly long list.
     */
    internal fun discardCacheIfStale(width: Int, itemCount: Int): Int? {
        if (width == cachedWidth && itemCount == cachedItemCount) return null
        val anchorItemIndex = if (itemCount == cachedItemCount && cachedWidth != -1) {
            lineStarts.getOrNull(firstVisibleLine)
        } else {
            null
        }
        cachedWidth = width
        cachedItemCount = itemCount
        lineStarts.clear()
        lineStarts.add(0)
        firstVisibleLine = 0
        firstVisibleLineScrollOffset = 0
        pendingScroll = 0f
        return anchorItemIndex
    }

    /** Takes the anchor a measure pass settled on. Writes only real changes, so measuring can't loop. */
    internal fun onMeasured(
        firstVisibleLine: Int,
        firstVisibleLineScrollOffset: Int,
        canScrollBackward: Boolean,
        canScrollForward: Boolean,
    ) {
        if (this.firstVisibleLine != firstVisibleLine) this.firstVisibleLine = firstVisibleLine
        if (this.firstVisibleLineScrollOffset != firstVisibleLineScrollOffset) {
            this.firstVisibleLineScrollOffset = firstVisibleLineScrollOffset
        }
        if (this.canScrollBackward != canScrollBackward) this.canScrollBackward = canScrollBackward
        if (this.canScrollForward != canScrollForward) this.canScrollForward = canScrollForward
        if (pendingScroll != 0f) pendingScroll = 0f
    }
}

/** The content block of a [LazyFlowRow]. */
interface LazyFlowRowScope {
    /** A single item. */
    fun item(key: Any? = null, content: @Composable () -> Unit)

    /** One item per entry of [items]. A [key] keeps an item's state with it as the list changes. */
    fun <T> items(items: List<T>, key: ((item: T) -> Any)? = null, itemContent: @Composable (item: T) -> Unit)
}

/** A run of items sharing one content lambda, so `items` costs one object rather than one per entry. */
private class FlowInterval(val count: Int, val key: ((Int) -> Any)?, val item: @Composable (Int) -> Unit)

private class FlowScope : LazyFlowRowScope {
    val intervals = mutableListOf<FlowInterval>()

    override fun item(key: Any?, content: @Composable () -> Unit) {
        intervals += FlowInterval(count = 1, key = key?.let { only -> { _: Int -> only } }, item = { content() })
    }

    override fun <T> items(items: List<T>, key: ((item: T) -> Any)?, itemContent: @Composable (item: T) -> Unit) {
        intervals += FlowInterval(
            count = items.size,
            key = key?.let { factory -> { index: Int -> factory(items[index]) } },
            item = { index -> itemContent(items[index]) },
        )
    }
}

private class FlowItemProvider(private val intervals: List<FlowInterval>) : LazyLayoutItemProvider {

    override val itemCount: Int = intervals.sumOf { it.count }

    @Composable
    override fun Item(index: Int, key: Any) {
        var rest = index
        intervals.forEach { interval ->
            if (rest < interval.count) {
                interval.item(rest)
                return
            }
            rest -= interval.count
        }
    }

    override fun getKey(index: Int): Any {
        var rest = index
        intervals.forEach { interval ->
            if (rest < interval.count) return interval.key?.invoke(rest) ?: getDefaultLazyLayoutKey(index)
            rest -= interval.count
        }
        return getDefaultLazyLayoutKey(index)
    }
}
