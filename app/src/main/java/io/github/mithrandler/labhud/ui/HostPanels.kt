package io.github.mithrandler.labhud.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mithrandler.labhud.data.AgentRow
import io.github.mithrandler.labhud.data.CLEANINGS
import io.github.mithrandler.labhud.data.HostInventory
import io.github.mithrandler.labhud.data.LabRepository
import io.github.mithrandler.labhud.data.StackInfo
import io.github.mithrandler.labhud.data.clean
import io.github.mithrandler.labhud.data.closeStream
import io.github.mithrandler.labhud.data.inventory
import io.github.mithrandler.labhud.data.openLiveLog
import io.github.mithrandler.labhud.data.readStream
import io.github.mithrandler.labhud.data.splitShow
import io.github.mithrandler.labhud.data.stackRun
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/* A host's Docker (T), its compose stacks (U) and a container's live log (V), for the admin: the agent's screen and the container's row. The terminal is not here: a shell wants a keyboard and a screen the wall's ⚙ has. */

/** "1.2G", "340M": the page's `bytes()`. */
fun sizeText(n: Long): String {
    if (n < 0) return "—"
    val units = listOf("B", "K", "M", "G", "T")
    var v = n.toDouble()
    var i = 0
    while (v >= 1024 && i < units.size - 1) { v /= 1024; i++ }
    return (if (v >= 100 || i == 0) "%.0f".format(v) else "%.1f".format(v)) + units[i]
}

