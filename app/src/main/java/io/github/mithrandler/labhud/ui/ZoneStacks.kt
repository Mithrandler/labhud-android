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
import androidx.compose.material3.Button
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mithrandler.labhud.data.ZoneHost
import io.github.mithrandler.labhud.data.ZoneStack
import io.github.mithrandler.labhud.data.line
import io.github.mithrandler.labhud.data.refusal
import io.github.mithrandler.labhud.data.stackDirty
import io.github.mithrandler.labhud.data.stackNameOk
import io.github.mithrandler.labhud.data.versionText
import io.github.mithrandler.labhud.data.zoneStackAct
import io.github.mithrandler.labhud.data.zoneStackDown
import io.github.mithrandler.labhud.data.zoneStackForget
import io.github.mithrandler.labhud.data.zoneStackHook
import io.github.mithrandler.labhud.data.zoneStackRead
import io.github.mithrandler.labhud.data.zoneStackRestore
import io.github.mithrandler.labhud.data.zoneStackSave
import io.github.mithrandler.labhud.data.zoneStackSource
import io.github.mithrandler.labhud.data.zoneStackSync
import io.github.mithrandler.labhud.data.zoneStackValidate
import io.github.mithrandler.labhud.data.zoneStacks
import kotlinx.coroutines.launch

/* Stacks in Servers: the compose projects a host keeps. A sheet per stack with Compose / .env / History / Git. The files are edited
   with Save and Discard (never autosaved); Save has the host check them (`compose config`) and write them, it does not deploy:
   Redeploy is its own button. The .env shows its values as ******** (they stay on the host). Down asks for the name typed. */

private val FILE_TABS = listOf("compose" to "Compose", "env" to ".env", "history" to "History", "git" to "Git")
private const val NEW_COMPOSE = "services:\n  app:\n    image: nginx:stable\n    restart: unless-stopped\n"
private val MONO = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Lab.text)

@Composable
internal fun StacksTab(h: ZoneHost, ask: ZoneAsk, gate: Gate) {
    var open by remember(h.name) { mutableStateOf<ZoneStack?>(null) }
    var fresh by remember(h.name) { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }
    var tick by remember(h.name) { mutableIntStateOf(0) }
    var enabled by remember(h.name) { mutableStateOf(true) }
    var rows by remember(h.name) { mutableStateOf<List<ZoneStack>?>(null) }
    var message by remember(h.name) { mutableStateOf("") }
    LaunchedEffect(h.name, tick) {
        val r = ask { zoneStacks(h.name) }
        if (r.ok) { rows = r.stacks; enabled = r.enabled; message = "" } else message = r.refusal()
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { tick++ }) { Text("Refresh") }
            OutlinedButton(enabled = enabled, onClick = { naming = true }) { Text("New stack") }
        }
        Said(message)
        if (!enabled) Text("Stacks is not ticked for this host: tick it in Settings > Hosts and agents.", color = Lab.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        else if (rows == null) Text("Loading…", color = Lab.muted, modifier = Modifier.padding(top = 12.dp))
        else if (rows.orEmpty().isEmpty()) Text("No stack on this host yet.", color = Lab.muted, modifier = Modifier.padding(top = 12.dp))
        Column(Modifier.verticalScroll(rememberScrollState())) {
            rows.orEmpty().forEach { s -> Row2(s.name, s.line(), "") { fresh = false; open = s } }
        }
    }
    if (naming) NewStackDialog(onDismiss = { naming = false }) { name -> naming = false; fresh = true; open = ZoneStack(name = name) }
    open?.let { s ->
        LabSheet(onClose = { open = null }) {
            StackSheet(h.name, s, fresh, ask, gate, onChanged = { tick++ }, onSaved = { fresh = false })
        }
    }
}

