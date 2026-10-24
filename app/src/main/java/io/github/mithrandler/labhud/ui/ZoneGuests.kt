package io.github.mithrandler.labhud.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mithrandler.labhud.data.LabApi
import io.github.mithrandler.labhud.data.ZoneAnswer
import io.github.mithrandler.labhud.data.ZoneGuest
import io.github.mithrandler.labhud.data.ZoneHost
import io.github.mithrandler.labhud.data.bytesText
import io.github.mithrandler.labhud.data.kind
import io.github.mithrandler.labhud.data.line
import io.github.mithrandler.labhud.data.newIdOk
import io.github.mithrandler.labhud.data.ordered
import io.github.mithrandler.labhud.data.refusal
import io.github.mithrandler.labhud.data.snapConfirmWord
import io.github.mithrandler.labhud.data.snapNameOk
import io.github.mithrandler.labhud.data.title
import io.github.mithrandler.labhud.data.versionText
import io.github.mithrandler.labhud.data.zoneGuestAct
import io.github.mithrandler.labhud.data.zoneGuestBackups
import io.github.mithrandler.labhud.data.zoneGuestConfig
import io.github.mithrandler.labhud.data.zoneGuestMigrate
import io.github.mithrandler.labhud.data.zoneGuestSnapshots
import io.github.mithrandler.labhud.data.zoneGuestTasks
import io.github.mithrandler.labhud.data.zoneGuests
import io.github.mithrandler.labhud.data.zoneSnapCreate
import io.github.mithrandler.labhud.data.zoneSnapDelete
import io.github.mithrandler.labhud.data.zoneSnapRollback
import kotlinx.coroutines.launch

/* VMs & LXC in Servers: the guests of a Proxmox node, a sheet per guest with Config / Snapshots / Backups / Tasks. A rollback asks for the
   guest's name typed, a delete for the snapshot's; a configuration shows keys that look like secrets as ********. Snapshots and the
   backup list need VM.Snapshot and Datastore.Audit on the agent's Proxmox token (opt-in): without them the host's refusal is shown. */

private val GUEST_TABS = listOf("config" to "Config", "snapshots" to "Snapshots", "backups" to "Backups", "tasks" to "Tasks")

@Composable
internal fun GuestsTab(h: ZoneHost, ask: ZoneAsk, gate: Gate) {
    var open by remember(h.name) { mutableStateOf<ZoneGuest?>(null) }
    var tick by remember(h.name) { mutableIntStateOf(0) }
    var enabled by remember(h.name) { mutableStateOf(true) }
    var rows by remember(h.name) { mutableStateOf<List<ZoneGuest>?>(null) }
    var peers by remember(h.name) { mutableStateOf<List<String>>(emptyList()) }
    var message by remember(h.name) { mutableStateOf("") }
    LaunchedEffect(h.name, tick) {
        val r = ask { zoneGuests(h.name) }
        if (r.ok) { rows = r.guests; peers = r.peers; enabled = r.enabled; message = "" } else message = r.refusal()
    }
    Column(Modifier.fillMaxSize().padding(horizontal = LabScale.space4)) {
        Row(Modifier.padding(top = LabScale.space2)) { OutlinedButton(onClick = { tick++ }) { Text("Refresh") } }
        Said(message)
        if (!enabled) Text("Proxmox is not ticked for this host: tick it in Settings > Hosts and agents.", color = Lab.muted, fontSize = LabScale.textSm, modifier = Modifier.padding(top = LabScale.space2))
        else if (rows == null) Text("Loading…", color = Lab.muted, modifier = Modifier.padding(top = LabScale.space3))
        else if (rows.orEmpty().isEmpty()) Text("No guest on this node yet.", color = Lab.muted, modifier = Modifier.padding(top = LabScale.space3))
        Column(Modifier.verticalScroll(rememberScrollState())) {
            rows.orEmpty().ordered().forEach { g ->
                Row2("${g.id} · ${g.name}", g.line(), if (g.running) "running" else if (g.template) "template" else "stopped", tone = if (g.ticked) 0 else 1) {
                    if (g.ticked) open = g
                }
            }
        }
    }
    open?.let { g -> LabSheet(onClose = { open = null }) { GuestSheet(h.name, g, peers, ask, gate, onChanged = { tick++ }) } }
}