/** Under Docker, when "images" is ticked: what the host holds (read when asked) and a button per cleaning. */
@Composable
fun DockerHostPanel(a: AgentRow, repo: LabRepository, gate: Gate, busy: Boolean, onMessage: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var inv by remember { mutableStateOf<HostInventory?>(null) }
    var reading by remember { mutableStateOf(false) }
    var ask by remember { mutableStateOf<Triple<String, String, String>?>(null) }

    fun read(fresh: Boolean = false) = gate.press {
        reading = true
        scope.launch {
            val r = runCatching { repo.api()?.inventory(a.name, fresh) }.getOrNull()
            inv = r?.takeIf { it.ok }
            if (r?.ok != true) onMessage("Not read: " + (r?.error?.ifBlank { null } ?: "refused"))
            reading = false
        }
    }
    Column(Modifier.padding(top = 12.dp)) {
        Text("Docker host", fontWeight = FontWeight.SemiBold)
        Text("Images, volumes and networks with their sizes, read when you ask.", fontSize = 12.sp, color = Lab.muted)
        OutlinedButton(onClick = { read() }, enabled = !busy && !reading && a.online, modifier = Modifier.padding(top = 6.dp)) {
            Text(if (reading) "Reading…" else if (inv == null) "Show images, volumes, networks" else "Read again")
        }
        inv?.let { i ->
            Text("${i.images.size} images (${sizeText(i.totals.images)}, ${sizeText(i.totals.unusedImages)} unused) · " +
                "${i.volumes.size} volumes (${sizeText(i.totals.volumes)}) · ${i.networks.size} networks",
                fontSize = 12.sp, color = Lab.sub, modifier = Modifier.padding(top = 8.dp))
            Text("Images", fontSize = 11.sp, color = Lab.muted, modifier = Modifier.padding(top = 8.dp))
            i.images.forEach { im ->
                HostLine(im.tags.joinToString(", ").ifEmpty { "(untagged) ${im.id}" }, sizeText(im.size) + " · " + if (im.used > 0) "${im.used} in use" else "unused", im.used == 0)
            }
            Text("Volumes", fontSize = 11.sp, color = Lab.muted, modifier = Modifier.padding(top = 8.dp))
            i.volumes.forEach { v ->
                HostLine(if (v.anonymous) "(anonymous) ${v.name.take(12)}" else v.name, sizeText(v.size) + " · " + if (v.used > 0) "${v.used} in use" else "unused", v.used == 0)
            }
            Text("Networks", fontSize = 11.sp, color = Lab.muted, modifier = Modifier.padding(top = 8.dp))
            i.networks.forEach { n -> HostLine(n.name + if (n.builtin) " (built in)" else "", "${n.driver} · ${n.used} on it", false) }
            if (i.more.isNotEmpty()) Text("More than shown: " + i.more.entries.joinToString(", ") { "${it.value} ${it.key}" } + ".", fontSize = 12.sp, color = Lab.muted)
            CLEANINGS.forEach { c ->
                OutlinedButton(onClick = { ask = c }, enabled = !busy && !reading, modifier = Modifier.padding(top = 6.dp)) { Text("Clean: " + c.second) }
            }
        }
    }
    ask?.let { (what, label, text) ->
        AlertDialog(
            onDismissRequest = { ask = null },
            title = { Text("$label on ${a.name}?") },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = {
                ask = null
                gate.press {
                    reading = true
                    scope.launch {
                        val r = runCatching { repo.api()?.clean(a.name, what) }.getOrNull()
                        onMessage(if (r?.ok == true) "Cleaned: ${r.output}" else "Not cleaned: " + (r?.error?.ifBlank { null } ?: "refused"))
                        reading = false
                        read(fresh = true)
                    }
                }
            }) { Text("Clean") } },
            dismissButton = { TextButton(onClick = { ask = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun HostLine(name: String, detail: String, dim: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(name, fontSize = 13.sp, color = if (dim) Lab.muted else Lab.text, maxLines = 2, modifier = Modifier.weight(1f))
        Text(detail, fontSize = 12.sp, color = Lab.muted, modifier = Modifier.padding(start = 8.dp))
    }
}

/** Under Stacks: the compose stacks the agent keeps, each with its buttons, and a dialog to write or change one. */
@Composable
fun StacksPanel(a: AgentRow, repo: LabRepository, gate: Gate, busy: Boolean, onMessage: (String) -> Unit, onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    var edit by remember { mutableStateOf<String?>(null) }       // a stack's name, "" for a new one
    var confirm by remember { mutableStateOf<Pair<String, String>?>(null) }   // verb, stack
    var working by remember { mutableStateOf(false) }

    fun run(verb: String, name: String) = gate.press {
        working = true
        onMessage("$verb $name…")
        scope.launch {
            val r = runCatching { repo.api()?.stackRun(a.name, verb, name) }.getOrNull()
            onMessage(if (r?.ok == true) "✓ $verb $name\n${r.output}".trim() else "✕ $verb $name: ${r?.error?.ifBlank { null } ?: "refused"}")
            working = false
            onChanged()
        }
    }
    Column(Modifier.padding(top = 12.dp)) {
        Text("Stacks", fontWeight = FontWeight.SemiBold)
        Text("A compose file is checked before anything changes. It can do anything the Docker socket can.", fontSize = 12.sp, color = Lab.muted)
        if (a.stacks.isEmpty()) Text(if (a.online) "No stack yet." else "Shown when the agent is online.", fontSize = 13.sp, color = Lab.muted, modifier = Modifier.padding(top = 6.dp))
        a.stacks.toSortedMap().forEach { (name, x) -> StackRow(name, x, enabled = !busy && !working && a.online,
            onEdit = { edit = name }, onVerb = { verb -> confirm = verb to name }) }
        OutlinedButton(onClick = { edit = "" }, enabled = !busy && !working && a.online, modifier = Modifier.padding(top = 6.dp)) { Text("New stack") }
    }
    confirm?.let { (verb, name) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("${verb.replaceFirstChar { it.uppercase() }} $name?") },
            text = { Text(when (verb) {
                "redeploy" -> "Pull the images, then bring the stack up again."
                "down" -> "Take the stack down: its containers go, its volumes stay."
                else -> "Restart its containers."
            }) },
            confirmButton = { TextButton(onClick = { confirm = null; run(verb, name) }) { Text(verb.replaceFirstChar { it.uppercase() }) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
    edit?.let { name ->
        StackEditor(a, name, repo, gate, onMessage, onClose = { edit = null }, onDeployed = { edit = null; onChanged() })
    }
}

@Composable
private fun StackRow(name: String, x: StackInfo, enabled: Boolean, onEdit: () -> Unit, onVerb: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text("$name  ·  ${x.running}/${x.services} running" + if (x.env) "  ·  .env" else "", fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onEdit, enabled = enabled) { Text("Edit") }
            TextButton(onClick = { onVerb("redeploy") }, enabled = enabled) { Text("Redeploy") }
            TextButton(onClick = { onVerb("restart") }, enabled = enabled) { Text("Restart") }
            TextButton(onClick = { onVerb("down") }, enabled = enabled) { Text("Down") }
        }
    }
}

/** A stack's compose file and .env. The .env's values never come back: its names are shown, and what is typed replaces it (empty keeps the host's). */
@Composable
private fun StackEditor(a: AgentRow, start: String, repo: LabRepository, gate: Gate, onMessage: (String) -> Unit, onClose: () -> Unit, onDeployed: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(start) }
    var compose by remember { mutableStateOf("") }
    var env by remember { mutableStateOf("") }
    var removeEnv by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf(if (start.isEmpty()) "" else "reading…") }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(start) {
        if (start.isEmpty()) return@LaunchedEffect
        gate.press {
            scope.launch {
                val r = runCatching { repo.api()?.stackRun(a.name, "show", start) }.getOrNull()
                if (r?.ok == true) {
                    val (text, names) = splitShow(r.output)
                    compose = text
                    note = if (names.isEmpty()) "No .env on the host." else ".env on the host has: " + names.joinToString(", ")
                } else note = "✕ " + (r?.error?.ifBlank { null } ?: "refused")
            }
        }
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text(if (start.isEmpty()) "New stack on ${a.name}" else "Stack $start") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (start.isEmpty()) OutlinedTextField(name, { name = it.lowercase() }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Name") })
                OutlinedTextField(compose, { compose = it }, Modifier.fillMaxWidth().heightIn(min = 160.dp), label = { Text("compose.yaml") },
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp))
                OutlinedTextField(env, { env = it }, Modifier.fillMaxWidth().heightIn(min = 80.dp), enabled = !removeEnv, label = { Text(".env (empty keeps the host's)") },
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp))
                Row(Modifier.padding(top = 4.dp)) {
                    androidx.compose.material3.Checkbox(removeEnv, { removeEnv = it })
                    Text("Remove the .env", fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
                }
                if (note.isNotEmpty()) Text(note, fontSize = 12.sp, color = Lab.muted)
            }
        },
        confirmButton = { Button(enabled = !busy && name.matches(Regex("[a-z0-9][a-z0-9_-]{0,63}")) && compose.isNotBlank(), onClick = {
            gate.press {
                busy = true
                onMessage("deploy $name…")
                scope.launch {
                    val e = if (removeEnv) "" else env.ifEmpty { null }
                    val r = runCatching { repo.api()?.stackRun(a.name, "deploy", name, compose, e) }.getOrNull()
                    busy = false
                    onMessage(if (r?.ok == true) "✓ deploy $name\n${r.output}".trim() else "✕ deploy $name: ${r?.error?.ifBlank { null } ?: "refused"}")
                    if (r?.ok == true) onDeployed() else note = "✕ " + (r?.error?.ifBlank { null } ?: "refused")
                }
            }
        }) { Text(if (busy) "Deploying…" else "Deploy") } },
        dismissButton = { TextButton(onClick = onClose, enabled = !busy) { Text("Close") } },
    )
}

