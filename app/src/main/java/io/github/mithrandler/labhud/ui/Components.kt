package io.github.mithrandler.labhud.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mithrandler.labhud.data.Card
import io.github.mithrandler.labhud.data.Metric
import io.github.mithrandler.labhud.data.Server
import java.util.Locale
import io.github.mithrandler.labhud.data.Row as DataRow

val CardShape = RoundedCornerShape(LabScale.radiusCard)

@Composable
fun StatusDot(card: Card, size: Int = 10) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(statusColor(card.status, card.bad)))
}

/** What the card's status says in words, when it is not simply "up". */
fun statusWord(card: Card): String = when (card.status) {
    "down" -> "down"
    "scheduled" -> "off on schedule"
    "on-demand" -> "off"
    "maintenance" -> "maintenance"
    "unknown" -> "…"
    else -> ""
}

/** How many columns of at least [min] px a grid of [available] px takes, with [spacing] px between
    them: as many as fit, never more than [cap] (a page's `columns`, 0 for no cap), never fewer than one. */
fun gridColumns(available: Int, spacing: Int, min: Int, cap: Int): Int {
    val fit = ((available + spacing) / (min + spacing).coerceAtLeast(1)).coerceAtLeast(1)
    return if (cap > 0) fit.coerceAtMost(cap) else fit
}

/** Like GridCells.Adaptive(min), but a page may cap the columns. */
class CappedAdaptive(private val min: androidx.compose.ui.unit.Dp, private val cap: Int, private val split: Int = 1) : androidx.compose.foundation.lazy.grid.GridCells {
    override fun androidx.compose.ui.unit.Density.calculateCrossAxisCellSizes(availableSize: Int, spacing: Int): List<Int> {
        // split: each column cut in this many cells, so a card can take a third of what a card took before
        val n = gridColumns(availableSize, spacing, min.roundToPx(), cap) * split
        val usable = availableSize - spacing * (n - 1)
        val each = usable / n
        return List(n) { if (it < usable % n) each + 1 else each }
    }
}

/** How many grid cells wide a card is on a phone, from its `size` (s 1, m and l 2, xl the whole row). */
fun Card.span(maxLine: Int): Int = when {
    size == "xl" -> maxLine
    size == "m" || size == "l" -> 2
    size.isEmpty() && kind == "list" -> maxLine
    else -> 1
}.coerceIn(1, maxLine)

/** The span of a card in the Status grid, whose cells are a third of a column: a guest is a mini-card (a third of the row on a phone). */
fun Card.statusSpan(maxLine: Int): Int = when {
    kind == "guest" -> 2
    size == "xl" -> maxLine
    size == "m" || size == "l" -> 6
    size.isEmpty() && kind == "list" -> maxLine
    else -> 3
}.coerceIn(1, maxLine)

/** The worst of the card: crit when red, warn when one of its numbers is past its mark, else "". */
fun Card.severity(): String = when {
    bad == true -> "crit"
    metrics.any { it.sev == "crit" } -> "crit"
    metrics.any { it.sev == "warn" } -> "warn"
    else -> ""
}

/** An uptime percentage as the wall writes it: 100%, 99.2%. */
fun uptimeText(percent: Double?, coverage: Double?): String {
    if (percent == null) return ""
    val p = if (percent >= 99.995) "100%" else String.format(Locale.ROOT, "%.1f%%", percent)
    return if (coverage != null && coverage < 99.95) p + " · " + String.format(Locale.ROOT, "%.0f%%", coverage) + " covered" else p
}

/**
 * One card, the same everywhere (Status, Devices): its icon, name and spec, the status dot; then
 * a bar for every percentage (orange and red past its marks), the other numbers in pairs; at the
 * foot the last half hour of its main number and the last 24 hours of uptime. A card in trouble
 * has a coloured edge on the left. [size] is the server's (s, m, l, xl): the bigger, the more numbers.
 */
