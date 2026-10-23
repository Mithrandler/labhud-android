package io.github.mithrandler.labhud.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * A line over the last points of one number, filled faintly below. Gaps (null: no answer then)
 * break the line instead of drawing a slope across them. Drawn on a Canvas: no chart library.
 */
private const val BUCKETS = 72

/** The runs of consecutive known points as pixel offsets: a gap (null) ends one run, so no slope is drawn across it. */
internal fun runsOf(points: List<Double?>, stepX: Float, y: (Double) -> Float): List<List<Offset>> {
    var run = mutableListOf<Offset>()
    val runs = mutableListOf<List<Offset>>()
    points.forEachIndexed { i, v ->
        if (v == null) {
            if (run.isNotEmpty()) runs.add(run)
            run = mutableListOf()
        } else run.add(Offset(i * stepX, y(v)))
    }
    if (run.isNotEmpty()) runs.add(run)
    return runs
}

internal fun linePath(r: List<Offset>) = Path().apply {
    moveTo(r[0].x, r[0].y)
    for (i in 1 until r.size) lineTo(r[i].x, r[i].y)
}

@Composable
fun LineChart(raw: List<Double?>, color: Color = Lab.accent, modifier: Modifier = Modifier) {
    // averaged in buckets, like the wall's sparklines: one point a minute is noise, not a trend
    val points = remember(raw) { buckets(raw, BUCKETS) }
    Spacer(modifier.fillMaxWidth().height(LabScale.chartH).drawWithCache {
        // paths built once per size and data, not on every frame
        val values = points.filterNotNull()
        val drawn = if (values.size < 2) emptyList() else {
            var lo = values.min()
            var hi = values.max()
            if (hi - lo < 1e-9) { lo -= 1; hi += 1 }
            val pad = (hi - lo) * 0.1
            lo -= pad; hi += pad
            val stepX = size.width / (points.size - 1).coerceAtLeast(1)
            runsOf(points, stepX) { v -> (size.height * (1 - (v - lo) / (hi - lo))).toFloat() }
                .filter { it.size >= 2 }
                .map { r ->
                    val line = linePath(r)
                    val fill = Path().apply {
                        addPath(line)
                        lineTo(r.last().x, size.height)
                        lineTo(r.first().x, size.height)
                        close()
                    }
                    line to fill
                }
        }
        val shade = Brush.verticalGradient(listOf(color.copy(alpha = 0.25f), Color.Transparent))
        val stroke = Stroke(width = LabScale.strokeChart.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        onDrawBehind {
            if (drawn.isEmpty()) return@onDrawBehind
            // grid: three faint lines
            for (i in 1..3) {
                val gy = size.height * i / 4f
                drawLine(Lab.line, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1f)
            }
            for ((line, fill) in drawn) {
                drawPath(fill, shade)
                drawPath(line, color, style = stroke)
            }
        }
    })
}

/** The bucket averages [LineChart] draws, kept apart so a small chart can use them too. */
fun buckets(raw: List<Double?>, n: Int): List<Double?> {
    val per = ((raw.size + n - 1) / n).coerceAtLeast(1)
    return raw.chunked(per).map { b -> b.filterNotNull().takeIf { it.isNotEmpty() }?.average() }
}

/** A small line for a tile: no grid, no fill below, its own scale (or [lo]..[hi] when given). */
@Composable
fun Sparkline(raw: List<Double?>, color: Color, modifier: Modifier = Modifier, lo: Double? = null, hi: Double? = null) {
    val points = remember(raw) { buckets(raw, 36) }
    Spacer(modifier.drawWithCache {
        val values = points.filterNotNull()
        val line = Path()
        if (values.size >= 2) {
            var a = lo ?: values.min()
            var b = hi ?: values.max()
            if (b - a < 1e-9) { a -= 1; b += 1 }
            val stepX = size.width / (points.size - 1).coerceAtLeast(1)
            val inset = LabScale.strokeThin.toPx()
            fun y(v: Double) = (inset + (size.height - 2 * inset) * (1 - (v.coerceIn(a, b) - a) / (b - a))).toFloat()
            var open = false
            points.forEachIndexed { i, v ->
                if (v == null) open = false
                else if (!open) { line.moveTo(i * stepX, y(v)); open = true } else line.lineTo(i * stepX, y(v))
            }
        }
        val stroke = Stroke(width = LabScale.strokeThin.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        onDrawBehind { if (values.size >= 2) drawPath(line, color, style = stroke) }
    })
}
