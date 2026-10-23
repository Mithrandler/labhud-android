package io.github.mithrandler.labhud.ui.settings

import io.github.mithrandler.labhud.ui.LabScale
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mithrandler.labhud.ui.CardShape
import io.github.mithrandler.labhud.ui.Lab
import io.github.mithrandler.labhud.ui.LabIcons

/**
 * A panel that shows one line (its title and a summary) and opens on a tap: a section with many
 * panels stays short, and the details are one tap away. [onOpen] runs each time it opens (to load
 * what is only read when someone looks).
 */
@Composable
fun CollapsiblePanel(
    title: String,
    summary: String = "",
    key: String = title,
    startOpen: Boolean = false,
    onOpen: () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    var open by rememberSaveable(key) { mutableStateOf(startOpen) }
    Surface(shape = CardShape, color = Lab.card, border = BorderStroke(LabScale.hairline, Lab.line)) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().clickable { open = !open; if (open) onOpen() }.padding(horizontal = LabScale.space3, vertical = LabScale.space3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.Medium, fontSize = LabScale.textMd, color = Lab.text)
                    if (summary.isNotEmpty()) Text(summary, fontSize = LabScale.textSm, color = Lab.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(if (open) LabIcons.chevronDown else LabIcons.chevronRight, null, tint = Lab.muted, modifier = Modifier.size(LabScale.iconCard))
            }
            if (open) Column(Modifier.fillMaxWidth().padding(start = LabScale.space3, end = LabScale.space3, bottom = LabScale.space3), content = content)
        }
    }
}
