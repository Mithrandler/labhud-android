package io.github.mithrandler.labhud.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mithrandler.labhud.data.LabRepository
import io.github.mithrandler.labhud.data.ScanStatus
import io.github.mithrandler.labhud.data.SourceItem
import io.github.mithrandler.labhud.data.scanStart
import io.github.mithrandler.labhud.data.scanStatus
import io.github.mithrandler.labhud.data.scanText
import io.github.mithrandler.labhud.data.sourceCatalog
import io.github.mithrandler.labhud.data.sourceForget
import io.github.mithrandler.labhud.data.sourceFormOk
import io.github.mithrandler.labhud.data.sourceSave
import io.github.mithrandler.labhud.data.sourceTry
import io.github.mithrandler.labhud.ui.GateDialogs
import io.github.mithrandler.labhud.ui.LabSheet
import io.github.mithrandler.labhud.ui.Lab
import io.github.mithrandler.labhud.ui.Row2
import io.github.mithrandler.labhud.ui.Said
import io.github.mithrandler.labhud.ui.TypedConfirm
import io.github.mithrandler.labhud.ui.rememberGate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The sources LabHUD can set up from a form (Proxmox, Pi-hole, Sonarr…): a list, and a sheet per source with its fields, Test, Save and
 * Forget (the name typed). Secrets are never shown; a secret left empty keeps the one set. Saving new keys restarts LabHUD, which says so.
 * Under it the scan of the network for known services, one at a time, with its progress. Behind the gate, as every ⚙ change.
 */
@Composable
fun IntegrationsSection(repo: LabRepository, nav: SettingsNav) {
    val scope = rememberCoroutineScope()
    val gate = rememberGate(repo)
    var sources by remember { mutableStateOf<List<SourceItem>?>(null) }
    var scan by remember { mutableStateOf<ScanStatus?>(null) }
    var cidr by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var open by remember { mutableStateOf<SourceItem?>(null) }

    fun load() = scope.launch {
        val api = repo.api() ?: return@launch
        runCatching { api.sourceCatalog() }.onSuccess { c -> if (c.ok) sources = c.sources else message = c.error.ifBlank { "Refused." } }.onFailure { message = it.message ?: "Not loaded." }
        runCatching { api.scanStatus() }.onSuccess { s -> if (s.ok) { scan = s; if (cidr.isEmpty()) cidr = s.cidr.ifEmpty { s.default } } }
    }
    LaunchedEffect(Unit) { gate.press { load() } }
    // while a scan runs its progress is read every two seconds
    LaunchedEffect(scan?.running) {
        while (scan?.running == true) {
            delay(2000)
            val s = runCatching { repo.api()?.scanStatus() }.getOrNull()
            if (s?.ok == true) scan = s else break
        }
    }

    SettingsPage(section("integrations")!!, nav) {
        if (sources == null) item { Unlock { gate.press { load() } } }
        if (message.isNotEmpty()) item { Text(message, color = Lab.warn, fontSize = 13.sp) }
        sources?.let { list ->
            heading("Network scan")
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Looks for known services on a private subnet of yours, slowly (two hosts at a time), only when asked.", fontSize = 12.sp, color = Lab.muted)
                    OutlinedTextField(cidr, { cidr = it.take(43) }, singleLine = true, label = { Text("Subnet (e.g. 192.168.1.0/24)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedButton(enabled = scan?.running != true && cidr.isNotBlank(), onClick = {
                        gate.press { scope.launch {
                            val s = runCatching { repo.api()?.scanStart(cidr) }.getOrNull()
                            if (s?.ok == true) { scan = s; message = "" } else message = "Scan: " + (s?.error?.ifBlank { null } ?: "refused")
                        } }
                    }) { Text(if (scan?.running == true) "Scanning…" else "Scan now") }
                    scan?.let { s -> scanText(s).takeIf { it.isNotEmpty() }?.let { Text(it, fontSize = 13.sp, color = Lab.sub) } }
                    if (scan?.error?.isNotBlank() == true) Text(scan!!.error, fontSize = 12.sp, color = Lab.warn)
                }
            }
            scan?.found?.forEachIndexed { i, f ->
                item(key = "found$i") { Row2(f.source, f.url, if (f.configured) "set" else "new") { } }
            }
            if (scan?.found?.any { !it.configured } == true) item { Text("A new one: set it up above, or add it as a card in Dashboard.", fontSize = 12.sp, color = Lab.muted) }
            heading("Sources")
            list.forEach { src ->
                item(key = "src-" + src.name) {
                    Row2(src.title, if (src.alsoWith.isNotEmpty()) "set up with ${src.alsoWith.joinToString(", ")}" else src.about,
                        if (src.configured) "set" else "not set") { if (src.fields.isNotEmpty()) open = src }
                }
            }
        }
    }
    open?.let { src ->
        LabSheet(onClose = { open = null }) {
            SourceSheet(src, repo, gate, onSaved = { note -> open = null; message = note; load() })
        }
    }
    GateDialogs(gate, nav.signIn)
}

