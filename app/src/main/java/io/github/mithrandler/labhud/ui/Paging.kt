package io.github.mithrandler.labhud.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Lists and grids show all of their items and the screen scrolls (a swipe sideways moves between pages of the wall, on Status). The
 * names Paged* stay for the callers; [per] and [rows] are no longer used.
 */

/** The number of pages [size] items make at [per] a page; an empty list is still one page. */
fun pageCount(size: Int, per: Int): Int = ((size + per - 1) / per.coerceAtLeast(1)).coerceAtLeast(1)

/** [items] cut into pages of [per]; an empty list is one empty page. */
fun <T> paginate(items: List<T>, per: Int): List<List<T>> = items.chunked(per.coerceAtLeast(1)).ifEmpty { listOf(emptyList()) }

/** How many cells a row holds: 2 on a phone, 3 from 600 dp, 4 from 840 dp (landscape, tablet). */
@Composable
fun gridColumns(): Int = LocalConfiguration.current.screenWidthDp.let { if (it >= 840) 4 else if (it >= 600) 3 else 2 }

/**
 * Items of a grid packed into screens by height, the way the grid lays them out: an item takes [span] of
 * [cells] cells in a line, a line that is full (or whose next item does not fit) closes, and a line is as
 * tall as its tallest item ([height], dp). A screen holds [budget] dp, [gap] between lines; an item that
 * alone is taller than the budget still gets a screen of its own.
 */
fun <T> packGrid(items: List<T>, span: (T) -> Int, height: (T) -> Float, cells: Int, gap: Float, budget: Float): List<List<T>> {
    val out = mutableListOf<MutableList<T>>()
    var used = 0          // cells taken in the open line
    var lineH = 0f        // its height
    var total = 0f        // the screen's height without the open line
    for (item in items) {
        val w = span(item).coerceIn(1, cells)
        val h = height(item)
        if (out.isEmpty()) { out.add(mutableListOf()); used = 0; lineH = 0f; total = 0f }
        if (used + w > cells) {                       // the line closes, a new one opens
            total += lineH + gap
            used = 0
            lineH = 0f
        }
        val grown = maxOf(lineH, h)
        if (out.last().isNotEmpty() && total + grown > budget) {   // it does not fit under what is there: next screen
            out.add(mutableListOf())
            used = 0; lineH = 0f; total = 0f
        }
        out.last().add(item)
        used += w
        lineH = maxOf(lineH, h)
    }
    return out.ifEmpty { listOf(emptyList()) }
}

/** [items] as a grid of [cols] cells a row, all of them: the screen they are on scrolls. The cells share the row's width; the last row is padded so its cells stay as wide as the others. */
@Composable
fun <T> PagedGrid(items: List<T>, cols: Int, rows: Int = 2, modifier: Modifier = Modifier, cell: @Composable RowScope.(T) -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(cols.coerceAtLeast(1)).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { cell(it) }
                repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** [items] as rows, all of them (for rows of text that are not cells). */
@Composable
fun <T> PagedList(items: List<T>, per: Int = 6, modifier: Modifier = Modifier, row: @Composable (T) -> Unit) {
    Column(modifier.fillMaxWidth()) { items.forEach { row(it) } }
}

/** A whole screen of rows, scrolled as one. */
@Composable
fun <T> PagedScreen(items: List<T>, per: Int, modifier: Modifier = Modifier, row: @Composable (T) -> Unit) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
        items.forEach { row(it) }
    }
}
