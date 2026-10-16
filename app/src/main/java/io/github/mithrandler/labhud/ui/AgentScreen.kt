package io.github.mithrandler.labhud.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
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
                            if (choices.isEmpty()) Text("Nothing found to tick.", fontSize = 12.sp, color = Lab.muted, modifier = Modifier.padding(top = 6.dp))
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
                        }
                    }
                }
            }
        }
    }
    GateDialogs(gate, onSignIn)
}
