package io.github.mithrandler.labhud.ui.settings

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
import io.github.mithrandler.labhud.data.discovered
import io.github.mithrandler.labhud.ui.GateDialogs
import io.github.mithrandler.labhud.ui.Lab
import io.github.mithrandler.labhud.ui.Panel
import io.github.mithrandler.labhud.ui.rememberGate
import kotlinx.coroutines.launch

/**
 * What LabHUD sees and has no card for (Proxmox guests, containers, services a network scan found):
 * tick some and add them as cards, or stop offering them. Behind the gate, as every ⚙ change.
 */
@Composable
fun DiscoveredSection(repo: LabRepository, nav: SettingsNav) {
    val scope = rememberCoroutineScope()
    val gate = rememberGate(repo)
    var list by remember { mutableStateOf<DiscoverList?>(null) }
    var picked by remember { mutableStateOf(setOf<String>()) }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    fun load() = scope.launch {
        val api = repo.api() ?: return@launch
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

    SettingsPage(section("discovered")!!, nav) {
        if (list == null) item { Unlock { gate.press { load() } } }
        if (message.isNotEmpty()) item { Text(message, color = Lab.warn, fontSize = 13.sp) }
        list?.let { l ->
            item {
                Panel("") {
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
    GateDialogs(gate, nav.signIn)
}
