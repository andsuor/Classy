package com.themoon.y1.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RowFocusTest {
    private val all = { _: Int -> true }

    private fun next(count: Int, current: Int, delta: Int, loop: Boolean, selectable: (Int) -> Boolean = all) =
        RowFocus.next(count, current, delta, loop, selectable)

    @Test
    fun stepsOneRowInTheWheelDirection() {
        assertEquals(3, next(5, 2, 1, loop = false))
        assertEquals(1, next(5, 2, -1, loop = false))
    }

    @Test
    fun stopsAtTheEndsWhenLoopIsOff() {
        assertNull(next(5, 4, 1, loop = false))
        assertNull(next(5, 0, -1, loop = false))
    }

    @Test
    fun goesRoundAtTheEndsWhenLoopIsOn() {
        assertEquals(0, next(5, 4, 1, loop = true))
        assertEquals(4, next(5, 0, -1, loop = true))
    }

    @Test
    fun skipsRowsThatCannotBeSelected() {
        // Rows 0 and 3 are group headers, row 5 is an info row
        val rows = { i: Int -> i != 0 && i != 3 && i != 5 }
        assertEquals(4, next(6, 2, 1, loop = false, selectable = rows))
        assertEquals(2, next(6, 4, -1, loop = false, selectable = rows))
        assertNull(next(6, 4, 1, loop = false, selectable = rows))
        assertNull(next(6, 1, -1, loop = false, selectable = rows))
        assertEquals(1, next(6, 4, 1, loop = true, selectable = rows))
        assertEquals(4, next(6, 1, -1, loop = true, selectable = rows))
    }

    @Test
    fun selectsTheFirstSelectableRowWhenNoRowIsSelected() {
        val rows = { i: Int -> i >= 2 }
        assertEquals(2, next(5, -1, 1, loop = false, selectable = rows))
        assertEquals(2, next(5, -1, -1, loop = true, selectable = rows))
    }

    @Test
    fun staysWhenNoOtherRowCanBeSelected() {
        assertNull(next(1, 0, 1, loop = true))
        assertNull(next(3, 1, 1, loop = true) { it == 1 })
        assertNull(next(0, -1, 1, loop = true))
        assertNull(next(3, -1, 1, loop = true) { false })
    }

    // A list 0..290 px showing rows 10..14, 60 px tall with a 1 px divider; row 14 is cut by the bottom edge.
    private val shown = mapOf(10 to intArrayOf(0, 60), 11 to intArrayOf(61, 121), 12 to intArrayOf(122, 182),
        13 to intArrayOf(183, 243), 14 to intArrayOf(244, 304))

    private fun scroll(position: Int, fade: Int = 0, count: Int = 100) =
        RowFocus.scrollDelta(position, 10, 14, count, { shown[it] }, 1, 0, 290, fade)

    @Test
    fun aRowOnTheScreenNeedsNoScroll() {
        assertEquals(0, scroll(12))
    }

    @Test
    fun aRowTheEdgeCutsScrollsUpJustEnough() {
        assertEquals(14, scroll(14))
    }

    @Test
    fun theRowBelowTheScreenScrollsInOneRowAtTheBottom() {
        // Row 15 comes at 305..365, so the list moves 75 px and row 15 ends at the bottom edge
        assertEquals(75, scroll(15))
    }

    @Test
    fun theRowAboveTheScreenScrollsInOneRowAtTheTop() {
        // Row 9 comes at -61..-1
        assertEquals(-61, scroll(9))
    }

    @Test
    fun withFadeTheRowStaysOneFadeFromTheEdge() {
        assertEquals(75 + 40, scroll(15, fade = 40))
        assertEquals(-61 - 40, scroll(9, fade = 40))
    }

    @Test
    fun theLastRowGoesToTheEdgeEvenWithFade() {
        assertEquals(75, scroll(15, fade = 40, count = 16))
    }

    @Test
    fun aRowFartherAwayIsNotAOneRowScroll() {
        assertNull(scroll(16))
        assertNull(scroll(8))
        assertNull(scroll(0))
    }
}
