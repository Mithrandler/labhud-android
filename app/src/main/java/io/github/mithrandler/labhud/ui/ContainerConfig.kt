package io.github.mithrandler.labhud.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import io.github.mithrandler.labhud.data.LabApi
import io.github.mithrandler.labhud.data.ZoneAnswer
import io.github.mithrandler.labhud.data.ZoneSettings
import io.github.mithrandler.labhud.data.refusal
import io.github.mithrandler.labhud.data.zoneReconfigure
import io.github.mithrandler.labhud.data.zoneSettings
import kotlinx.coroutines.launch

private val RESTARTS = listOf("no", "always", "unless-stopped", "on-failure")

private fun lines(text: String) = text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }

/** A container's restart policy, ports, volumes and variables: the variables' values are only written, never read back. */
@Composable
internal fun ConfigView(host: String, name: String, ask: suspend (suspend LabApi.() -> ZoneAnswer) -> ZoneAnswer, onChanged: () -> Unit) {
    var loaded by remember(name) { mutableStateOf<ZoneAnswer?>(null) }
    var round by remember(name) { mutableStateOf(0) }
    LaunchedEffect(name, round) { loaded = ask { zoneSettings(host, name) } }
    val a = loaded
    val s = a?.settings
    when {
        a == null -> Text("Loading…", color = Lab.muted)
        !a.ok || s == null -> Text(a.refusal(), color = Lab.warn)
        s.self -> Text("This is the agent's own container: change it from its host.", color = Lab.muted)
        s.managed.isNotEmpty() -> Text("Made by compose (${s.managed}): change its file under Stacks, or the next deploy undoes the change here.", color = Lab.muted)
        else -> ConfigForm(host, name, s, ask, onChanged) { loaded = null; round++ }
    }
}

@Composable
private fun ConfigForm(
    host: String, name: String, s: ZoneSettings,
    ask: suspend (suspend LabApi.() -> ZoneAnswer) -> ZoneAnswer, onChanged: () -> Unit, reload: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var policy by remember(s) { mutableStateOf(s.restart.substringBefore(':')) }
    var retries by remember(s) { mutableStateOf(s.restart.substringAfter(':', "")) }
    var ports by remember(s) { mutableStateOf(s.ports.sorted().joinToString("\n")) }
    var volumes by remember(s) { mutableStateOf(s.volumes.joinToString("\n")) }
    val values = remember(s) { mutableStateMapOf<String, String>() }
    val removed = remember(s) { mutableStateListOf<String>() }
    var addName by remember(s) { mutableStateOf("") }
    var addValue by remember(s) { mutableStateOf("") }
    var message by remember(s) { mutableStateOf("") }
    var bad by remember(s) { mutableStateOf(false) }
    var busy by remember(s) { mutableStateOf(false) }
    var confirm by remember(s) { mutableStateOf<Change?>(null) }

    Column(Modifier.heightIn(max = LabScale.scrollM).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(LabScale.space2)) {
        Text("Restart policy", fontSize = LabScale.textXs, color = Lab.muted)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(LabScale.space2)) {
            for (r in RESTARTS) FilterChip(policy == r, { policy = r }, label = { Text(r) })
        }
        if (policy == "on-failure") {
            OutlinedTextField(retries, { retries = it.filter(Char::isDigit).take(3) }, label = { Text("Max retries") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        }
        OutlinedTextField(ports, { ports = it }, label = { Text("Ports (host:container[/udp], one per line)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(volumes, { volumes = it }, label = { Text("Volumes (source:target[:ro], one per line)") }, modifier = Modifier.fillMaxWidth())
        if (s.otherMounts > 0) Text("${s.otherMounts} more mount(s) stay as they are.", fontSize = LabScale.textSm, color = Lab.muted)
        Text("Variables (names only; a value you type replaces it)", fontSize = LabScale.textXs, color = Lab.muted)
        for (n in s.env) {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(LabScale.space2)) {
                    Text(n, fontSize = LabScale.textSm, color = Lab.text, modifier = Modifier.weight(1f))
                    Checkbox(n in removed, { if (it) removed.add(n) else removed.remove(n) })
                    Text("remove", fontSize = LabScale.textSm, color = Lab.sub)
                }
                OutlinedTextField(values[n].orEmpty(), { values[n] = it }, label = { Text("new value") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), enabled = n !in removed, modifier = Modifier.fillMaxWidth())
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(LabScale.space2)) {
            OutlinedTextField(addName, { addName = it }, label = { Text("NEW_VARIABLE") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(addValue, { addValue = it }, label = { Text("value") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.weight(1f))
        }
        OutlinedButton(enabled = !busy, onClick = {
            val restartNew = if (policy == "on-failure" && retries.isNotEmpty()) "on-failure:$retries" else policy
            val change = Change(
                restart = restartNew.takeIf { it != s.restart },
                ports = lines(ports).takeIf { it != s.ports.sorted() },
                volumes = lines(volumes).takeIf { it != s.volumes },
                envSet = buildMap {
                    s.env.filter { it !in removed && values[it].orEmpty().isNotEmpty() }.forEach { put(it, values.getValue(it)) }
                    if (addName.isNotBlank()) put(addName.trim(), addValue)
                },
                envUnset = removed.toList(),
            )
            if (change.empty()) { message = "nothing changed"; bad = false } else confirm = change
        }) { Text("Apply") }
        if (message.isNotEmpty()) Text(message, fontSize = LabScale.textSm, color = if (bad) Lab.warn else Lab.muted, modifier = Modifier.padding(bottom = LabScale.space2))
    }
    confirm?.let { c ->
        var typed by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Apply to $name?") },
            text = { Column { Text("Type its name to confirm.", fontSize = LabScale.textSm, color = Lab.sub)
                OutlinedTextField(typed, { typed = it }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = LabScale.space2)) } },
            confirmButton = { TextButton(enabled = typed == name, onClick = {
                confirm = null
                busy = true; bad = false
                message = "working… the container is made again; the old one comes back if the new one does not start"
                scope.launch {
                    val r = ask { zoneReconfigure(host, name, name, c.envSet, c.envUnset, c.ports, c.volumes, c.restart) }
                    busy = false
                    if (r.ok) { onChanged(); reload() } else { bad = true; message = r.refusal() }
                }
            }) { Text("Apply") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}

private data class Change(
    val restart: String?, val ports: List<String>?, val volumes: List<String>?, val envSet: Map<String, String>, val envUnset: List<String>,
) {
    fun empty() = restart == null && ports == null && volumes == null && envSet.isEmpty() && envUnset.isEmpty()
}
