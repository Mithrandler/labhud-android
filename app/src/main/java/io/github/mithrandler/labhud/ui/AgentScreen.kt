package io.github.mithrandler.labhud.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mithrandler.labhud.data.AgentRow
import io.github.mithrandler.labhud.data.Agents
import io.github.mithrandler.labhud.data.LabRepository
import kotlinx.coroutines.launch

/** What a capability lets labhud do, in words. */
private val CAP_WORDS = mapOf(
    "sensors" to "CPU, memory, disks, temperatures, network",
    "docker" to "Containers: start, stop, restart, update, logs",
    "systemd" to "Services: start, stop, restart",
    "host" to "The host: reboot, shut down, update packages",
    "wol" to "Wake-on-LAN for machines it can see",
    "pve" to "Proxmox guests: start, shut down, reboot",
    "ptero" to "Game servers (Pterodactyl): start, stop, restart",
)

/**
 * One agent, as ⚙ > Agents shows it: each capability it found, with a switch, and for those that
 * act on something (containers, guests, services, MACs) a tick per target. Every change goes
 * through the same routes as ⚙, so with a PIN the screen asks for it first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentScreen(repo: LabRepository, id: String, onBack: () -> Unit, onSignIn: () -> Unit) {
    val scope = rememberCoroutineScope()
    val gate = rememberGate(repo)
    var all by remember { mutableStateOf<Agents?>(null) }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var confirmUpdate by remember { mutableStateOf(false) }
    var confirmPrune by remember { mutableStateOf(false) }

    fun load() = scope.launch {
        runCatching { repo.api()?.agents() }.onSuccess { all = it }.onFailure { message = it.message ?: "" }
    }
    LaunchedEffect(id) { gate.press { load() } }

    fun change(run: suspend (io.github.mithrandler.labhud.data.LabApi) -> io.github.mithrandler.labhud.data.ActionAnswer?) = gate.press {
        busy = true
        scope.launch {
            val api = repo.api()
            val r = api?.let { runCatching { run(it) }.getOrNull() }
            message = if (r?.ok == true) "" else "Not saved: " + (r?.error?.ifBlank { null } ?: "refused")
            load().join()
            busy = false
        }
    }

    val a: AgentRow? = all?.agents?.firstOrNull { it.id == id }
    Scaffold(
        containerColor = Lab.bg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Lab.bg),
                navigationIcon = { IconButton(onClick = onBack) { Icon(LabIcons.back, "Back") } },
                title = { Text(a?.name ?: "Agent") },
            )
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (message.isNotEmpty()) item { Text(message, color = Lab.warn, fontSize = 13.sp) }
            if (a == null) {
                item { Text(if (all == null) "Unlock to see the agent." else "This agent is gone.", color = Lab.muted) }
                return@LazyColumn
            }
            item {
                Text(listOf(if (a.online) "online" else "offline", a.address, a.version).filter { it.isNotEmpty() }.joinToString(" · "),
                    color = if (a.online) Lab.sub else Lab.red, fontSize = 13.sp)
            }
            if (a.update.isNotEmpty()) item {
                Panel("") {
                    Text("v${a.update} is out (this one is v${a.version}).", fontSize = 14.sp)
                    Text("It downloads the release from GitHub, checks it, and starts again in a few seconds.",
                        fontSize = 12.sp, color = Lab.muted)
                    Button(onClick = { confirmUpdate = true }, enabled = !busy && a.online, modifier = Modifier.padding(top = 8.dp)) {
                        Icon(LabIcons.update, null, Modifier.size(18.dp)); Text("  Update to v${a.update}")
                    }
                }
            } else if (all!!.release.isNotEmpty() && a.version.isNotEmpty() && a.version != all!!.release) item {
                Text(if (a.install == "container") "In a container the image is the agent: your image updater updates it."
                    else "v${all!!.release} is out. This agent is too old to update itself: run its install line again.",
                    fontSize = 12.sp, color = Lab.muted)
            }
            val caps = all!!.caps.filter { it in a.found }
            if (caps.isEmpty()) item { Text("It found nothing it could do on its host.", color = Lab.muted) }
            caps.forEach { cap ->
                val on = cap in a.caps
                item(key = "cap-$cap") {
                    Panel("") {
                        Row(
                            Modifier.fillMaxWidth().toggleable(value = on, enabled = !busy, role = Role.Switch) { want ->
                                change { api -> api.agentCaps(a.id, if (want) a.caps + cap else a.caps - cap) }
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(cap, fontWeight = FontWeight.SemiBold)
                                Text(CAP_WORDS[cap] ?: "", fontSize = 12.sp, color = Lab.muted)
                            }
                            Switch(checked = on, onCheckedChange = null, enabled = !busy)
                        }
                        if (on && cap in all!!.verbs) {
                            val ticked = a.targets[cap].orEmpty()
                            val choices = (a.choices[cap].orEmpty() + ticked).distinct()
                            if (choices.isEmpty() && cap != "wol") Text("Nothing found to tick.", fontSize = 12.sp, color = Lab.muted, modifier = Modifier.padding(top = 6.dp))
                            choices.forEach { t ->
                                val label = a.labels[cap]?.get(t)
                                Row(
                                    Modifier.fillMaxWidth().toggleable(value = t in ticked, enabled = !busy, role = Role.Checkbox) { want ->
                                        change { api -> api.agentTargets(a.id, cap, if (want) ticked + t else ticked - t) }
                                    }.padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(checked = t in ticked, onCheckedChange = null, enabled = !busy)
                                    Text(if (label.isNullOrEmpty()) t else "$label  ·  $t", fontSize = 14.sp, maxLines = 1,
                                        overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp))
                                }
                            }
                            // a machine that is off is not in the ARP table: its MAC is typed in, as in ⚙
                            if (cap == "wol") MacField(enabled = !busy) { mac ->
                                if (mac !in ticked) change { api -> api.agentTargets(a.id, cap, ticked + mac) }
                            }
                            if (cap == "docker") DockerExtras(a, ticked.filter { it != "images" }, busy,
                                onAuto = { list -> change { api -> api.agentAuto(a.id, list) } },
                                onPrune = { confirmPrune = true })
                        }
                    }
                }
            }
        }
    }
    if (confirmUpdate && a != null) AlertDialog(
        onDismissRequest = { confirmUpdate = false },
        title = { Text("Update ${a.name}?") },
        text = { Text("To v${a.update}. It goes offline for a few seconds while it starts again.") },
        confirmButton = { TextButton(onClick = {
            confirmUpdate = false
            change { api -> api.agentUpdate(a.id) }
        }) { Text("Update") } },
        dismissButton = { TextButton(onClick = { confirmUpdate = false }) { Text("Cancel") } },
    )
    if (confirmPrune && a != null) AlertDialog(
        onDismissRequest = { confirmPrune = false },
        title = { Text("Clean unused images on ${a.name}?") },
        text = { Text("Images no container uses, unused for a day at least. An image a stopped container uses stays.") },
        confirmButton = { TextButton(onClick = {
            confirmPrune = false
            gate.press {
                busy = true
                scope.launch {
                    val r = runCatching { repo.api()?.prune(a.name) }.getOrNull()
                    message = if (r?.ok == true) "Cleaned: ${r.output}" else "Not cleaned: " + (r?.error?.ifBlank { null } ?: "refused")
                    busy = false
                }
            }
        }) { Text("Clean") } },
        dismissButton = { TextButton(onClick = { confirmPrune = false }) { Text("Cancel") } },
    )
    GateDialogs(gate, onSignIn)
}

/** Under Docker: the containers labhud updates on its own (in the window set in ⚙), and the button
    that cleans the unused images when "images" is ticked. */
