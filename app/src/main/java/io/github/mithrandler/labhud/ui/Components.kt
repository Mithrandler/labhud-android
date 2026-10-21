package io.github.mithrandler.labhud.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
class CappedAdaptive(private val min: androidx.compose.ui.unit.Dp, private val cap: Int) : androidx.compose.foundation.lazy.grid.GridCells {
    override fun androidx.compose.ui.unit.Density.calculateCrossAxisCellSizes(availableSize: Int, spacing: Int): List<Int> {
        val n = gridColumns(availableSize, spacing, min.roundToPx(), cap)
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
    onClick: () -> Unit,
) {
    val sev = c.severity()
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
                CardBody(c, fade, server)
                if (c.note.isNotEmpty()) {
                    Text(c.note, fontSize = LabScale.textXs, color = if (c.noteKind == "error" || c.noteKind == "stale") Lab.warn else Lab.muted,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = LabScale.space1))
                }
                Spacer(Modifier.weight(1f))
                val main = c.metrics.firstOrNull { it.trend && sparks[it.key]?.count { v -> v != null }?.let { n -> n >= 2 } == true }
                if (main != null && !c.frozen) {
                    Row(Modifier.padding(top = LabScale.space2), verticalAlignment = Alignment.CenterVertically) {
                        Text(main.short(), fontSize = LabScale.textXs, color = Lab.muted, maxLines = 1, modifier = Modifier.widthIn(max = 56.dp))
                        Spacer(Modifier.width(LabScale.space2))
                        Sparkline(sparks.getValue(main.key), if (main.sev.isEmpty()) Lab.accent else barColor(main.sev),
                            Modifier.weight(1f).height(18.dp))
                    }
                }
                if (!uptime.isNullOrEmpty()) {
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
            Text(c.name, fontWeight = FontWeight.SemiBold, fontSize = LabScale.textMd, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (c.subtitle.isNotEmpty() && c.size.isNotEmpty() && c.size != "s") {
                Text(c.subtitle, fontSize = LabScale.textXs, color = Lab.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (c.status != null || c.bad != null) { Spacer(Modifier.width(LabScale.space2)); StatusDot(c, 9) }
    }
}

@Composable
private fun CardBody(c: Card, fade: Modifier, server: Server? = null) {
    val word = statusWord(c)
    val most = when (c.size) { "xl" -> 8; "l" -> 6; "m" -> 4; else -> 2 }
    val shown = c.metrics.filter { it.value.isNotEmpty() && it.value != "—" }.take(most)
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
                shown.filter { it.pct != null }.forEach { MeterRow(it) }
                shown.filter { it.pct == null }.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        pair.forEach { m -> PairCell(m, Modifier.weight(1f)) }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
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

/** A number that is not a percentage: its label over its value. */
@Composable
private fun PairCell(m: Metric, modifier: Modifier) {
    Column(modifier.padding(end = LabScale.space2)) {
        Text(m.short(), fontSize = LabScale.textXs, color = Lab.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(m.value, fontSize = LabScale.textMd, fontWeight = FontWeight.Medium, color = sevColor(m.sev), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A percentage as a bar: its label and value on one line, the bar (orange and red past its marks) under them. */
@Composable
fun MeterRow(m: Metric, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(m.short(), fontSize = LabScale.textXs, color = Lab.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(m.value, fontSize = LabScale.textSm, fontWeight = FontWeight.Medium, color = sevColor(m.sev), maxLines = 1,
                modifier = Modifier.padding(start = LabScale.space2))
        }
        Meter(m.pct ?: 0.0, m.sev, Modifier.fillMaxWidth().padding(top = 3.dp))
    }
}

/** The bar alone: [pct] of 0..100 filled in the severity's colour, on the block colour. */
@Composable
fun Meter(pct: Double, sev: String, modifier: Modifier = Modifier) {
    val color = barColor(sev)
    Canvas(modifier.height(LabScale.bar)) {
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
