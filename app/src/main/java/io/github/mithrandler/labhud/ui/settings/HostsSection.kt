package io.github.mithrandler.labhud.ui.settings

import androidx.compose.foundation.background
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mithrandler.labhud.data.AgentCode
import io.github.mithrandler.labhud.data.AgentRow
import io.github.mithrandler.labhud.data.agentCode
import io.github.mithrandler.labhud.data.LabRepository
import io.github.mithrandler.labhud.ui.GateDialogs
import io.github.mithrandler.labhud.ui.Lab
import io.github.mithrandler.labhud.ui.LabIcons
import io.github.mithrandler.labhud.ui.Panel
import io.github.mithrandler.labhud.ui.rememberGate
import kotlinx.coroutines.launch

/**
 * The section listing the agents connected to this LabHUD: their name, address, version, and
 * capabilities, with an online/offline dot. Tapping one opens it (there: rename, remove). "Add agent"
 * makes a pairing code and the line that installs an agent with it.
 */
@Composable
fun HostsSection(repo: LabRepository, nav: SettingsNav) {
    val scope = rememberCoroutineScope()
    val gate = rememberGate(repo)
    var agents by remember { mutableStateOf<List<AgentRow>?>(null) }
    var message by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf<AgentCode?>(null) }

    fun load() = scope.launch {
        val api = repo.api() ?: return@launch
        message = ""
        runCatching { api.agents().agents }.onSuccess { agents = it }.onFailure { message = it.message ?: "Not loaded." }
    }
    LaunchedEffect(Unit) { gate.press { load() } }
    // the sheet is open while the agent is installed: the list picks it up when it dials in
    LaunchedEffect(adding != null) {
        while (adding != null) { kotlinx.coroutines.delay(4000); load() }
    }
    fun newCode() = gate.press {
        scope.launch {
            val r = runCatching { repo.api()?.agentCode() }.getOrNull()
            if (r?.ok == true) { adding = r; message = "" } else message = "No code: " + (r?.error?.ifBlank { null } ?: "refused")
        }
    }

    SettingsPage(section("hosts")!!, nav) {
        if (agents == null) item { Unlock { gate.press { load() } } }
        if (message.isNotEmpty()) item { Text(message, color = Lab.warn, fontSize = 13.sp) }
        agents?.let { list ->
            item {
                Panel("Agents", "${list.size} agents · ${list.count { it.online }} online") {
                    if (list.isEmpty()) Text("No agent yet: Add agent makes the line that installs one.", color = Lab.muted, fontSize = 14.sp)
                    list.forEach { a ->
                        Row(Modifier.fillMaxWidth().clickable { nav.agent(a.id) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(if (a.online) Lab.green else Lab.red))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(a.name, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                Text(listOf(a.address, a.version, a.caps.joinToString(", ")).filter { it.isNotEmpty() }.joinToString(" · "),
                                    fontSize = 12.sp, color = Lab.muted)
                            }
                            Text(if (a.online) "online" else "offline", fontSize = 12.sp, color = if (a.online) Lab.muted else Lab.red)
                            Spacer(Modifier.width(6.dp))
                            Icon(LabIcons.chevronRight, null, tint = Lab.muted)
                        }
                    }
                }
            }
            item { Button(onClick = { newCode() }, modifier = Modifier.fillMaxWidth()) { Text("Add agent") } }
        }
    }
    adding?.let { AddAgentSheet(it, onNewCode = { newCode() }, onDismiss = { adding = null; load() }) }
    GateDialogs(gate, nav.signIn)
}
