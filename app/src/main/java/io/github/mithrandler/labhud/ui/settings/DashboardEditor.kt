package io.github.mithrandler.labhud.ui.settings

import io.github.mithrandler.labhud.ui.LabScale
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mithrandler.labhud.data.LayoutCard
import io.github.mithrandler.labhud.data.LayoutGroup
import io.github.mithrandler.labhud.data.LayoutPage
import io.github.mithrandler.labhud.data.editCard
import io.github.mithrandler.labhud.data.editGroup
import io.github.mithrandler.labhud.data.editPage
import io.github.mithrandler.labhud.data.moveCard
import io.github.mithrandler.labhud.data.moveCardTo
import io.github.mithrandler.labhud.data.moveGroup
import io.github.mithrandler.labhud.data.movePage
import io.github.mithrandler.labhud.ui.Lab
import io.github.mithrandler.labhud.ui.LabIcons
import io.github.mithrandler.labhud.ui.Panel

/** The pages, in order, each with its number of groups and cards; ↑ ↓ reorder, a tap opens it. */
fun LazyListScope.pagesList(pages: List<LayoutPage>, enabled: Boolean, onChange: (List<LayoutPage>) -> Unit, onOpen: (String) -> Unit) {
    item {
        Panel("") {
            Text("Pages · ${pages.sumOf { p -> p.groups.sumOf { it.cards.size } }} cards", fontWeight = FontWeight.Medium, fontSize = LabScale.textMd)
            Text("The order here is the order on the screen. Tap a page for its groups and cards.", fontSize = LabScale.textSm, color = Lab.sub)
            pages.forEachIndexed { i, p ->
                Row(Modifier.fillMaxWidth().clickable { onOpen(p.id) }.padding(vertical = LabScale.space1), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(p.title, fontWeight = FontWeight.Medium, fontSize = LabScale.textMd, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${p.groups.size} groups · ${p.groups.sumOf { it.cards.size }} cards", fontSize = LabScale.textSm, color = Lab.muted)
                    }
                    Arrows(up = enabled && i > 0, down = enabled && i < pages.lastIndex,
                        onUp = { onChange(pages.movePage(p.id, -1)) }, onDown = { onChange(pages.movePage(p.id, 1)) })
                    Icon(LabIcons.chevronRight, null, tint = Lab.muted)
                }
            }
        }
    }
}

/** One page: its look, then every group as a panel with its cards. */
fun LazyListScope.pageEditor(pages: List<LayoutPage>, pageId: String, enabled: Boolean, onChange: (List<LayoutPage>) -> Unit, onMove: (String) -> Unit) {
    val page = pages.firstOrNull { it.id == pageId } ?: return
    item(key = "look-$pageId") { PageLook(page, enabled) { edit -> onChange(pages.editPage(pageId, edit)) } }
    page.groups.forEachIndexed { gi, g ->
        item(key = "group-${g.id}") {
            Panel("") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(g.title, { v -> onChange(pages.editGroup(g.id) { it.copy(title = v.take(60)) }) }, singleLine = true,
                        enabled = enabled, label = { Text("Group") }, modifier = Modifier.weight(1f))
                    Arrows(up = enabled && gi > 0, down = enabled && gi < page.groups.lastIndex,
                        onUp = { onChange(pages.moveGroup(pageId, g.id, -1)) }, onDown = { onChange(pages.moveGroup(pageId, g.id, 1)) })
                }
                if (g.auto) Text("Adds the guests by itself; it cannot be moved into or emptied here.", fontSize = LabScale.textSm, color = Lab.muted, modifier = Modifier.padding(top = LabScale.space1))
                else Row(Modifier.fillMaxWidth().toggleable(value = g.hidden, enabled = enabled, role = Role.Switch) { v -> onChange(pages.editGroup(g.id) { it.copy(hidden = v) }) }
                    .padding(vertical = LabScale.space1), verticalAlignment = Alignment.CenterVertically) {
                    Text("Hide this group", modifier = Modifier.weight(1f), fontSize = LabScale.textMd)
                    Switch(checked = g.hidden, onCheckedChange = null, enabled = enabled)
                }
                g.cards.forEachIndexed { ci, c ->
                    CardEditor(c, enabled, first = ci == 0, last = ci == g.cards.lastIndex,
                        onEdit = { edit -> onChange(pages.editCard(c.id, edit)) },
                        onUp = { onChange(pages.moveCard(g.id, c.id, -1)) }, onDown = { onChange(pages.moveCard(g.id, c.id, 1)) },
                        onMove = { onMove(c.id) })
                }
            }
        }
    }
}

