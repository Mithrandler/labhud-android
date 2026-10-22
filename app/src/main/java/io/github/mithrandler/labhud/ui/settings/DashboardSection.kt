package io.github.mithrandler.labhud.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mithrandler.labhud.data.DiscoverList
import io.github.mithrandler.labhud.data.LabRepository
import io.github.mithrandler.labhud.data.discoverApprove
import io.github.mithrandler.labhud.data.discoverDismiss
import io.github.mithrandler.labhud.data.discoverRestore
import io.github.mithrandler.labhud.data.LayoutPage
import io.github.mithrandler.labhud.data.discovered
import io.github.mithrandler.labhud.data.kept
import io.github.mithrandler.labhud.data.layout
import io.github.mithrandler.labhud.data.saveLayout
import io.github.mithrandler.labhud.ui.GateDialogs
import io.github.mithrandler.labhud.ui.Lab
import io.github.mithrandler.labhud.ui.Panel
import io.github.mithrandler.labhud.ui.rememberGate
import kotlinx.coroutines.launch

/**
 * The pages, groups and cards as the wall's ⚙ > Dashboard edits them: order (↑ ↓), titles, icons, sizes, hidden, a card moved to
 * another group or page. Edits are kept here until Save sends them, all together (the server applies a layout as a whole); Discard
 * drops them. Under it, what LabHUD sees and has no card for (Proxmox guests, containers, services a network scan found):
 * tick some and add them as cards, or stop offering them. Behind the gate, as every ⚙ change.
 */
@Composable
fun DashboardSection(repo: LabRepository, nav: SettingsNav) {
    val scope = rememberCoroutineScope()
    val gate = rememberGate(repo)
    var list by remember { mutableStateOf<DiscoverList?>(null) }
    var picked by remember { mutableStateOf(setOf<String>()) }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf<List<LayoutPage>?>(null) }     // as the server has it
    var pages by remember { mutableStateOf<List<LayoutPage>?>(null) }     // as edited here
    var openPage by remember { mutableStateOf<String?>(null) }
    var moving by remember { mutableStateOf<String?>(null) }
    val dirty = pages != null && pages != saved
    BackHandler(openPage != null) { openPage = null }

    fun load(force: Boolean = false) = scope.launch {
        val api = repo.api() ?: return@launch
        runCatching { api.layout() }.onSuccess { l ->
            if (l.ok) { if (force || !dirty) pages = l.pages; saved = l.pages; if (openPage != null && l.pages.none { it.id == openPage }) openPage = null }
            else message = l.error.ifBlank { "Layout refused." }
        }
        runCatching { api.discovered() }.onSuccess { r ->
            if (r.ok) { list = r; picked = picked.filter { k -> r.items.any { it.key == k } }.toSet() } else message = r.error.ifBlank { "Refused." }
        }.onFailure { message = it.message ?: "Not loaded." }
    }
    /** One change behind the gate; its refusal in the message line, else the list anew. */
    fun change(what: String, call: suspend (io.github.mithrandler.labhud.data.LabApi) -> io.github.mithrandler.labhud.data.ActionAnswer) = gate.press {
        busy = true
        scope.launch {
            val api = repo.api()
            val r = api?.let { runCatching { call(it) }.getOrNull() }
            message = if (r?.ok == true) "" else "$what: " + (r?.error?.ifBlank { null } ?: "refused")
            if (r?.ok == true) picked = emptySet()
            load().join()
            busy = false
        }
    }
    LaunchedEffect(Unit) { gate.press { load() } }

    fun save() = gate.press {
        val send = pages?.kept() ?: return@press
        busy = true
        scope.launch {
            val r = repo.api()?.let { api -> runCatching { api.saveLayout(send) }.getOrNull() }
            message = if (r?.ok == true) "" else "Not saved: " + (r?.error?.ifBlank { null } ?: "refused")
            if (r?.ok == true) { pages = null; saved = null }
            load(force = true).join()
            busy = false
        }
    }

    SettingsPage(section("dashboard")!!, nav) {
        if (list == null && pages == null) item { Unlock { gate.press { load() } } }
        if (message.isNotEmpty()) item { Text(message, color = Lab.warn, fontSize = 13.sp) }
        if (dirty) item {
            Panel("") {
                Text("Changes not saved yet.", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { save() }, enabled = !busy) { Text("Save") }
                    OutlinedButton(onClick = { pages = saved }, enabled = !busy) { Text("Discard") }
                }
            }
        }
        pages?.let { ps ->
            val open = openPage
            if (open == null) pagesList(ps, !busy, { pages = it }) { openPage = it }
            else {
                item { TextButton(onClick = { openPage = null }) { Text("‹ All pages") } }
                pageEditor(ps, open, !busy, { pages = it }) { moving = it }
            }
        }
        list?.let { l ->
            item {
                Panel("Discovered · to approve", if (l.items.isEmpty()) "Nothing new" else "${l.items.size} to approve") {
                    Text("Found, with no card yet. A card added here lands on the page \"discovered\"; move it from the wall's ⚙ > Dashboard.",
                        fontSize = 13.sp, color = Lab.sub)
                    if (l.items.isEmpty()) Text("Nothing new.", color = Lab.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                    l.items.forEach { d ->
                        Row(
                            Modifier.fillMaxWidth().toggleable(value = d.key in picked, enabled = !busy, role = Role.Checkbox) { want ->
                                picked = if (want) picked + d.key else picked - d.key
                            }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = d.key in picked, onCheckedChange = null, enabled = !busy)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(d.name, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text(listOf(d.kind, d.detail).filter { it.isNotEmpty() }.joinToString(" · "), fontSize = 12.sp, color = Lab.muted)
                            }
                        }
                    }
                    if (l.items.isNotEmpty()) Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = !busy && picked.isNotEmpty(), onClick = { val keys = picked.toList(); change("Not added") { it.discoverApprove(keys) } }) {
                            Text("Add ${picked.size}")
                        }
                        OutlinedButton(enabled = !busy && picked.isNotEmpty(), onClick = { val keys = picked.toList(); change("Not dismissed") { it.discoverDismiss(keys) } }) {
                            Text("Dismiss")
                        }
                        TextButton(enabled = !busy, onClick = { picked = if (picked.size == l.items.size) emptySet() else l.items.map { it.key }.toSet() }) {
                            Text(if (picked.size == l.items.size) "None" else "All")
                        }
                    }
                    if (l.dismissed > 0) TextButton(enabled = !busy, onClick = { change("Not restored") { it.discoverRestore() } }) {
                        Text("Offer the ${l.dismissed} dismissed again")
                    }
                }
            }
        }
    }
    moving?.let { id -> pages?.let { ps -> MoveCardDialog(ps, id, { pages = it; moving = null }) { moving = null } } }
    GateDialogs(gate, nav.signIn)
}
