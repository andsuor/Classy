package com.themoon.y1.navigation

import com.themoon.y1.AppPrefs
import com.themoon.y1.Prefs
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.AbsListView
import android.widget.ListView
import com.themoon.y1.views.ListEdges
import java.util.WeakHashMap

/**
 * One wheel step in a list of rows, the same for all row lists: the next selectable row (visible and focusable,
 * so headers and info rows are skipped) in the wheel direction. At an end of the list the step goes round to the
 * other end when Settings → Controls → Wheel Loop Scroll is On, else it stays. With no selected row, the first
 * selectable row is selected.
 */
object RowFocus {

    /** How long a list takes to glide one row. */
    private const val GLIDE_MS = 100

    /** The row each list is gliding to, focused when the glide ends ([settle]). */
    private val gliding = WeakHashMap<AbsListView, Int>()

    /** Settings → Controls → Wheel Loop Scroll (default On). */
    fun isLoopOn(context: Context): Boolean =
        Prefs.LOOP_SCROLL.get(AppPrefs.get(context))

    fun isSelectable(row: View?): Boolean = row != null && row.visibility == View.VISIBLE && row.isFocusable

    /** The row after one step from [current] (-1 = no row) in the direction of [delta], or null to stay. */
    fun next(count: Int, current: Int, delta: Int, loop: Boolean, selectable: (Int) -> Boolean): Int? {
        if (current !in 0 until count) return (0 until count).firstOrNull(selectable)
        val direction = if (delta < 0) -1 else 1
        for (n in 1 until count) {
            var i = current + direction * n
            if (i !in 0 until count) {
                if (!loop) return null
                i = i.mod(count)
            }
            if (selectable(i)) return i
        }
        return null
    }

    /**
     * Pixels to scroll a list so row [position] is fully in [viewTop]..[viewBottom], as an iPod list moves: one row
     * at a time, the row coming in at the edge. The list shows rows [first]..[last], [bounds] gives the top and
     * bottom of each of them, and rows are [divider] apart. With [fade] the row stays that far from an edge that has
     * more rows past it. Null when [position] is not on the screen or right next to it (the list jumps there).
     */
    fun scrollDelta(
        position: Int, first: Int, last: Int, count: Int, bounds: (Int) -> IntArray?,
        divider: Int, viewTop: Int, viewBottom: Int, fade: Int
    ): Int? {
        val b = when (position) {
            in first..last -> bounds(position)
            last + 1 -> bounds(last)?.let { val top = it[1] + divider; intArrayOf(top, top + it[1] - it[0]) }
            first - 1 -> bounds(first)?.let { val bottom = it[0] - divider; intArrayOf(bottom - (it[1] - it[0]), bottom) }
            else -> null
        } ?: return null
        return ListEdges.revealDelta(b[0], b[1], viewTop, viewBottom, fade, position > 0, position < count - 1)
    }

    /** Moves the focus one step in the rows of [list]. Returns the index of the new row, or null if it did not move. */
    fun step(list: ViewGroup, delta: Int, loop: Boolean = isLoopOn(list.context)): Int? {
        val current = (0 until list.childCount).firstOrNull { list.getChildAt(it)?.hasFocus() == true } ?: -1
        val i = next(list.childCount, current, delta, loop) { isSelectable(list.getChildAt(it)) } ?: return null
        list.getChildAt(i).requestFocus()
        return i
    }

    /**
     * The same step in an [AbsListView] (song lists): it has views only for the rows on the screen, so a disabled row
     * of the adapter is the row that cannot be selected. Returns the position of the new row, or null if it did not move.
     */
    fun step(list: AbsListView, delta: Int, loop: Boolean = isLoopOn(list.context)): Int? {
        settle(list)
        val focus = list.findFocus()
        val current = if (focus == null || focus === list) -1 else list.getPositionForView(focus)
        val adapter = list.adapter
        val i = next(list.count, current, delta, loop) { adapter?.isEnabled(it) != false } ?: return null
        showRow(list, i)
        return i
    }

    /**
     * Selects row [position] of [list]. A row that is not on the screen comes into the list first. With
     * List Edges = Fade the row stays clear of the fades ([ListEdges.focusRow]).
     */
    fun showRow(list: AbsListView, position: Int) {
        settle(list)
        val delta = glideDelta(list, position)
        if (delta == 0) {
            list.getChildAt(position - list.firstVisiblePosition)?.requestFocus()
            return
        }
        if (delta != null) {
            gliding[list] = position
            focusWhenShown(list, position)
            list.smoothScrollBy(delta, GLIDE_MS)
            list.postDelayed({ if (gliding[list] == position) settle(list) }, GLIDE_MS + 20L)
            return
        }
        if (list is ListView && ListEdges.focusRow(list, position)) return
        val first = list.firstVisiblePosition
        val row = if (position in first..list.lastVisiblePosition) list.getChildAt(position - first) else null
        if (row?.requestFocus() == true) return
        list.setSelection(position)
        // The row view exists after the next layout
        list.post { list.getChildAt(position - list.firstVisiblePosition)?.requestFocus() }
    }

    /**
     * Ends a glide that has not finished (the wheel turned again, or its time is up): the list goes straight to
     * where it was going and that row gets the focus, so the next step starts from it.
     */
    fun settle(list: AbsListView) {
        val position = gliding.remove(list) ?: return
        list.smoothScrollBy(0, 0)
        glideDelta(list, position)?.let { if (it != 0) list.scrollListBy(it) }
        val row = list.getChildAt(position - list.firstVisiblePosition) ?: return
        // A ListView lays out again around its selected row, so the selection follows, or the list jumps back.
        // A ListView-typed call: on the smart-cast list it goes to AbsListView.setSelectionFromTop (API 21).
        val listView: ListView? = list as? ListView
        listView?.setSelectionFromTop(position, row.top - list.paddingTop)
        row.requestFocus()
    }

    /** True while [list] glides to a row ([showRow]); List Edges then leaves the scroll to the glide. */
    fun isGliding(list: View): Boolean = gliding.containsKey(list)

    /**
     * Moves the focus to row [position] in the first frame that shows it, so the highlight comes in with the row
     * instead of after the glide.
     */
    private fun focusWhenShown(list: AbsListView, position: Int) {
        val observer = list.viewTreeObserver
        observer.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (gliding[list] != position) {
                    list.viewTreeObserver.removeOnPreDrawListener(this)
                    return true
                }
                val row = list.getChildAt(position - list.firstVisiblePosition) ?: return true
                list.viewTreeObserver.removeOnPreDrawListener(this)
                row.requestFocus()
                return true
            }
        })
    }

    /** [scrollDelta] for row [position] of [list] as it shows now. */
    private fun glideDelta(list: AbsListView, position: Int): Int? {
        if (list.childCount == 0 || list.height == 0) return null
        val first = list.firstVisiblePosition
        val fade = if (list is ListView && ListEdges.isFade(list.context)) list.verticalFadingEdgeLength else 0
        return scrollDelta(
            position, first, first + list.childCount - 1, list.count,
            { list.getChildAt(it - first)?.let { v -> intArrayOf(v.top, v.bottom) } },
            (list as? ListView)?.dividerHeight ?: 0, list.paddingTop, list.height - list.paddingBottom, fade
        )
    }
}