@Composable
private fun PageLook(page: LayoutPage, enabled: Boolean, edit: ((LayoutPage) -> LayoutPage) -> Unit) {
    Panel("") {
        OutlinedTextField(page.title, { v -> edit { it.copy(title = v.take(60)) } }, singleLine = true, enabled = enabled,
            label = { Text("Page title") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(page.icon, { v -> edit { it.copy(icon = iconName(v)) } }, singleLine = true, enabled = enabled,
            label = { Text("Icon (a name like proxmox)") }, modifier = Modifier.fillMaxWidth().padding(top = LabScale.space2))
        Text("Columns", fontSize = LabScale.textSm, color = Lab.sub, modifier = Modifier.padding(top = LabScale.space2))
        Chips(listOf("Auto" to 0, "1" to 1, "2" to 2, "3" to 3, "4" to 4, "5" to 5, "6" to 6), page.columns, enabled) { v -> edit { it.copy(columns = v) } }
        Text("Density", fontSize = LabScale.textSm, color = Lab.sub, modifier = Modifier.padding(top = LabScale.space2))
        Chips(listOf("Default" to "", "Compact" to "compact", "Normal" to "normal"), page.density, enabled) { v -> edit { it.copy(density = v) } }
    }
}

@Composable
private fun CardEditor(c: LayoutCard, enabled: Boolean, first: Boolean, last: Boolean, onEdit: ((LayoutCard) -> LayoutCard) -> Unit,
                       onUp: () -> Unit, onDown: () -> Unit, onMove: () -> Unit) {
    var open by remember(c.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(top = LabScale.space2)) {
        Row(Modifier.fillMaxWidth().clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(vertical = LabScale.space2)) {
                Text(c.name, fontSize = LabScale.textMd, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (c.hidden) Lab.muted else Lab.text)
                val bits = listOfNotNull(c.size.uppercase().ifEmpty { null }, if (c.hidden) "hidden" else null, c.icon.ifEmpty { null })
                if (bits.isNotEmpty()) Text(bits.joinToString(" · "), fontSize = LabScale.textSm, color = Lab.muted)
            }
            Arrows(up = enabled && !first, down = enabled && !last, onUp = onUp, onDown = onDown)
            Icon(if (open) LabIcons.chevronUp else LabIcons.chevronDown, null, tint = Lab.muted)
        }
        if (open) Column(Modifier.padding(start = LabScale.space2, bottom = LabScale.space2)) {
            Text("Size", fontSize = LabScale.textSm, color = Lab.sub)
            Chips(listOf("Auto" to "", "S" to "s", "M" to "m", "L" to "l", "XL" to "xl"), c.size, enabled) { v -> onEdit { it.copy(size = v) } }
            OutlinedTextField(c.icon, { v -> onEdit { it.copy(icon = iconName(v)) } }, singleLine = true, enabled = enabled,
                label = { Text("Icon") }, modifier = Modifier.fillMaxWidth().padding(top = LabScale.space2))
            Row(Modifier.fillMaxWidth().toggleable(value = c.hidden, enabled = enabled, role = Role.Switch) { v -> onEdit { it.copy(hidden = v) } }
                .padding(vertical = LabScale.space2), verticalAlignment = Alignment.CenterVertically) {
                Text("Hide this card", modifier = Modifier.weight(1f), fontSize = LabScale.textMd)
                Switch(checked = c.hidden, onCheckedChange = null, enabled = enabled)
            }
            TextButton(onClick = onMove, enabled = enabled) { Text("Move to…") }
        }
    }
}

/** An icon name as the server takes it: lowercase letters, digits and dashes. */
internal fun iconName(text: String): String = text.lowercase().filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(40)

@Composable
private fun <T> Chips(options: List<Pair<String, T>>, selected: T, enabled: Boolean, onPick: (T) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(LabScale.space2)) {
        options.forEach { (label, value) -> FilterChip(selected = value == selected, onClick = { onPick(value) }, label = { Text(label) }, enabled = enabled) }
    }
}

@Composable
private fun Arrows(up: Boolean, down: Boolean, onUp: () -> Unit, onDown: () -> Unit) {
    IconButton(onClick = onUp, enabled = up) { Icon(LabIcons.chevronUp, "Up", Modifier.size(LabScale.iconCard)) }
    IconButton(onClick = onDown, enabled = down) { Icon(LabIcons.chevronDown, "Down", Modifier.size(LabScale.iconCard)) }
}

/** Where a card goes: a page, then one of its groups or a new group (made with a title). */
@Composable
fun MoveCardDialog(pages: List<LayoutPage>, cardId: String, onDone: (List<LayoutPage>) -> Unit, onDismiss: () -> Unit) {
    val here = pages.firstOrNull { p -> p.groups.any { g -> g.cards.any { it.id == cardId } } }
    var pageId by remember { mutableStateOf(here?.id ?: pages.firstOrNull()?.id.orEmpty()) }
    var groupId by remember { mutableStateOf<String?>(here?.groups?.firstOrNull { g -> g.cards.any { it.id == cardId } }?.id) }   // null: a new group
    var title by remember { mutableStateOf("") }
    val page = pages.firstOrNull { it.id == pageId }
    val groups = page?.groups.orEmpty().filter { !it.auto }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move ${pages.flatMap { p -> p.groups.flatMap { it.cards } }.firstOrNull { it.id == cardId }?.name ?: "the card"}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Page", fontSize = LabScale.textSm, color = Lab.sub)
                Chips(pages.map { it.title to it.id }, pageId, true) { id ->
                    pageId = id
                    groupId = pages.firstOrNull { it.id == id }?.groups.orEmpty().firstOrNull { !it.auto }?.id
                }
                Text("Group", fontSize = LabScale.textSm, color = Lab.sub, modifier = Modifier.padding(top = LabScale.space3))
                groups.forEach { g ->
                    Row(Modifier.fillMaxWidth().selectable(selected = groupId == g.id, role = Role.RadioButton) { groupId = g.id }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = groupId == g.id, onClick = null, modifier = Modifier.padding(LabScale.space2)); Text(g.title, fontSize = LabScale.textMd)
                    }
                }
                Row(Modifier.fillMaxWidth().selectable(selected = groupId == null, role = Role.RadioButton) { groupId = null }, verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = groupId == null, onClick = null, modifier = Modifier.padding(LabScale.space2)); Text("A new group", fontSize = LabScale.textMd)
                }
                if (groupId == null) OutlinedTextField(title, { title = it.take(60) }, singleLine = true, label = { Text("Group title") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(enabled = page != null, onClick = { onDone(pages.moveCardTo(cardId, pageId, groupId, title)) }) { Text("Move") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
