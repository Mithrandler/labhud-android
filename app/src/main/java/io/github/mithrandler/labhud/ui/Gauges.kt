package io.github.mithrandler.labhud.ui

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.mithrandler.labhud.data.Card
import io.github.mithrandler.labhud.data.Gpu
import io.github.mithrandler.labhud.data.Metric

/** One share of a gauge, 0..100, in the colour of its severity ("", "warn", "crit"). */
data class GaugePart(val pct: Double, val sev: String) {
    val text: String get() = "${Math.round(pct)}%"
}

/**
 * One ring of a machine card. [a] fills the whole ring, or its left half when [b] is there too and fills the right half
 * (CPU and RAM, a GPU's load and its video memory). [temp] is the temperature of [a]'s part, shown inside under its number.
 */
data class Gauge(
    val label: String,
    val a: GaugePart,
    val b: GaugePart? = null,
    val temp: String? = null,
    val tempSev: String = "",
) {
    /** What a screen reader says: "CPU · RAM: 23 percent, 60 percent, 52 degrees". */
    val description: String get() = label + ": " + listOfNotNull(a.text, b?.text).joinToString(", ") { it.replace("%", " percent") } +
        (temp?.let { ", " + it.replace("°", " degrees") } ?: "")
}

private fun Metric.part() = GaugePart((pct ?: 0.0).coerceIn(0.0, 100.0), sev)
private val ramName = Regex("\\b(ram|mem)")
private fun Metric.isRam() = ramName.containsMatchIn((label + " " + key).lowercase())
private fun Metric.isKind(k: String) = Regex("\\b$k").containsMatchIn((label + " " + key).lowercase())

/** The server's colour for a GPU number, or (an older server) the default marks 80 and 92. */
private fun gpuSev(s: String?, v: Double) = s ?: if (v >= 92) "crit" else if (v >= 80) "warn" else ""

/**
 * The gauges of a machine, in the order CPU·RAM, the other shares (disk…), then the GPUs. The CPU and the RAM share one ring (a
 * half each); so do a GPU's load and video memory. A card's own GPU number is dropped when [Card.gpus] has the real ones.
 */
fun gaugesOf(c: Card, own: List<Metric>): List<Gauge> {
    val (rings, _) = ringsAndChips(own)
    val withGpus = c.gpus.isNotEmpty()
    val rest = rings.filterNot { (m, _) -> withGpus && m.isKind("gpu") }
    val cpu = rest.firstOrNull { (m, _) -> m.isKind("cpu") }
    val ram = rest.firstOrNull { (m, _) -> !m.isKind("cpu") && m.isRam() }
    val out = mutableListOf<Gauge>()
    if (cpu != null && ram != null) {
        val t = cpu.second
        out += Gauge("${cpu.first.short()} · ${ram.first.short()}", cpu.first.part().copy(sev = worseSev(cpu.first.sev, t?.sev.orEmpty())), ram.first.part(),
            t?.value, t?.sev.orEmpty())
    }
    for ((m, t) in rest) {
        if (cpu != null && ram != null && (m === cpu.first || m === ram.first)) continue
        out += Gauge(m.short(), m.part().copy(sev = worseSev(m.sev, t?.sev.orEmpty())), temp = t?.value, tempSev = t?.sev.orEmpty())
    }
    c.gpus.forEachIndexed { i, g ->
        val name = if (c.gpus.size > 1) "GPU${i + 1}" else "GPU"
        out += g.gauge(name)
    }
    return out
}

private fun Gpu.gauge(name: String): Gauge {
    val load = GaugePart(util.coerceIn(0.0, 100.0), gpuSev(utilSev, util))
    val vram = mem?.let { GaugePart(it.coerceIn(0.0, 100.0), gpuSev(memSev, it)) }
    val tempText = temp?.let { "${Math.round(it)}°" }
    val tempSev = if (temp == null) "" else tempSev ?: if (temp >= 85) "crit" else if (temp >= 75) "warn" else ""
    return Gauge(if (vram != null) "$name · VRAM" else name, load, vram, tempText, tempSev)
}

/** How many gauges a card has (its height on Status depends on it: four or more need three rows in portrait). */
fun Card.gaugeCount(): Int = gaugesOf(this, metrics.filter { it.value.isNotEmpty() && it.value != "—" }).size

/** Columns and the ring's diameter (dp) for [n] gauges in [width] x [height] dp. */
data class GaugeLayout(val cols: Int, val size: Float, val cell: Float)

/**
 * The columns for [n] gauges: the biggest ring (up to [maxRing]) the room allows, with each row needing [labelH] more for its label;
 * every layout whose ring is at least 85% of that counts as good, and among them the shape decides: one row for up to three gauges,
 * from four the fewest columns (two at least), so 4 gauges are 2×2 unless one row gives clearly bigger rings. A height of
 * [Float.MAX_VALUE] means no limit.
 */