@Composable
private fun NewStackDialog(onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("A new stack") },
        text = { Column { Text("Lowercase letters, digits, - and _.", fontSize = 13.sp, color = Lab.sub)
            OutlinedTextField(name, { name = it.take(64) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) } },
        confirmButton = { TextButton(enabled = stackNameOk(name), onClick = { onOk(name) }) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun StackSheet(host: String, stack: ZoneStack, isNew: Boolean, ask: ZoneAsk, gate: Gate, onChanged: () -> Unit, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    var tab by remember(stack.name) { mutableStateOf("compose") }
    var kept by remember(stack.name) { mutableStateOf(!isNew) }          // the files exist on the host
    var loaded by remember(stack.name) { mutableStateOf(isNew) }
    var compose by remember(stack.name) { mutableStateOf(if (isNew) NEW_COMPOSE else "") }
    var compose0 by remember(stack.name) { mutableStateOf(if (isNew) "" else "") }
    var env by remember(stack.name) { mutableStateOf("") }
    var env0 by remember(stack.name) { mutableStateOf("") }
    var mask by remember(stack.name) { mutableStateOf("********") }
    var versions by remember(stack.name) { mutableStateOf<List<Long>>(emptyList()) }
    var said by remember { mutableStateOf("") }
    var bad by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var down by remember { mutableStateOf(false) }
    var shown by remember { mutableStateOf("") }
    val dirty = loaded && stackDirty(compose, compose0, env, env0)

    fun say(t: String, isBad: Boolean = false) { said = t; bad = isBad }
    suspend fun reload() {
        val r = ask { zoneStackRead(host, stack.name) }
        if (!r.ok) { say(r.refusal(), true); return }
        compose = r.compose; compose0 = r.compose; env = r.env; env0 = r.env; mask = r.mask; versions = r.versions; loaded = true
    }
    LaunchedEffect(stack.name) { if (!isNew) reload() }

    fun run(label: String, call: suspend io.github.mithrandler.labhud.data.LabApi.() -> io.github.mithrandler.labhud.data.ZoneAnswer, after: suspend () -> Unit = {}) = gate.press {
        busy = true; say("$label…")
        scope.launch {
            val r = ask(call)
            if (r.ok) { say(if (label == "Validate") "The compose file is valid." else "$label done"); after() }
            else say("$label: ${r.refusal()}" + if (r.output.isNotBlank()) "\n${r.output.take(600)}" else "", true)
            busy = false
            onChanged()
        }
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 20.dp).heightIn(max = 640.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stack.name, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Lab.text, modifier = Modifier.padding(end = 48.dp))
        Text(if (!kept) "new stack: nothing is written until Save" else stack.line(), fontSize = 13.sp, color = Lab.muted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = kept && !busy && !dirty, onClick = { run("Redeploy", { zoneStackAct(host, stack.name, "redeploy") }) }) { Text("Redeploy") }
            OutlinedButton(enabled = kept && !busy, onClick = { run("Restart", { zoneStackAct(host, stack.name, "restart") }) }) { Text("Restart") }
            OutlinedButton(enabled = kept && !busy, onClick = { down = true }) { Text("Down", color = Lab.red) }
        }
        if (dirty && kept) Text("Unsaved changes: Save them before Redeploy.", fontSize = 12.sp, color = Lab.warn)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FILE_TABS.forEach { (id, label) ->
                FilterChip(selected = tab == id, enabled = kept || id == "compose" || id == "env", onClick = { tab = id; shown = "" },
                    label = { Text(label + if ((id == "compose" && compose != compose0 && loaded) || (id == "env" && env != env0 && loaded)) " •" else "") })
            }
        }
        if (!loaded) Text("Loading…", color = Lab.muted)
        else when (tab) {
            "compose", "env" -> {
                val isEnv = tab == "env"
                OutlinedTextField(
                    if (isEnv) env else compose, { if (isEnv) env = it.take(48 * 1024) else compose = it.take(48 * 1024) },
                    textStyle = MONO, modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 360.dp),
                )
                if (isEnv) Text("Values stay on the host and show as $mask. Leave a line as it is to keep its value; type a new value to change it.", fontSize = 12.sp, color = Lab.muted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = !busy, onClick = { run("Validate", { zoneStackValidate(host, stack.name, compose, env) }) }) { Text("Validate") }
                    Button(enabled = dirty && !busy, onClick = {
                        run("Save", { zoneStackSave(host, stack.name, compose, env) }) {
                            kept = true; onSaved(); reload(); say("Saved. Redeploy to run it.")
                        }
                    }) { Text("Save") }
                    OutlinedButton(enabled = dirty && !busy, onClick = { compose = compose0; env = env0; say("") }) { Text("Discard") }
                }
            }
            "history" -> {
                Text("Each save and deploy keeps the files it replaces (the last 10). Restore writes an older version back; it does not deploy.", fontSize = 12.sp, color = Lab.muted)
                if (dirty) Text("Save or Discard your changes before restoring.", fontSize = 12.sp, color = Lab.warn)
                if (versions.isEmpty()) Text("No older version yet.", color = Lab.muted)
                versions.forEach { v ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(versionText(v), color = Lab.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = { scope.launch { val r = ask { zoneStackRead(host, stack.name, v) }; shown = if (r.ok) r.compose + if (r.hasEnv) "\n# .env\n" + r.env else "" else r.refusal() } }) { Text("View") }
                        TextButton(enabled = !dirty && !busy, onClick = { run("Restore", { zoneStackRestore(host, stack.name, v) }) { reload() } }) { Text("Restore") }
                    }
                }
                if (shown.isNotEmpty()) SelectionContainer { Text(shown, style = MONO, modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp).verticalScroll(rememberScrollState())) }
            }
            else -> GitPane(host, stack, ask, gate, busy, ::say, onChanged)
        }
        if (said.isNotEmpty()) Text(said, color = if (bad) Lab.red else Lab.sub, fontSize = 13.sp)
    }
    if (down) TypedConfirm("Take ${stack.name} down? (volumes stay)", stack.name, "Down", onOk = { run("Down", { zoneStackDown(host, stack.name, stack.name) }) }, onDismiss = { down = false })
}

