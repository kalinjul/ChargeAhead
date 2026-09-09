package de.autoapp.shared.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class SectionSelectionTest {

    @Test
    fun toggling_entersAndLeavesSelectionMode_droppingPicks() {
        val entered = SectionSelection().toggled()
        assertEquals(SectionSelection(selecting = true), entered)

        val left = entered.picked(1).picked(3).toggled()
        assertEquals(SectionSelection(), left)
    }

    @Test
    fun picking_fillsThePair_thenRestartsOnAThirdPick() {
        val selection = SectionSelection(selecting = true)

        val one = selection.picked(0)
        assertEquals(SectionSelection(selecting = true, a = 0), one)

        val two = one.picked(2)
        assertEquals(SectionSelection(selecting = true, a = 0, b = 2), two)

        // A third pick starts a fresh pair — same cycle the screen had.
        assertEquals(SectionSelection(selecting = true, a = 1), two.picked(1))
    }

    @Test
    fun pickingTheSamePointTwice_doesNotPairItWithItself() {
        val one = SectionSelection(selecting = true).picked(2)
        assertEquals(one, one.picked(2))
    }
}