@Composable
fun HudCard(
    c: Card,
    server: Server?,
    modifier: Modifier = Modifier,
    sparks: Map<String, List<Double?>> = emptyMap(),
    uptime: List<Double?>? = null,
    percent: Double? = null,
    coverage: Double? = null,
    compact: Boolean = false,
    /** Small facts under the numbers, as chips: 12/14 containers. */
    chips: List<String> = emptyList(),
    onClick: () -> Unit,
) {
    val sev = c.severity()
    // the host's graph and 24 h bar live in its panel: on the card they cost a third of its height
    val bigMachine = c.type == "proxmox" || c.type == "nas"
    Surface(
        onClick = onClick, modifier = modifier.fillMaxWidth(), shape = CardShape, color = Lab.card,
        // HUD: a thin edge on black, as the wall; Material: raised as an ElevatedCard, the edge only when in trouble
        border = when {
            c.bad == true -> BorderStroke(1.dp, Lab.red.copy(alpha = 0.7f))
            Lab.hud -> BorderStroke(1.dp, Lab.line)
            else -> null
        },
        shadowElevation = if (Lab.hud) 0.dp else 1.dp,
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            if (sev.isNotEmpty()) Box(Modifier.width(3.dp).fillMaxHeight().background(barColor(sev)))
            Column(Modifier.weight(1f).heightIn(min = if (compact) 72.dp else 92.dp)
                .padding(horizontal = LabScale.space3, vertical = if (compact) 6.dp else 10.dp)) {
                CardHead(c, server)
                val fade = Modifier.alpha(if (c.frozen) 0.45f else 1f)
                CardBody(c, fade, server, chips)
                if (c.note.isNotEmpty()) {
                    Text(c.note, fontSize = LabScale.textXs, color = if (c.noteKind == "error" || c.noteKind == "stale") Lab.warn else Lab.muted,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = LabScale.space1))
                }
                Spacer(Modifier.weight(1f))
                val main = c.metrics.firstOrNull { it.trend && sparks[it.key]?.count { v -> v != null }?.let { n -> n >= 2 } == true }
                if (main != null && !c.frozen && !bigMachine) {
                    Row(Modifier.padding(top = LabScale.space2), verticalAlignment = Alignment.CenterVertically) {
                        Text(main.short(), fontSize = LabScale.textXs, color = Lab.muted, maxLines = 1, modifier = Modifier.widthIn(max = 56.dp))
                        Spacer(Modifier.width(LabScale.space2))
                        Sparkline(sparks.getValue(main.key), if (main.sev.isEmpty()) Lab.accent else barColor(main.sev),
                            Modifier.weight(1f).height(18.dp))
                    }
                }
                if (!uptime.isNullOrEmpty() && !bigMachine) {
                    UptimeBar(uptime, Modifier.fillMaxWidth().padding(top = LabScale.space2).height(LabScale.bar), slices = 24)
                    val words = uptimeText(percent, coverage)
                    if (words.isNotEmpty()) Text("24 h · $words", fontSize = LabScale.textXs, color = Lab.muted, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
    }
}

/** Icon, name and spec, and the one status signal: the dot. */
@Composable
fun CardHead(c: Card, server: Server?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CardIcon(c, server)
        Spacer(Modifier.width(LabScale.space2))
        Column(Modifier.weight(1f)) {
            FitText(c.name, LabScale.textMd, FontWeight.SemiBold)
            if (c.subtitle.isNotEmpty() && c.size.isNotEmpty() && c.size != "s") {
                Text(c.subtitle, fontSize = LabScale.textXs, color = Lab.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (c.status != null || c.bad != null) { Spacer(Modifier.width(LabScale.space2)); StatusDot(c, 9) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardBody(c: Card, fade: Modifier, server: Server? = null, chips: List<String> = emptyList()) {
    val word = statusWord(c)
    val most = when (c.size) { "xl" -> 8; "l" -> 6; "m" -> 4; else -> 2 }
    // a guest has no metrics of its own: its CPU, RAM and disk come from the server's `guest`
    val own = c.metrics.filter { it.value.isNotEmpty() && it.value != "—" }.ifEmpty { c.guest?.meters().orEmpty() }
    // a machine shows all of its numbers (rings, temperatures, counts); a plain card only the first few
    val shown = if (c.type.isNotEmpty() || own.count { it.pct != null } >= 2) own.take(10) else own.take(most)
    // the state in words stays clear; only the numbers that are not live are dimmed
    if (word.isNotEmpty() && c.status != "up") {
        Text(word, fontSize = LabScale.textSm, color = if (c.status == "down") Lab.red else Lab.muted, modifier = Modifier.padding(top = LabScale.space2))
        return
    }
    Column(fade.padding(top = LabScale.space2)) {
        when {
            c.widget == "clock" -> ClockWidget(c.timezone)
            c.widget == "calendar" -> CalendarWidget()
            c.frame.isNotEmpty() -> FrameWidget(c.frame, c.frameHeight)
            shown.isNotEmpty() -> {
                val (rings, others) = ringsAndChips(shown)
                // two or more percentages: rings side by side, a temperature under its number; one alone keeps its bar
                if (rings.size >= 2 && c.type == "proxmox") Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    rings.take(4).forEach { (m, t) -> Ring(m, Modifier.weight(1f), t) }
                    c.gpus.take(2).forEachIndexed { i, g -> GpuRing(g, if (c.gpus.size > 1) "GPU${i + 1}" else "GPU", Modifier.weight(1f)) }
                } else rings.forEach { (m, t) -> MeterRow(m, thin = true); if (t != null) Chip(t) }
                // the GPU's temperature is in its own ring
                val facts = others.filter { c.gpus.isEmpty() || !(it.format == "celsius" && "gpu" in (it.label + it.key).lowercase()) } + listOfNotNull(c.guest?.uptime?.let { Metric(label = "up", value = upText(it)) }) +
                    chips.map { Metric(value = it) }
                if (facts.isNotEmpty()) FlowRow(Modifier.fillMaxWidth().padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    facts.forEach { Chip(it) }
                }
            }
            c.kind == "list" && c.rows.isNotEmpty() -> {
                val n = if (c.size == "s") 3 else 5
                c.rows.take(n).forEach { ListRow(it, server) }
                if (c.rows.size > n) Text("+${c.rows.size - n} more", fontSize = LabScale.textXs, color = Lab.muted)
            }
            !c.notes.isNullOrBlank() -> Text(c.notes, fontSize = LabScale.textSm, color = Lab.sub, maxLines = 4, overflow = TextOverflow.Ellipsis)
            c.bookmarks.isNotEmpty() -> Text(c.bookmarks.joinToString(" · ") { it.name }, fontSize = LabScale.textSm, color = Lab.sub,
                maxLines = 3, overflow = TextOverflow.Ellipsis)
            c.summary.isNotEmpty() -> Text(c.summary, fontSize = LabScale.textSm, color = Lab.sub, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A number that is not a percentage, as a small chip: its label, then its value. */
@Composable
fun Chip(m: Metric) {
    Row(Modifier.clip(RoundedCornerShape(50)).background(Lab.block).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (m.label.isNotEmpty() || m.key.isNotEmpty()) {
            Text(m.short(), fontSize = LabScale.textXs, color = Lab.muted, maxLines = 1)
            Spacer(Modifier.width(6.dp))
        }
        Text(m.value, fontSize = LabScale.textSm, fontWeight = FontWeight.Medium, color = sevColor(m.sev), maxLines = 1)
    }
}

/** A percentage as a ring (orange and red past its marks) with the number in the middle and its label under it. */
@Composable
fun Ring(m: Metric, modifier: Modifier = Modifier, temp: Metric? = null) {
    val pct = (m.pct ?: 0.0).coerceIn(0.0, 100.0)
    val sev = if (temp == null) m.sev else worseSev(m.sev, temp.sev)
    val color = barColor(sev)
    val track = Lab.block
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val w = 6.dp.toPx()
                val box = Size(size.width - w, size.height - w)
                val at = Offset(w / 2, w / 2)
                drawArc(track, 0f, 360f, false, topLeft = at, size = box, style = Stroke(w))
                if (pct > 0) drawArc(color, -90f, (360 * pct / 100).toFloat(), false, topLeft = at, size = box,
                    style = Stroke(w, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${Math.round(pct)}%", fontSize = LabScale.textXs, fontWeight = FontWeight.SemiBold, color = sevColor(m.sev))
                if (temp != null) Text(temp.value, fontSize = 10.sp, color = sevColor(temp.sev), maxLines = 1)
            }
        }
        Text(m.short(), fontSize = LabScale.textXs, color = Lab.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A percentage as a bar: its label and value on one line, the bar (orange and red past its marks) under them. */
@Composable
fun MeterRow(m: Metric, modifier: Modifier = Modifier, thin: Boolean = false) {
    Column(modifier.fillMaxWidth().padding(vertical = if (thin) 1.dp else 3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(m.short(), fontSize = LabScale.textXs, color = Lab.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(m.value, fontSize = LabScale.textSm, fontWeight = FontWeight.Medium, color = sevColor(m.sev), maxLines = 1,
                modifier = Modifier.padding(start = LabScale.space2))
        }
        Meter(m.pct ?: 0.0, m.sev, Modifier.fillMaxWidth().padding(top = if (thin) 1.dp else 3.dp), height = if (thin) 4.dp else LabScale.bar)
    }
}

/** The bar alone: [pct] of 0..100 filled in the severity's colour, on the block colour. */
@Composable
fun Meter(pct: Double, sev: String, modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = LabScale.bar) {
    val color = barColor(sev)
    Canvas(modifier.height(height)) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(Lab.block, cornerRadius = r)
        val w = (size.width * (pct.coerceIn(0.0, 100.0) / 100.0)).toFloat()
        if (w > 0f) drawRoundRect(color, size = Size(w.coerceAtLeast(size.height), size.height), cornerRadius = r)
    }
}

/**
 * Slices of time, oldest on the left: each the worst of the 15-minute points in it. A slice
 * labhud knows nothing about (it was off) is a thin grey line: a gap, not a colour.
 */
@Composable
fun UptimeBar(values: List<Double?>, modifier: Modifier, slices: Int = 48) {
    val n = slices
    val parts = remember(values, n) {
        val per = ((values.size + n - 1) / n).coerceAtLeast(1)
        values.chunked(per).map { b ->
            val known = b.filterNotNull()
            when {
                known.isEmpty() -> null
                known.all { it < 0 } -> -1.0
                else -> known.filter { it >= 0 }.min()
            }
        }
    }
    Canvas(modifier) {
        if (parts.isEmpty()) return@Canvas
        val gap = 1.5.dp.toPx()
        val w = (size.width - gap * (n - 1)) / n
        val offset = n - parts.size   // a young history fills from the right
        val r = CornerRadius(1.5.dp.toPx())
        parts.forEachIndexed { i, v ->
            val x = (offset + i) * (w + gap)
            if (v == null) {
                val h = (size.height / 3).coerceAtLeast(1.dp.toPx())
                drawRoundRect(Lab.border, Offset(x, (size.height - h) / 2), Size(w, h), r)
            } else drawRoundRect(uptimeColor(v), Offset(x, 0f), Size(w, size.height), r)
        }
    }
}

@Composable
fun ListRow(row: DataRow, server: Server? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        if (row.icon.isNotEmpty()) {
            NamedIcon(row.icon, LabIcons.container, server, 14.dp)
            Spacer(Modifier.width(6.dp))
        }
        Text(row.name, fontSize = LabScale.textSm, color = if (row.bad) Lab.red else if (row.quiet) Lab.muted else Lab.text,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (row.value.isNotEmpty()) {
            Spacer(Modifier.width(12.dp))
            Text(row.value, fontSize = LabScale.textSm, color = if (row.bad) Lab.red else Lab.sub, maxLines = 1)
        }
    }
}

/** A group's title, as on the wall: a dot (red while one of its cards is), the name, a thin line. */
@Composable
fun SectionTitle(title: String, bad: Boolean = false, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(top = LabScale.space2), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(if (bad) Lab.red else Lab.title))
        Spacer(Modifier.width(LabScale.space2))
        Text(title.uppercase(), style = Heading, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(LabScale.space2))
        Box(Modifier.weight(1f).height(1.dp).background(Lab.line))
    }
}


/**
 * A guest (VM, LXC) as a small card, three to a row: its name and dot, then CPU, RAM and disk as thin
 * bars and how long it has run. A guest that is not running says so instead of the bars.
 */
@Composable
fun MiniCard(c: Card, server: Server?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val sev = c.severity()
    Surface(
        onClick = onClick, modifier = modifier.fillMaxWidth(), shape = CardShape, color = Lab.card,
        border = when {
            c.bad == true -> BorderStroke(1.dp, Lab.red.copy(alpha = 0.7f))
            Lab.hud -> BorderStroke(1.dp, Lab.line)
            else -> null
        },
        shadowElevation = if (Lab.hud) 0.dp else 1.dp,
    ) {
        // a guest that is down has no bars to show: its card is as short as its two lines
        Column(Modifier.heightIn(min = if (c.guest == null) 0.dp else 96.dp).padding(horizontal = 8.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CardIcon(c, server)
                Spacer(Modifier.width(6.dp))
                FitText(c.name, 12.sp, FontWeight.SemiBold, Modifier.weight(1f))
                if (c.status != null || c.bad != null) { Spacer(Modifier.width(4.dp)); StatusDot(c, 8) }
            }
            val g = c.guest
            if (g == null) {
                Text(statusWord(c).ifEmpty { c.summary }, fontSize = 12.sp, color = if (c.status == "down") Lab.red else Lab.muted,
                    modifier = Modifier.padding(top = 8.dp))
            } else Column(Modifier.padding(top = 6.dp).alpha(if (c.frozen) 0.45f else 1f)) {
                g.meters().forEach { m ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 1.dp)) {
                        Text(m.label, fontSize = 9.sp, color = Lab.muted, modifier = Modifier.width(26.dp), maxLines = 1)
                        Meter(m.pct ?: 0.0, m.sev, Modifier.weight(1f), height = 4.dp)
                        Text(m.value, fontSize = 9.sp, color = sevColor(m.sev), maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.End,
                            modifier = Modifier.width(26.dp))
                    }
                }
                g.uptime?.let { Text("up " + upText(it), fontSize = 10.sp, color = Lab.muted, modifier = Modifier.padding(top = 3.dp)) }
            }
        }
    }
}


/**
 * A graphics card in one ring: the load round the outside, the video memory in a thinner ring inside it,
 * the load and the temperature in the middle, "VRAM 30%" under the name.
 */
@Composable
fun GpuRing(g: io.github.mithrandler.labhud.data.Gpu, name: String, modifier: Modifier = Modifier) {
    val util = g.util.coerceIn(0.0, 100.0)
    val mem = g.mem?.coerceIn(0.0, 100.0)
    val track = Lab.block
    val loadColor = if (util >= 92) Lab.red else if (util >= 80) Lab.warn else Lab.accent
    val memColor = if ((mem ?: 0.0) >= 92) Lab.red else if ((mem ?: 0.0) >= 80) Lab.warn else Lab.accent.copy(alpha = 0.55f)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val w = 6.dp.toPx(); val w2 = 4.dp.toPx()
                val outer = Size(size.width - w, size.height - w)
                drawArc(track, 0f, 360f, false, topLeft = Offset(w / 2, w / 2), size = outer, style = Stroke(w))
                if (util > 0) drawArc(loadColor, -90f, (360 * util / 100).toFloat(), false, topLeft = Offset(w / 2, w / 2), size = outer,
                    style = Stroke(w, cap = StrokeCap.Round))
                if (mem != null) {
                    val inset = w + 2.dp.toPx() + w2 / 2
                    val inner = Size(size.width - 2 * inset, size.height - 2 * inset)
                    drawArc(track, 0f, 360f, false, topLeft = Offset(inset, inset), size = inner, style = Stroke(w2))
                    if (mem > 0) drawArc(memColor, -90f, (360 * mem / 100).toFloat(), false, topLeft = Offset(inset, inset), size = inner,
                        style = Stroke(w2, cap = StrokeCap.Round))
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${Math.round(util)}%", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (util >= 80) loadColor else Lab.text)
                g.temp?.let { Text("${Math.round(it)}°", fontSize = 10.sp, color = Lab.muted, maxLines = 1) }
            }
        }
        Text(name, fontSize = LabScale.textXs, color = Lab.muted, maxLines = 1)
        // the legend, in the rings' own colours: the outer ring is the load, the thinner inner one the video memory
        GpuKey(loadColor, "Load ${Math.round(util)}%", 4.dp)
        mem?.let { GpuKey(memColor, "VRAM ${Math.round(it)}%", 2.dp) }
    }
}

/**
 * A name on one line that shrinks (down to half of [size]) until all of it shows; only a name that is
 * still too long then ends in "…". Cards cut "homeassistant" to "homeassi…" otherwise.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun FitText(text: String, size: androidx.compose.ui.unit.TextUnit, weight: FontWeight? = null, modifier: Modifier = Modifier, color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified) {
    Text(text, modifier, color = color, fontWeight = weight, maxLines = 1, overflow = TextOverflow.Ellipsis,
        autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = size * 0.5f, maxFontSize = size, stepSize = 0.5.sp))
}

/** One line of a GPU ring's legend: a swatch as thick as the ring it names, then the words. */
@Composable
private fun GpuKey(color: androidx.compose.ui.graphics.Color, text: String, thickness: androidx.compose.ui.unit.Dp) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 1.dp)) {
        Box(Modifier.width(12.dp).height(thickness).clip(androidx.compose.foundation.shape.RoundedCornerShape(50)).background(color))
        Spacer(Modifier.width(4.dp))
        Text(text, fontSize = 10.sp, lineHeight = 12.sp, color = Lab.sub, maxLines = 1)
    }
}