@Composable
private fun SnapshotDialog(guest: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("A snapshot of $guest") },
        text = { Column { Text("Letters, digits, - and _; starts with a letter (40 at most).", fontSize = LabScale.textSm, color = Lab.sub)
            OutlinedTextField(name, { name = it.take(40) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = LabScale.space2)) } },
        confirmButton = { TextButton(enabled = snapNameOk(name), onClick = { onOk(name) }) { Text("Take") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun MoveDialog(g: ZoneGuest, peers: List<String>, onDismiss: () -> Unit, onOk: (peer: String, mode: String, newId: String) -> Unit) {
    var peer by remember { mutableStateOf(peers.firstOrNull().orEmpty()) }
    val mode = if (g.running) "online" else "offline"   // a running guest moves live (an LXC restarts there); a stopped one stays stopped
    var newId by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Move ${g.name} to another node") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(LabScale.space2)) {
            Row(horizontalArrangement = Arrangement.spacedBy(LabScale.space2)) { peers.forEach { p -> FilterChip(selected = peer == p, onClick = { peer = p }, label = { Text(p) }) } }
            if (g.running) Text(if (g.type == "lxc") "It restarts on the other node." else "It keeps running while it moves.", fontSize = LabScale.textSm, color = Lab.sub)
            OutlinedTextField(newId, { newId = it.filter(Char::isDigit).take(9) }, label = { Text("Number there (empty: ${g.id})") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("The copy is made on the other node. The original stays here, locked as “migrate”: remove it after you have checked the copy.", fontSize = LabScale.textSm, color = Lab.sub)
        } },
        confirmButton = { TextButton(enabled = peer.isNotEmpty() && newIdOk(newId), onClick = { onOk(peer, mode, newId) }) { Text("Move") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun GuestSheet(host: String, g: ZoneGuest, peers: List<String>, ask: ZoneAsk, gate: Gate, onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    var tab by remember(g.id) { mutableStateOf("config") }
    var said by remember(g.id) { mutableStateOf("") }
    var bad by remember(g.id) { mutableStateOf(false) }
    var busy by remember(g.id) { mutableStateOf(false) }
    var shutting by remember(g.id) { mutableStateOf(false) }
    var making by remember(g.id) { mutableStateOf(false) }
    var moving by remember(g.id) { mutableStateOf(false) }
    var again by remember(g.id) { mutableIntStateOf(0) }          // the snapshot list asks again after a change

    fun run(label: String, call: suspend LabApi.() -> ZoneAnswer, after: () -> Unit = {}) = gate.press {
        busy = true; said = "$label…"; bad = false
        scope.launch {
            val r = ask(call)
            if (r.ok) { said = "$label done"; after() } else { said = "$label: ${r.refusal()}"; bad = true }
            busy = false
            onChanged()
        }
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = LabScale.space4).padding(bottom = LabScale.space5).heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.9f).dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(LabScale.space2)) {
        Text(g.name, fontSize = LabScale.textWallNum, fontWeight = FontWeight.SemiBold, color = Lab.text, modifier = Modifier.padding(end = LabScale.touch))
        Text("${g.id} · ${g.kind()} · " + if (g.running) "running" else "stopped", fontSize = LabScale.textSm, color = Lab.muted)
        Row(horizontalArrangement = Arrangement.spacedBy(LabScale.space2)) {
            OutlinedButton(enabled = "start" in g.can && !g.running && !busy, onClick = { run("Start", { zoneGuestAct(host, g.id, "start") }) }) { Text("Start") }
            OutlinedButton(enabled = "shutdown" in g.can && g.running && !busy, onClick = { shutting = true }) { Text("Shut down", color = Lab.red) }
            OutlinedButton(enabled = "reboot" in g.can && g.running && !busy, onClick = { run("Reboot", { zoneGuestAct(host, g.id, "reboot") }) }) { Text("Reboot") }
            if (g.moves) OutlinedButton(enabled = !busy, onClick = { moving = true }) { Text("Move") }
        }
        if (g.console) Text("Its console opens in Servers on the web (the app has no terminal).", fontSize = LabScale.textSm, color = Lab.muted)
        Row(horizontalArrangement = Arrangement.spacedBy(LabScale.space2)) {
            GUEST_TABS.forEach { (id, label) -> FilterChip(selected = tab == id, onClick = { tab = id }, label = { Text(label) }) }
        }
        when (tab) {
            "config" -> Listing(g.id, ask, { zoneGuestConfig(host, g.id) }, { it.config }, "No configuration came back.") { x ->
                Row(Modifier.fillMaxWidth().padding(vertical = LabScale.spaceHalf)) {
                    Text(x.key, fontSize = LabScale.textSm, color = Lab.muted, modifier = Modifier.weight(0.4f))
                    Text(x.value, fontSize = LabScale.textSm, color = Lab.text, modifier = Modifier.weight(0.6f))
                }
            }
            "snapshots" -> {
                OutlinedButton(enabled = !busy, onClick = { making = true }) { Text("Take a snapshot") }
                Listing(g.id + again, ask, { zoneGuestSnapshots(host, g.id) }, { it.snapshots }, "No snapshot yet. Taking one needs VM.Snapshot on the agent's Proxmox token.") { x ->
                    var asking by remember { mutableStateOf("") }
                    Column(Modifier.fillMaxWidth().padding(vertical = LabScale.space1)) {
                        Text(x.name + if (x.description.isNotBlank()) " · ${x.description}" else "", fontSize = LabScale.textMd, color = Lab.text)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(versionText(x.time), fontSize = LabScale.textSm, color = Lab.muted, modifier = Modifier.weight(1f))
                            TextButton(enabled = !busy, onClick = { asking = "rollback" }) { Text("Roll back", color = Lab.red) }
                            TextButton(enabled = !busy, onClick = { asking = "delete" }) { Text("Delete", color = Lab.red) }
                        }
                    }
                    if (asking.isNotEmpty()) {
                        val word = snapConfirmWord(asking, g.name, x.name)
                        val title = if (asking == "rollback") "Roll ${g.name} back to ${x.name}? What it did since is lost." else "Delete the snapshot ${x.name}?"
                        TypedConfirm(title, word, if (asking == "rollback") "Roll back" else "Delete", onOk = {
                            if (asking == "rollback") run("Roll back", { zoneSnapRollback(host, g.id, x.name, word) }) { again++ }
                            else run("Delete", { zoneSnapDelete(host, g.id, x.name, word) }) { again++ }
                        }, onDismiss = { asking = "" })
                    }
                }
            }
            "backups" -> Listing(g.id, ask, { zoneGuestBackups(host, g.id) }, { it.backups }, "No backup found. Listing them needs Datastore.Audit on the agent's Proxmox token.") { x ->
                Column(Modifier.fillMaxWidth().padding(vertical = LabScale.space1)) {
                    Text(x.title() + if (x.protected) " · protected" else "", fontSize = LabScale.textMd, color = Lab.text)
                    Text("${x.storage} · ${bytesText(x.size.toDouble())} · ${versionText(x.time)}" + if (x.notes.isNotBlank()) " · ${x.notes}" else "", fontSize = LabScale.textSm, color = Lab.muted)
                }
            }
            else -> Listing(g.id, ask, { zoneGuestTasks(host, g.id) }, { it.tasks }, "No task for this guest yet.") { x ->
                Column(Modifier.fillMaxWidth().padding(vertical = LabScale.space1)) {
                    Text("${x.type} · ${x.status}", fontSize = LabScale.textMd, color = if (x.status == "OK") Lab.text else Lab.red)
                    Text("${x.user} · ${versionText(x.start)}", fontSize = LabScale.textSm, color = Lab.muted)
                }
            }
        }
        if (said.isNotEmpty()) Text(said, color = if (bad) Lab.red else Lab.sub, fontSize = LabScale.textSm)
    }
    if (shutting) TypedConfirm("Shut down ${g.name}?", g.name, "Shut down", onOk = { run("Shut down", { zoneGuestAct(host, g.id, "shutdown") }) }, onDismiss = { shutting = false })
    if (moving) MoveDialog(g, peers, onDismiss = { moving = false }) { peer, mode, newId ->
        moving = false
        // the move runs on the host for as long as it takes: the answer is only that it started (labhud says when it ends)
        gate.press {
            busy = true; said = "Moving…"; bad = false
            scope.launch {
                val r = ask { zoneGuestMigrate(host, g.id, peer, mode, newId) }
                if (r.ok) said = "The move started. It runs on the host and can take a long time; Tasks shows it, and labhud says when it ends." else { said = "Move: ${r.refusal()}"; bad = true }
                busy = false
            }
        }
    }
    if (making) SnapshotDialog(g.name, onDismiss = { making = false }) { name -> making = false; run("Snapshot", { zoneSnapCreate(host, g.id, name) }) { again++ } }
}

/** A list the host gives for one guest: loaded when it opens (and again when [key] changes), the reason shown when it cannot. */
@Composable
private fun <T> Listing(key: Any, ask: ZoneAsk, call: suspend LabApi.() -> ZoneAnswer, pick: (ZoneAnswer) -> List<T>, empty: String, row: @Composable (T) -> Unit) {
    var rows by remember(key) { mutableStateOf<List<T>?>(null) }
    var message by remember(key) { mutableStateOf("") }
    LaunchedEffect(key) {
        val r = ask(call)
        if (r.ok) { rows = pick(r); message = "" } else message = r.refusal()
    }
    if (message.isNotEmpty()) Text(message, color = Lab.red, fontSize = LabScale.textSm)
    else if (rows == null) Text("Loading…", color = Lab.muted)
    else if (rows.orEmpty().isEmpty()) Text(empty, color = Lab.muted, fontSize = LabScale.textSm)
    rows.orEmpty().forEach { row(it) }
}