@Composable
private fun SourceSheet(src: SourceItem, repo: LabRepository, gate: io.github.mithrandler.labhud.ui.Gate, onSaved: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val values = remember(src.name) { mutableStateOf(mapOf<String, String>()) }
    var said by remember(src.name) { mutableStateOf("") }
    var busy by remember(src.name) { mutableStateOf(false) }
    var forgetting by remember(src.name) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 20.dp)
        .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.9f).dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(src.title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Lab.text, modifier = Modifier.padding(end = 48.dp))
        Text(src.about, fontSize = 13.sp, color = Lab.sub)
        src.fields.forEach { f ->
            OutlinedTextField(
                values.value[f.name].orEmpty(), { values.value = values.value + (f.name to it) }, singleLine = true,
                label = { Text(f.label + if (f.optional) " (optional)" else "") },
                placeholder = { Text(if (f.secret && src.configured) "set: leave empty to keep it" else f.hint, maxLines = 1) },
                supportingText = if (f.hint.isNotEmpty() && !(f.secret && src.configured)) ({ Text(f.hint) }) else null,
                visualTransformation = if (f.secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Said(said)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = !busy, onClick = {
                gate.press { busy = true; said = "Testing…"; scope.launch {
                    val r = runCatching { repo.api()?.sourceTry(src.name, values.value) }.getOrNull()
                    said = if (r?.ok == true) "✓ It answers" + (r.keys.takeIf { it.isNotEmpty() }?.let { ": " + it.take(6).joinToString(", ") } ?: "") else "✕ " + (r?.error?.ifBlank { null } ?: "no answer")
                    busy = false
                } }
            }) { Text("Test") }
            OutlinedButton(enabled = !busy && (src.configured || sourceFormOk(src, values.value)), onClick = {
                gate.press { busy = true; said = "Saving…"; scope.launch {
                    val r = runCatching { repo.api()?.sourceSave(src.name, values.value) }.getOrNull()
                    if (r?.ok == true) onSaved(if (r.restart) "${src.title} saved: LabHUD restarts to read the keys." else "${src.title} saved.")
                    else { said = "Not saved: " + (r?.error?.ifBlank { null } ?: "refused"); busy = false }
                } }
            }) { Text("Save") }
            if (src.configured) OutlinedButton(enabled = !busy, onClick = { forgetting = true }) { Text("Forget", color = Lab.red) }
        }
    }
    if (forgetting) TypedConfirm("Forget ${src.title}?", src.name, "Forget", onOk = {
        forgetting = false
        gate.press { busy = true; scope.launch {
            val r = runCatching { repo.api()?.sourceForget(src.name) }.getOrNull()
            if (r?.ok == true) onSaved("${src.title} forgotten" + if (r.restart) ": LabHUD restarts." else ".") else { said = "Not forgotten: " + (r?.error?.ifBlank { null } ?: "refused"); busy = false }
        } }
    }, onDismiss = { forgetting = false })
}
