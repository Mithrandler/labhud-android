package io.github.mithrandler.labhud.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mithrandler.labhud.data.Card
import io.github.mithrandler.labhud.data.Row as DataRow

val CardShape = RoundedCornerShape(12.dp)

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

/** One card as the phone shows it: a tile with its dot, name, numbers and note. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CardTile(card: Card, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        color = Lab.card,
        border = BorderStroke(1.dp, if (card.bad == true) Lab.red.copy(alpha = 0.7f) else Lab.line),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (card.status != null) {
                    StatusDot(card)
                    Spacer(Modifier.width(10.dp))
                }
                Text(card.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                val word = statusWord(card)
                if (word.isNotEmpty()) {
                    Text(word, fontSize = 12.sp, color = if (card.status == "down") Lab.red else Lab.muted)
                }
            }
            if (card.subtitle.isNotEmpty()) {
                Text(card.subtitle, fontSize = 12.sp, color = Lab.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp))
            }
            val fade = Modifier.alpha(if (card.frozen) 0.45f else 1f)
            when {
                card.metrics.isNotEmpty() -> FlowRow(
                    fade.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    card.metrics.forEach { m ->
                        Row(
                            Modifier.clip(RoundedCornerShape(6.dp)).background(Lab.block).padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(m.label, fontSize = 11.sp, color = Lab.muted)
                            Spacer(Modifier.width(5.dp))
                            Text(m.value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = sevColor(m.sev))
                        }
                    }
                }
                card.kind == "list" -> Column(fade.padding(top = 6.dp)) {
                    card.rows.take(5).forEach { ListRow(it) }
                    if (card.rows.size > 5) Text("+${card.rows.size - 5} more", fontSize = 12.sp, color = Lab.muted)
                }
                card.summary.isNotEmpty() -> Text(card.summary, fontSize = 13.sp, color = Lab.sub, modifier = fade.padding(top = 4.dp))
            }
            if (card.note.isNotEmpty()) {
                Text(card.note, fontSize = 12.sp, color = if (card.noteKind == "error" || card.noteKind == "stale") Lab.warn else Lab.muted,
                    modifier = Modifier.padding(top = 6.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A Proxmox guest: small, two to a row, like on the wall. */
@Composable
fun GuestTile(card: Card, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick, modifier = modifier, shape = CardShape, color = Lab.card,
        border = BorderStroke(1.dp, if (card.bad == true) Lab.red.copy(alpha = 0.7f) else Lab.line),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(card, 8)
                Spacer(Modifier.width(8.dp))
                Text(card.name, fontWeight = FontWeight.Medium, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(card.summary.ifEmpty { statusWord(card).ifEmpty { "—" } }, fontSize = 12.sp, color = Lab.sub,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
fun ListRow(row: DataRow) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(row.name, fontSize = 13.sp, color = if (row.bad) Lab.red else if (row.quiet) Lab.muted else Lab.text,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (row.value.isNotEmpty()) {
            Spacer(Modifier.width(12.dp))
            Text(row.value, fontSize = 13.sp, color = if (row.bad) Lab.red else Lab.sub, maxLines = 1)
        }
    }
}
