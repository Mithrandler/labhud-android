package io.github.mithrandler.labhud.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.mithrandler.labhud.data.Card
import io.github.mithrandler.labhud.data.Server
import kotlin.math.roundToInt

/* The two tiles of the redesigned Status screen (the wall's, see labhud static/tiles.js): a host two cells wide with its four
   meters, and the summary of a group of cards. Every other card is a MiniCard. */

/** One of a host's four meters: its name, the percentage (null when the host has no such number) and the colour the server gives it. */
internal class HostMeter(val label: String, val pct: Double?, val sev: String)

/** CPU, RAM, GPU and video memory of a host; with several GPUs the busiest. A missing number is null: its place stays. */
internal fun Card.hostMeters(): List<HostMeter> {
    fun share(vararg names: String) = metrics.firstOrNull { m -> m.pct != null && names.any { m.short().equals(it, true) } }
    val cpu = share("CPU")
    val ram = share("RAM", "Memory")
    val gpu = gpus.maxByOrNull { it.util }
    val vram = gpus.filter { it.mem != null }.maxByOrNull { it.mem ?: 0.0 }
    return listOf(
        HostMeter("CPU", cpu?.pct, cpu?.sev.orEmpty()),
        HostMeter("RAM", ram?.pct, ram?.sev.orEmpty()),
        HostMeter("GPU", gpu?.util, gpu?.utilSev.orEmpty()),
        HostMeter("VRAM", vram?.mem, vram?.memSev.orEmpty()),
    )
}

/** "6 VM · 3 LXC · all fine", or how many of them are stopped; the card's subtitle when it knows no guests. */
internal fun Card.guestLine(): String {
    val kinds = guestCounts.entries.sortedByDescending { it.key }.filter { it.value.size == 2 && it.value[1] > 0 }
    if (kinds.isEmpty()) return subtitle
    val stopped = kinds.sumOf { it.value[1] - it.value[0] }
    return kinds.joinToString(" · ") { "${it.value[1]} ${it.key.uppercase()}" } + " · " + if (stopped > 0) "$stopped stopped" else "all fine"
}

private fun tintOf(sev: String): Color? = when (sev) { "crit" -> Lab.red; "warn" -> Lab.warn; else -> null }

@Composable
private fun TileSurface(tint: Color?, modifier: Modifier, onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick, modifier = modifier.fillMaxWidth().height(LocalTileH.current), shape = CardShape,
        color = tint?.copy(alpha = 0.12f)?.compositeOver(Lab.card) ?: Lab.card,
        border = when {
            tint != null -> BorderStroke(LabScale.hairline, tint.copy(alpha = 0.7f))
            Lab.hud -> BorderStroke(LabScale.hairline, Lab.line)
            else -> null
        },
        shadowElevation = if (Lab.hud) 0.dp else LabScale.hairline,
    ) { content() }
}

/** A host: its name and dot, CPU / RAM / GPU / VRAM as a number and a bar each, and how many guests it runs. Its guests are listed on its screen. */
@Composable
internal fun HostTile(c: Card, server: Server?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val sev = c.severity().let { if (c.bad == true) "crit" else it }
    TileSurface(tintOf(sev), modifier, onClick) {
        Column(Modifier.padding(horizontal = LabScale.space3, vertical = LabScale.space2), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CardIcon(c, server, LabScale.iconTile)
                Spacer(Modifier.width(LabScale.space2))
                FitText(c.name, LabScale.textWallName, FontWeight.SemiBold, Modifier.weight(1f), minSize = LabScale.textWallMin)
                if (c.status != null || c.bad != null) { Spacer(Modifier.width(LabScale.space1)); StatusDot(c, LabScale.dotTile.value.toInt()) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(LabScale.space2)) {
                c.hostMeters().forEach { m ->
                    Column(Modifier.weight(1f)) {
                        Text(m.label, fontSize = LabScale.textWallMin, lineHeight = LabScale.textWallMin * 1.15f, color = Lab.sub, maxLines = 1)
                        Text(m.pct?.let { "${it.roundToInt()}%" } ?: "—", fontSize = LabScale.textWallNum, lineHeight = LabScale.textWallNum * 1.15f, fontWeight = FontWeight.SemiBold,
                            color = sevColor(m.sev), maxLines = 1, softWrap = false)
                        Meter(m.pct ?: 0.0, m.sev, Modifier.fillMaxWidth().padding(top = LabScale.space1), height = LabScale.bar)
                    }
                }
            }
            Text(c.guestLine().ifEmpty { " " }, fontSize = LabScale.textWallMin, lineHeight = LabScale.textWallMin * 1.2f, color = Lab.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A group of cards: how many are in trouble (red while any is, orange for warnings, green when none), and who. The list is one tap away. */
@Composable
internal fun GroupSummaryTile(g: GroupTile, server: Server?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val problems = g.problems
    val warnings = g.warnings
    val color = when { problems > 0 -> Lab.red; warnings > 0 -> Lab.warn; else -> Lab.green }
    val count = if (problems > 0) problems else warnings
    // what is wrong, not who has it: the first broken things of the cards in trouble (the card's own name when it lists none)
    val who = g.cards.filter { isProblem(it) || it.severity() == "warn" }.sortedByDescending { isProblem(it) }
        .flatMap { c -> brokenRows(c).map { it.name }.ifEmpty { listOf(c.name) } }
    TileSurface(tintOf(if (problems > 0) "crit" else if (warnings > 0) "warn" else ""), modifier, onClick) {
        Column(Modifier.padding(horizontal = LabScale.space3, vertical = LabScale.space2), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                g.cards.firstOrNull()?.let { CardIcon(it, server, LabScale.iconTile); Spacer(Modifier.width(LabScale.space2)) }
                FitText(g.title.uppercase(), LabScale.textWallName, FontWeight.SemiBold, Modifier.weight(1f), minSize = LabScale.textWallMin)
                Spacer(Modifier.width(LabScale.space1))
                Box(Modifier.size(LabScale.dotTile).clip(CircleShape).background(color))
            }
            Text(if (count > 0) "$count" else "OK", fontSize = LabScale.textWallNum, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1)
            Text(if (count == 0) "" else who.take(2).joinToString(", ") + if (who.size > 2) " +${who.size - 2}" else "",
                fontSize = LabScale.textWallMin, color = Lab.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
