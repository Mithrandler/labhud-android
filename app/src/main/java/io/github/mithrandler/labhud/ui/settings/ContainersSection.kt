package io.github.mithrandler.labhud.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mithrandler.labhud.data.AgentRow
import io.github.mithrandler.labhud.data.LabRepository
import io.github.mithrandler.labhud.data.Registries
import io.github.mithrandler.labhud.data.UpdateWindow
import io.github.mithrandler.labhud.data.agentsAutoUpdate
import io.github.mithrandler.labhud.data.holdCritical
import io.github.mithrandler.labhud.data.updateWindow
import io.github.mithrandler.labhud.data.words
import io.github.mithrandler.labhud.data.registries
import io.github.mithrandler.labhud.data.removeRegistry
import io.github.mithrandler.labhud.data.saveRegistry
import io.github.mithrandler.labhud.ui.GateDialogs
import io.github.mithrandler.labhud.ui.Lab
import io.github.mithrandler.labhud.ui.LabIcons
import io.github.mithrandler.labhud.ui.Panel
import io.github.mithrandler.labhud.ui.isRefusal
import io.github.mithrandler.labhud.ui.refusalText
import io.github.mithrandler.labhud.ui.rememberGate
import kotlinx.coroutines.launch

/**
 * The containers section: load behind the gate (admin, then the PIN or a fingerprint), an "Unlock"
 * panel until then, a message line, the Docker-capable agents, how many containers each has and how many update on their own,
 * the registries and the image scans ([RegistriesPanel], [ScansPanel]), and the gate's dialogs.
 */
@Composable
fun ContainersSection(repo: LabRepository, nav: SettingsNav) {
    val scope = rememberCoroutineScope()
    val gate = rememberGate(repo)
    var agents by remember { mutableStateOf<List<AgentRow>?>(null) }
    var regs by remember { mutableStateOf<Registries?>(null) }
    var window by remember { mutableStateOf<UpdateWindow?>(null) }
    var agentsAuto by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }

    fun load() = scope.launch {
        val api = repo.api() ?: return@launch
        message = ""
        runCatching { api.agents() }.onSuccess { a ->
            if (a.ok) {
                agents = a.agents.filter { "docker" in it.caps }
                window = a.window
                agentsAuto = a.autoUpdate
            }
            else {   // a refusal is not "no agent": say what to do, and read the rights again
                message = refusalText(a.error, repo.rights.value)
                if (isRefusal(a.error)) repo.refreshRights()
            }
        }.onFailure { message = it.message ?: "Not loaded." }
        runCatching { api.registries() }.onSuccess { r -> if (r.ok) regs = r else message = r.error.ifBlank { "Registries refused." } }
    }
    /** A change behind the gate: its refusal in the message line, else the section anew. */
    fun change(what: String, call: suspend () -> Pair<Boolean, String>?) = gate.press {
        scope.launch {
            val (ok, error) = runCatching { call() }.getOrElse { false to (it.message ?: "") } ?: return@launch
            if (ok) load() else message = "$what: " + error.ifBlank { "refused" }
        }
    }
    LaunchedEffect(Unit) { gate.press { load() } }

    SettingsPage(section("containers")!!, nav) {
        if (agents == null) item { Unlock { gate.press { load() } } }
        if (message.isNotEmpty()) item { Text(message, color = Lab.warn, fontSize = 13.sp) }
        agents?.let { list ->
            item {
                val containers = list.sumOf { (it.targets["docker"] ?: emptyList()).size }
                Panel("Docker hosts", "${list.size} hosts · $containers containers · ${list.count { !it.online }} offline") {
                    Text("Each Docker host's containers: which update on their own, and cleaning unused images.", fontSize = 13.sp, color = Lab.sub)
                    if (list.isEmpty()) Text("No agent runs Docker yet.", color = Lab.muted, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                    list.forEach { a ->
                        Row(Modifier.fillMaxWidth().clickable { nav.agent(a.id) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(LabIcons.container, null, tint = if (a.online) Lab.accent else Lab.muted, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(a.name, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                Text(if (a.online) "${(a.targets["docker"] ?: emptyList()).size} containers · ${a.auto.size} update on their own" else "offline", fontSize = 12.sp, color = if (a.online) Lab.muted else Lab.red)
                            }
                            Icon(LabIcons.chevronRight, null, tint = Lab.muted)
                        }
                    }
                }
            }
            window?.let { w ->
                item {
                    UpdateWindowPanel(w, agentsAuto, true) { days, at, auto ->
                        change("Not saved") {
                            val api = repo.api() ?: return@change null
                            val r = api.updateWindow(days, at)
                            if (r.ok && auto != agentsAuto) api.agentsAutoUpdate(auto).let { it.ok to it.error } else r.ok to r.error
                        }
                    }
                }
            }
            regs?.let { r ->
                item {
                    RegistriesPanel(r,
                        onSave = { host, user, token -> change("Not saved") { repo.api()?.saveRegistry(host, user, token)?.let { it.ok to it.error } } },
                        onRemove = { reg -> change("Not removed") { repo.api()?.removeRegistry(reg.host)?.let { it.ok to it.error } } })
                }
                item { ScansPanel(r) { on -> change("Not saved") { repo.api()?.holdCritical(on)?.let { it.ok to it.error } } } }
            }
        }
    }
    GateDialogs(gate, nav.signIn)
}

/**
 * When the containers ticked "update on their own" are updated: the days (tap to tick, one stays
 * on) and the time of day on the server's clock; and whether the agents update themselves.
 * Kept here until Save sends them.
 */
@Composable
private fun UpdateWindowPanel(w: UpdateWindow, agentsAuto: Boolean, enabled: Boolean, onSave: (List<Int>, String, Boolean) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val names = listOf("M", "T", "W", "T", "F", "S", "S")
    val full = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    var days by remember(w) { mutableStateOf(w.days.toSet()) }
    var at by remember(w) { mutableStateOf(w.at) }
    var auto by remember(agentsAuto) { mutableStateOf(agentsAuto) }
    val changed = days != w.days.toSet() || at != w.at || auto != agentsAuto
    Panel("Update window", w.words()) {
        Text("The containers ticked “on their own” are updated then, on the server's clock.", fontSize = 13.sp, color = Lab.sub)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            names.forEachIndexed { i, n ->
                val on = i in days
                androidx.compose.material3.FilterChip(
                    selected = on,
                    onClick = { val next = if (on) days - i else days + i; if (next.isNotEmpty()) days = next },
                    label = { Text(n) },
                    modifier = Modifier.semantics { contentDescription = full[i] },
                )
            }
        }
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("At", fontSize = 13.sp, color = Lab.sub)
            androidx.compose.material3.OutlinedButton(onClick = {
                val (h, m) = at.split(":").let { (it.getOrNull(0)?.toIntOrNull() ?: 4) to (it.getOrNull(1)?.toIntOrNull() ?: 0) }
                android.app.TimePickerDialog(context, { _, hh, mm -> at = "%02d:%02d".format(hh, mm) }, h, m, true).show()
            }) { Text(at) }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text("Update agents too", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Text("An agent with an older version updates itself when it dials in.", fontSize = 12.sp, color = Lab.muted)
            }
            androidx.compose.material3.Switch(checked = auto, onCheckedChange = { auto = it })
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.Button(onClick = { onSave(days.sorted(), at, auto) }, enabled = enabled && changed) { Text("Save") }
            if (changed) androidx.compose.material3.OutlinedButton(onClick = { days = w.days.toSet(); at = w.at; auto = agentsAuto }, enabled = enabled) { Text("Discard") }
        }
    }
}