fun gaugeLayout(n: Int, width: Float, height: Float, maxRing: Float = 96f, gap: Float = 12f, labelH: Float = 22f): GaugeLayout {
    val from = if (n >= 4) 2 else 1
    val options = (from..n.coerceIn(from, 6)).map { cols ->
        val rows = (n + cols - 1) / cols
        val cell = (width - gap * (cols - 1)) / cols
        val size = minOf(maxRing, cell, (height - rows * labelH - gap * (rows - 1)) / rows).coerceAtLeast(0f)
        Triple(cols, rows, GaugeLayout(cols, size, cell))
    }
    val top = options.maxOf { it.third.size }
    val good = options.filter { it.third.size >= top * 0.85f }
    return good.minWith(compareBy({ if (n <= 3) it.second else it.first }, { -it.third.size })).third
}

private val GapDp = 12f
private val LabelDp = 22f

/** A machine's gauges as a grid of equal rings, sized to the room the card gives them ([reserve] is what the card keeps for what comes after). */
@Composable
fun MachineGauges(gauges: List<Gauge>, modifier: Modifier = Modifier, reserve: Dp = 0.dp) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val h = if (maxHeight == Dp.Infinity) Float.MAX_VALUE else maxHeight.value - reserve.value
        val lay = remember(gauges.size, maxWidth, maxHeight, reserve) {
            gaugeLayout(gauges.size, maxWidth.value, h, LabScale.ring.value, GapDp, LabelDp)
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(GapDp.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            gauges.chunked(lay.cols).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(GapDp.dp, Alignment.CenterHorizontally)) {
                    row.forEach { GaugeRing(it, lay.size.dp, Modifier.width(lay.cell.dp)) }
                }
            }
        }
    }
}

/** True unless the system turned animations off (Settings > Accessibility > Remove animations, or the developer scale 0). */
@Composable
fun rememberAnimationsOn(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f }
}

/**
 * One gauge: a ring of [size], its numbers inside, its label under it. A ring with a second share is two half rings (the first
 * fills the left side from the bottom up, the second the right side), with a small gap at the top and at the bottom.
 */
@Composable
fun GaugeRing(g: Gauge, size: Dp, modifier: Modifier = Modifier) {
    val animate = rememberAnimationsOn()
    val a = remember { Animatable(if (animate) 0f else g.a.pct.toFloat()) }
    val b = remember { Animatable(if (animate) 0f else (g.b?.pct ?: 0.0).toFloat()) }
    LaunchedEffect(g.a.pct) { if (animate) a.animateTo(g.a.pct.toFloat(), tween(450)) else a.snapTo(g.a.pct.toFloat()) }
    LaunchedEffect(g.b?.pct) { val t = (g.b?.pct ?: 0.0).toFloat(); if (animate) b.animateTo(t, tween(450)) else b.snapTo(t) }
    val track = Lab.block
    val colA = barColor(g.a.sev)
    val colB = barColor(g.b?.sev.orEmpty())
    val dual = g.b != null
    Column(modifier.semantics(mergeDescendants = true) { contentDescription = g.description }, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val w = LabScale.ringStroke.toPx()
                val box = Size(this.size.width - w, this.size.height - w)
                val at = Offset(w / 2, w / 2)
                val stroke = Stroke(w, cap = StrokeCap.Round)
                if (!dual) {
                    drawArc(track, 0f, 360f, false, topLeft = at, size = box, style = Stroke(w))
                    if (a.value > 0f) drawArc(colA, -90f, 360f * a.value / 100f, false, topLeft = at, size = box, style = stroke)
                } else {
                    // 16° of gap top and bottom keeps the round ends of the two halves apart
                    val half = 164f
                    drawArc(track, 98f, half, false, topLeft = at, size = box, style = Stroke(w))     // left: bottom to top by the left
                    drawArc(track, -82f, half, false, topLeft = at, size = box, style = Stroke(w))    // right: top to bottom by the right
                    if (a.value > 0f) drawArc(colA, 98f, half * a.value / 100f, false, topLeft = at, size = box, style = stroke)
                    if (b.value > 0f) drawArc(colB, 82f, -half * b.value / 100f, false, topLeft = at, size = box, style = stroke)
                }
            }
            Column(Modifier.clearAndSetSemantics {}, horizontalAlignment = Alignment.CenterHorizontally) {
                if (!dual) {
                    GaugeNumber(g.a, size < LabScale.fieldMinS)
                } else Row(Modifier.width(size - LabScale.ringStroke * 2)) {
                    GaugeNumber(g.a, size < LabScale.fieldMinS, Modifier.weight(1f))
                    GaugeNumber(g.b!!, size < LabScale.fieldMinS, Modifier.weight(1f))
                }
                g.temp?.let { Text(it, fontSize = LabScale.textXs, color = if (g.tempSev.isEmpty()) Lab.muted else sevColor(g.tempSev), maxLines = 1) }
            }
        }
        Text(g.label, fontSize = LabScale.textSm, color = Lab.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            modifier = Modifier.clearAndSetSemantics {})
    }
}

@Composable
private fun GaugeNumber(p: GaugePart, small: Boolean, modifier: Modifier = Modifier) {
    Text(p.text, fontSize = if (small) LabScale.textSm else LabScale.textRing, fontWeight = FontWeight.SemiBold, color = sevColor(p.sev), maxLines = 1, softWrap = false,
        overflow = TextOverflow.Visible, textAlign = TextAlign.Center, modifier = modifier)
}
