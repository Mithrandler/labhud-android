package io.github.mithrandler.labhud.ui

import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.isSpecified

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
    /** A small fact under the numbers, as a chip: 12/14 containers. */
    chip: String? = null,
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
        Box {
            if (sev.isNotEmpty()) Box(Modifier.width(3.dp).fillMaxHeight().background(barColor(sev)).align(Alignment.CenterStart))
            Column(Modifier.fillMaxWidth().heightIn(min = if (compact) 72.dp else 92.dp)
                .padding(horizontal = LabScale.space3, vertical = if (compact) 6.dp else 10.dp)) {
                CardHead(c, server)
                val fade = Modifier.alpha(if (c.frozen) 0.45f else 1f)
                CardBody(c, fade, server, chip)
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
private fun CardBody(c: Card, fade: Modifier, server: Server? = null, chip: String? = null) {
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
                val gauges = remember(c) { gaugesOf(c, shown) }
                // two or more shares: gauge rings (one ring holds two: CPU and RAM, a GPU's load and memory); one alone keeps its bar
                val ringed = gauges.size >= 2 || (c.type == "proxmox" && gauges.isNotEmpty())
                // the GPU's temperature is in its own ring
                val counts = c.guestCounts.entries.sortedBy { it.key }.mapNotNull { (k, v) -> if (v.size == 2 && v[1] > 0) Metric(label = k.uppercase(), value = "${v[0]}/${v[1]}") else null }
                val facts = others.filter { o -> counts.isEmpty() || counts.none { it.label.equals(o.label, true) } }.filter { c.gpus.isEmpty() || !(it.format == "celsius" && "gpu" in (it.label + it.key).lowercase()) } + listOfNotNull(c.guest?.uptime?.let { Metric(label = "up", value = upText(it)) }) + counts +
                    listOfNotNull(chip).map { Metric(value = it) }
                if (ringed) MachineGauges(gauges, Modifier.padding(vertical = 2.dp), reserve = if (facts.isNotEmpty()) 40.dp else 0.dp)
                else rings.forEach { (m, t) -> MeterRow(m, thin = true); if (t != null) Chip(t) }
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

/** The last week, a square a day coloured by how much of that day it was up, with the day's initials and percent. */
@Composable
fun UptimeDays(values: List<Double?>, modifier: Modifier = Modifier) {
    val shares = remember(values) { dayShares(values) }
    val today = java.time.LocalDate.now()
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales.get(0)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        shares.forEachIndexed { i, v ->
            val day = today.minusDays((shares.size - 1 - i).toLong()).dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, locale).take(2)
            val color = uptimeColor(v)
            val on = v != null && v >= 0
            Column(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(if (on) color else Lab.block).padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(day.uppercase(), fontSize = 10.sp, color = if (on) androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.7f) else Lab.muted)
                Text(if (on) "${Math.round(v!! * 100)}%" else "—", fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    color = if (on) androidx.compose.ui.graphics.Color.Black else Lab.muted)
            }
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


/** The height of every small card on Status: the same everywhere (112 dp, 84 dp on a wide, low screen), so the grid has the same gap in every direction. */
val LocalTileH = androidx.compose.runtime.compositionLocalOf { 112.dp }

/** How a card sits in the grid of equal cells: [span] cells wide ([cols] to a row), [rows] cells tall. */
fun Card.tileSpan(cols: Int): Int = when {
    isBig() -> cols
    size == "m" -> 2.coerceAtMost(cols)
    else -> 1
}

/** Rows of cells a card takes. */
fun Card.tileRows(): Int = if (isBig()) 2 else 1

/**
 * A card's height in dp: its rows of cells and the gaps between them; a card with gauges is never shorter than they need. Four or more
 * rings are 2×2 (two rings of 80 dp, their labels, the chips and the head: 326 dp) in portrait, one row (208 dp) on a low screen
 * ([tile] is a cell's height); two or three are one row.
 */
fun Card.tileHeight(tile: Float): Float {
    val rows = tileRows()
    val h = tile * rows + 12f * (rows - 1)
    val n = if (isBig()) gaugeCount() else 0
    return when {
        n >= 4 -> maxOf(h, if (tile >= 100f) 326f else 208f)
        n >= 2 -> maxOf(h, 208f)
        else -> h
    }
}

/** The cards that need more than a cell: a machine's rings, and the widgets that draw themselves (clock, calendar, a frame). Lists, notes and links are cells too. */
fun Card.isBig(): Boolean = kind != "guest" && (type == "proxmox" || type == "nas" || widget.isNotEmpty() || frame.isNotEmpty())

/**
 * A small card, one cell of the grid: always [LocalTileH] high. Its icon, name and dot; then the one number it is about ([headline]:
 * the worst of CPU, RAM and disk) with its label, a thin bar, and how long it has run or what state it is in. A card without numbers
 * says what it knows about itself. A card in trouble has a coloured edge and ground. Everything else is on the card's own screen.
 */
@Composable
fun MiniCard(c: Card, server: Server?, modifier: Modifier = Modifier, chip: String? = null, onClick: () -> Unit) {
    val sev = c.severity().let { if (c.bad == true) "crit" else it }
    val tint = when (sev) { "crit" -> Lab.red; "warn" -> Lab.warn; else -> null }
    val low = LocalTileH.current < 100.dp   // a wide, low screen: no room for the last line
    Surface(
        onClick = onClick, modifier = modifier.fillMaxWidth().height(LocalTileH.current), shape = CardShape,
        color = tint?.copy(alpha = 0.12f)?.compositeOver(Lab.card) ?: Lab.card,
        border = when {
            tint != null -> BorderStroke(1.dp, tint.copy(alpha = 0.7f))
            Lab.hud -> BorderStroke(1.dp, Lab.line)
            else -> null
        },
        shadowElevation = if (Lab.hud) 0.dp else 1.dp,
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CardIcon(c, server, 18.dp)
                Spacer(Modifier.width(6.dp))
                FitText(c.name, LabScale.textTileName, FontWeight.SemiBold, Modifier.weight(1f), minSize = LabScale.textXs)
                if (c.status != null || c.bad != null) { Spacer(Modifier.width(4.dp)); StatusDot(c, 8) }
            }
            val head = c.headline()
            val word = statusWord(c)
            Column(Modifier.padding(top = 4.dp).weight(1f).alpha(if (c.frozen) 0.45f else 1f), verticalArrangement = Arrangement.SpaceEvenly) {
                if (head == null) Text(
                    word.ifEmpty {
                        c.rows.take(3).joinToString("\n") { it.name.ifEmpty { it.value } }.ifEmpty {
                            c.notes?.takeIf { it.isNotBlank() } ?: c.bookmarks.take(3).joinToString("\n") { it.name }.ifEmpty { c.summary.ifEmpty { c.subtitle } }
                        }
                    },
                    fontSize = LabScale.textXs, lineHeight = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, color = if (c.status == "down") Lab.red else Lab.muted)
                else {
                    Row(verticalAlignment = Alignment.Bottom) {
                        FitText(head.value, LabScale.textTile, FontWeight.SemiBold, Modifier.weight(1f, fill = false), color = sevColor(head.sev), minSize = LabScale.textMd)
                        Spacer(Modifier.width(4.dp))
                        Text(head.short(), fontSize = LabScale.textXs, color = Lab.muted, maxLines = 1, modifier = Modifier.padding(bottom = 3.dp))
                    }
                    head.pct?.let { Meter(it, head.sev, Modifier.fillMaxWidth(), height = 4.dp) }
                    if (!low) {
                        val up = c.guest?.uptime?.let { "up " + upText(it) } ?: c.metrics.firstOrNull { it.short().equals("uptime", true) }?.value?.let { "up $it" }
                        val line = word.ifEmpty { listOfNotNull(up, chip?.substringBefore(" ")).joinToString(" · ") }
                        Text(line.ifEmpty { " " }, fontSize = LabScale.textXs, color = if (c.status == "down") Lab.red else Lab.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** The "+3" that ends the "Needs attention" row when more cards are in trouble than the row holds. */
@Composable
fun MoreTile(n: Int) {
    Surface(shape = CardShape, color = Lab.card, border = BorderStroke(1.dp, Lab.line), modifier = Modifier.fillMaxWidth().height(LocalTileH.current)) {
        Box(contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("+$n", fontSize = LabScale.textTile, fontWeight = FontWeight.SemiBold, color = Lab.red)
                Text("more", fontSize = LabScale.textXs, color = Lab.muted)
            }
        }
    }
}

/**
 * A name on one line that shrinks (down to half of [size]) until all of it shows; only a name that is
 * still too long then ends in "…". Cards cut "homeassistant" to "homeassi…" otherwise.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun FitText(text: String, size: androidx.compose.ui.unit.TextUnit, weight: FontWeight? = null, modifier: Modifier = Modifier, color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
            /** The smallest size it shrinks to; half of [size] when not given. */
            minSize: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified) {
    Text(text, modifier, color = color, fontWeight = weight, maxLines = 1, overflow = TextOverflow.Ellipsis,
        autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(
            minFontSize = if (minSize.isSpecified) minSize else size * 0.5f, maxFontSize = size, stepSize = 0.5.sp))
}
