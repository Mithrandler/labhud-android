package io.github.mithrandler.labhud.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mithrandler.labhud.data.AgentRow
import io.github.mithrandler.labhud.data.AlertSetup
import io.github.mithrandler.labhud.data.LabRepository
import io.github.mithrandler.labhud.data.Phone
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private val VIAS = listOf("ntfy" to "ntfy", "app" to "App", "both" to "Both")

/**
 * What the wall's ⚙ manages, for a phone signed in as the admin: the paired phones (Remove ends
 * one at once), the agents, and where LabHUD sends its news. Everything here goes through the
 * same routes as ⚙, so with a PIN the screen asks for it (or the fingerprint) first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageScreen(repo: LabRepository, onBack: () -> Unit, onSignIn: () -> Unit, onAgent: (String) -> Unit = {}) {
    val scope = rememberCoroutineScope()
    val gate = rememberGate(repo)
    var phones by remember { mutableStateOf<List<Phone>?>(null) }
    var agents by remember { mutableStateOf<List<AgentRow>?>(null) }
    var alerts by remember { mutableStateOf<AlertSetup?>(null) }
    var message by remember { mutableStateOf("") }
    var removing by remember { mutableStateOf<Phone?>(null) }
    var loaded by remember { mutableStateOf(false) }

    fun load() = scope.launch {
        val api = repo.api() ?: return@launch
        message = ""
        runCatching { api.phones().devices }.onSuccess { phones = it }
        runCatching { api.agents().agents }.onSuccess { agents = it }.onFailure { message = it.message ?: "" }
        runCatching { api.alertSetup() }.onSuccess { alerts = it }
        loaded = true
    }
    LaunchedEffect(Unit) { gate.press { load() } }

    Scaffold(
        containerColor = Lab.bg,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Lab.bg),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = { Text("Server management") },
            )
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!loaded) item {
                Panel("") {
                    Text("Unlock to manage this LabHUD.", color = Lab.sub, fontSize = 14.sp)
                    Button(onClick = { gate.press { load() } }, modifier = Modifier.padding(top = 8.dp)) { Text("Unlock") }
                }
            }
            if (message.isNotEmpty()) item { Text(message, color = Lab.warn, fontSize = 13.sp) }

            phones?.let { list ->
                item { Text("PHONES", style = Heading) }
                item {
                    Panel("") {
                        if (list.isEmpty()) Text("No phone paired.", color = Lab.muted, fontSize = 14.sp)
                        list.forEach { p ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(p.name, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                    Text(listOfNotNull(
                                        if (p.admin) "admin" else "looks only",
                                        if (p.push) "instant notifications" else null,
                                        p.seen.takeIf { it > 0 }?.let { "seen " + stamp(it) },
                                    ).joinToString(" · "), fontSize = 12.sp, color = Lab.muted)
                                }
                                TextButton(onClick = { removing = p }) { Text("Remove", color = Lab.red) }
                            }
                        }
                    }
                }
            }

            agents?.let { list ->
                item { Text("AGENTS", style = Heading, modifier = Modifier.padding(top = 8.dp)) }
                item {
                    Panel("") {
                        if (list.isEmpty()) Text("No agent yet: on the wall, ⚙ > Agents shows the line that installs one.", color = Lab.muted, fontSize = 14.sp)
                        list.forEach { a ->
                            Row(Modifier.fillMaxWidth().clickable { onAgent(a.id) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(if (a.online) Lab.green else Lab.red))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(a.name, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                    Text(listOf(a.address, a.version, a.caps.joinToString(", ")).filter { it.isNotEmpty() }.joinToString(" · "),
                                        fontSize = 12.sp, color = Lab.muted)
                                }
                                Text(if (a.online) "online" else "offline", fontSize = 12.sp, color = if (a.online) Lab.muted else Lab.red)
                            }
                        }
                    }
                }
            }

            alerts?.let { setup ->
                item { Text("NOTIFICATIONS FROM LABHUD", style = Heading, modifier = Modifier.padding(top = 8.dp)) }
                item {
                    Panel("") {
                        Text("Where LabHUD sends its news, for every phone.", fontSize = 13.sp, color = Lab.sub)
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            VIAS.forEachIndexed { i, (via, label) ->
                                SegmentedButton(selected = setup.notify.via == via, shape = SegmentedButtonDefaults.itemShape(i, VIAS.size), onClick = {
                                    gate.press {
                                        scope.launch {
                                            val r = runCatching { repo.api()?.saveNotify(setup, via) }.getOrNull()
                                            if (r?.ok == true) alerts = setup.copy(notify = setup.notify.copy(via = via))
                                            else message = "Not saved: " + (r?.error?.ifBlank { null } ?: "refused") + if (r?.error?.contains("take over") == true) " (on the wall: ⚙ > Advanced > Take over)" else ""
                                        }
                                    }
                                }) { Text(label) }
                            }
                        }
                        Text(
                            if (setup.ntfy.set) "ntfy: ${setup.ntfy.host ?: "set"} · ${setup.ntfy.sent} sent" +
                                (setup.ntfy.lastError?.let { " · last error: $it" } ?: "")
                            else "ntfy: no topic set (on the wall: ⚙ > Alerts, or `labhud notify set <url>`).",
                            fontSize = 12.sp, color = if (setup.ntfy.lastError != null) Lab.warn else Lab.muted,
                        )
                        if (setup.ntfy.set) TextButton(onClick = {
                            gate.press {
                                scope.launch {
                                    val r = runCatching { repo.api()?.notifyTest() }.getOrNull()
                                    message = if (r?.ok == true) "✓ Test sent to ntfy." else "✕ " + (r?.error?.ifBlank { null } ?: "not sent")
                                }
                            }
                        }) { Text("Send a test to ntfy") }
                    }
                }
            }
        }
    }

    removing?.let { p ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove ${p.name}?") },
            text = { Text("Its token stops working at once. To use it again, pair it again.") },
            confirmButton = {
                TextButton(onClick = {
                    removing = null
                    gate.press {
                        scope.launch {
                            val r = runCatching { repo.api()?.unpair(p.name) }.getOrNull()
                            if (r?.ok == true) phones = phones?.filter { it.name != p.name } else message = r?.error?.ifBlank { null } ?: "Not removed."
                        }
                    }
                }) { Text("Remove", color = Lab.red) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
        )
    }
    GateDialogs(gate, onSignIn)
}

private fun stamp(t: Long): String = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(t * 1000))