@Composable
private fun DockerExtras(a: AgentRow, containers: List<String>, busy: Boolean, onAuto: (List<String>) -> Unit, onPrune: () -> Unit) {
    if (containers.isNotEmpty()) {
        Text("Update on their own", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
        Text("When their image has a newer one, in the window set in ⚙ on the wall.", fontSize = 12.sp, color = Lab.muted)
        containers.forEach { t ->
            Row(
                Modifier.fillMaxWidth().toggleable(value = t in a.auto, enabled = !busy, role = Role.Checkbox) { want ->
                    onAuto(if (want) a.auto + t else a.auto - t)
                }.padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = t in a.auto, onCheckedChange = null, enabled = !busy)
                Text(t, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
    if (a.prune) OutlinedButton(onClick = onPrune, enabled = !busy && a.online, modifier = Modifier.padding(top = 10.dp)) {
        Text("Clean unused images")
    }
}

/** "aa:bb:cc:dd:ee:ff", also typed with dashes or in capitals; null for anything else. */
internal fun normalMac(text: String): String? =
    text.trim().lowercase().replace('-', ':').takeIf { Regex("[0-9a-f]{2}(:[0-9a-f]{2}){5}").matches(it) }

@Composable
private fun MacField(enabled: Boolean, onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val mac = normalMac(text)
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            text, { text = it }, Modifier.weight(1f), singleLine = true, enabled = enabled,
            placeholder = { Text("Add a MAC (aa:bb:cc:dd:ee:ff)") },
            isError = text.isNotBlank() && mac == null,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { mac?.let { onAdd(it); text = "" } }),
        )
        TextButton(onClick = { mac?.let { onAdd(it); text = "" } }, enabled = enabled && mac != null) { Text("Add") }
    }
}