/** A container's log as it is written: read-only, the last [LIVE_LINES] lines, ends by itself after 10 minutes. */
const val LIVE_LINES = 500

@Composable
fun LiveLogDialog(agent: String, container: String, repo: LabRepository, gate: Gate, onClose: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("opening…") }
    val scroll = rememberScrollState()
    var streamId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(agent, container) {
        gate.press {}   // the PIN, if one is set; the stream call below is refused without it
        val api = repo.api() ?: return@LaunchedEffect
        val open = runCatching { api.openLiveLog(agent, container) }.getOrNull()
        if (open?.ok != true) { status = "✕ " + (open?.error?.ifBlank { null } ?: "refused"); return@LaunchedEffect }
        streamId = open.id
        status = "live"
        var after = 0
        var fails = 0
        val dec = Charsets.UTF_8.newDecoder()
        var pending = ByteArray(0)
        try {
            while (true) {
                val r = runCatching { api.readStream(open.id, after) }.getOrElse { if (it is CancellationException) throw it; null }
                if (r == null || !r.ok) {
                    if (++fails > 3) { status = "✕ " + (r?.error?.ifBlank { null } ?: "lost"); return@LaunchedEffect }
                    delay(1000)
                    continue
                }
                fails = 0
                after = r.next
                if (r.data.isNotEmpty()) {
                    val bytes = pending + android.util.Base64.decode(r.data, android.util.Base64.DEFAULT)
                    // a character cut in two by a chunk waits for the rest
                    var cut = bytes.size
                    while (cut > 0 && cut > bytes.size - 4 && (bytes[cut - 1].toInt() and 0xC0) == 0x80) cut--
                    if (cut > 0 && cut <= bytes.size && (bytes[cut - 1].toInt() and 0x80) != 0 && cut > bytes.size - 4) cut--
                    val usable = if (cut in 1..bytes.size) cut else bytes.size
                    text = (text + String(bytes, 0, usable, Charsets.UTF_8)).lines().takeLast(LIVE_LINES).joinToString("\n")
                    pending = bytes.copyOfRange(usable, bytes.size)
                    scroll.scrollTo(scroll.maxValue)
                }
                r.end?.let { status = "ended: $it"; streamId = null; return@LaunchedEffect }
            }
        } finally {
            dec.reset()
        }
    }
    DisposableEffect(Unit) { onDispose { } }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = { streamId?.let { id -> scope.launch { repo.api()?.closeStream(id) } }; onClose() },
        title = { Text("$container · live log") },
        text = {
            Column {
                Text(status, fontSize = 12.sp, color = Lab.muted)
                Text(text.ifEmpty { "…" }, fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.heightIn(max = 380.dp).verticalScroll(scroll))
            }
        },
        confirmButton = { TextButton(onClick = { streamId?.let { id -> scope.launch { repo.api()?.closeStream(id) } }; onClose() }) { Text("Close") } },
    )
}