@Composable
private fun GitPane(host: String, stack: ZoneStack, ask: ZoneAsk, gate: Gate, busy: Boolean, say: (String, Boolean) -> Unit, onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    var repo by remember(stack.name) { mutableStateOf(stack.source?.repo.orEmpty()) }
    var branch by remember(stack.name) { mutableStateOf(stack.source?.branch ?: "main") }
    var path by remember(stack.name) { mutableStateOf(stack.source?.path.orEmpty()) }
    var hasSource by remember(stack.name) { mutableStateOf(stack.source != null) }
    var token by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    Text("The compose file can come from a repository. Sync pulls it, saves it (the .env stays) and redeploys. A token for a private repository is used once and never kept, so a webhook only works for a repository that needs none.", fontSize = 12.sp, color = Lab.muted)
    OutlinedTextField(repo, { repo = it.take(200) }, singleLine = true, label = { Text("Repository") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(branch, { branch = it.take(100) }, singleLine = true, label = { Text("Branch") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(path, { path = it.take(200) }, singleLine = true, label = { Text("Path of the file") }, modifier = Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(enabled = !busy && repo.isNotBlank() && path.isNotBlank(), onClick = {
            gate.press { scope.launch {
                val r = ask { zoneStackSource(host, stack.name, repo.trim(), branch.trim(), path.trim()) }
                if (r.ok) hasSource = true
                say(if (r.ok) "Source saved." else r.refusal(), !r.ok); onChanged()
            } }
        }) { Text("Save source") }
        OutlinedButton(enabled = hasSource && !busy, onClick = {
            gate.press { scope.launch {
                val r = ask { zoneStackForget(host, stack.name) }
                if (r.ok) { hasSource = false; url = "" }
                say(if (r.ok) "Source and webhook removed." else r.refusal(), !r.ok); onChanged()
            } }
        }) { Text("Forget", color = Lab.red) }
    }
    OutlinedTextField(token, { token = it.take(300) }, singleLine = true, label = { Text("Token (private repository only)") }, modifier = Modifier.fillMaxWidth(),
        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(enabled = hasSource && !busy, onClick = {
            gate.press { scope.launch {
                say("Syncing…", false)
                val r = ask { zoneStackSync(host, stack.name, token) }
                token = ""
                say(if (r.ok) "Synced and redeployed." else "Sync: ${r.refusal()}" + if (r.output.isNotBlank()) "\n${r.output.take(600)}" else "", !r.ok); onChanged()
            } }
        }) { Text("Sync now") }
        OutlinedButton(enabled = hasSource && !busy, onClick = {
            gate.press { scope.launch {
                val r = ask { zoneStackHook(host, stack.name) }
                if (r.ok) { url = r.path; say("Webhook made. It is shown once.", false) } else say(r.refusal(), true)
                onChanged()
            } }
        }) { Text(if (stack.hook) "New webhook" else "Make webhook") }
    }
    if (url.isNotEmpty()) {
        Text("Call it with POST from your git host (the server's address in front); it only syncs this stack, at most once in 30 s:", fontSize = 12.sp, color = Lab.muted)
        SelectionContainer { Text(url, style = MONO) }
    }
}
