package org.julakali.chargeahead.uitests

import org.julakali.chargeahead.android.phone.stackPlacement
import org.junit.Assert.assertEquals
import org.junit.Test

/** Where each card of the endless stack sits, by its distance in pages from the card on top. */
class StackPlacementTest {

    private val width = 360f
    private val gap = 8f
    private val peek = 16f

    /** Where the card's centre ends up: its place in the pager plus the stack's pull. */
    private fun centre(offset: Float) = offset * (width + gap) + width / 2 + stackPlacement(offset, width, gap, peek).translationX

    @Test
    fun `the card on top sits where the pager put it`() {
        val top = stackPlacement(0f, width, gap, peek)
        assertEquals(1f, top.scale, 0.001f)
        assertEquals(0f, top.translationX, 0.001f)
        assertEquals(1f, top.alpha, 0.001f)
    }

    @Test
    fun `every card from the first place behind on waits in that same place`() {
        val behind = centre(1f)
        listOf(1.5f, 2f, 2.5f, 3f).forEach { assertEquals("offset $it", behind, centre(it), 0.01f) }
    }

    @Test
    fun `only the card behind the top one and the one coming up show, nothing further back`() {
        assertEquals(1f, stackPlacement(1.5f, width, gap, peek).alpha, 0.001f)
        assertEquals(0f, stackPlacement(2f, width, gap, peek).alpha, 0.001f)
        assertEquals(0f, stackPlacement(2.5f, width, gap, peek).alpha, 0.001f)
    }

    @Test
    fun `a card swiped past fades out where it goes`() {
        val gone = stackPlacement(-0.5f, width, gap, peek)
        assertEquals(0.5f, gone.alpha, 0.001f)
        assertEquals(0f, gone.translationX, 0.001f)
    }

    /** Drawn outside the card's outline only, the shadow can fade along with the card: nothing shows through. */
    @Test
    fun `the shadow fades with a card fading out and is there in full in the stack`() {
        assertEquals(1f, stackPlacement(0f, width, gap, peek).shadow, 0.001f)
        assertEquals(0.8f, stackPlacement(-0.2f, width, gap, peek).shadow, 0.001f)
        assertEquals(0.4f, stackPlacement(-0.6f, width, gap, peek).shadow, 0.001f)
        assertEquals(1f, stackPlacement(1f, width, gap, peek).shadow, 0.001f)
        assertEquals(0f, stackPlacement(2.5f, width, gap, peek).shadow, 0.001f)
    }
}
