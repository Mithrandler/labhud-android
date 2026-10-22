package io.github.mithrandler.labhud.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mithrandler.labhud.data.LabApi
import io.github.mithrandler.labhud.data.LabRepository
import io.github.mithrandler.labhud.data.Utf8Stream
import io.github.mithrandler.labhud.data.ZoneAnswer
import io.github.mithrandler.labhud.data.ZoneContainer
import io.github.mithrandler.labhud.data.ZoneHost
import io.github.mithrandler.labhud.data.bytesText
import io.github.mithrandler.labhud.data.filtered
import io.github.mithrandler.labhud.data.line
import io.github.mithrandler.labhud.data.plainTerminal
import io.github.mithrandler.labhud.data.refusal
import io.github.mithrandler.labhud.data.status
import io.github.mithrandler.labhud.data.tailLines
import io.github.mithrandler.labhud.data.tone
import io.github.mithrandler.labhud.data.zoneAction
import io.github.mithrandler.labhud.data.zoneClose
import io.github.mithrandler.labhud.data.zoneContainers
import io.github.mithrandler.labhud.data.zoneHosts
import io.github.mithrandler.labhud.data.zoneInput
import io.github.mithrandler.labhud.data.zoneInspect
import io.github.mithrandler.labhud.data.zoneOpen
import io.github.mithrandler.labhud.data.zoneRead
import io.github.mithrandler.labhud.data.zoneSession
import io.github.mithrandler.labhud.data.zoneStats
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Servers: the hosts' Docker, over labhud's own gate (api/servers.py). A compact list of hosts; a host has
 * its tabs (at the top, or a column at the side on a wide screen); a container opens a sheet from below
 * with its actions, its live log, Inspect, Stats and a shell.
 *
 * It needs the admin and, with a PIN, the PIN typed: that starts a session which ends after 15 minutes
 * without a call. Nothing is kept here: every number comes from labhud again.
 */
internal typealias ZoneAsk = suspend (suspend LabApi.() -> ZoneAnswer) -> ZoneAnswer

private const val LOG_LINES = 800
private val SHELL_KEYS = listOf("Ctrl-C" to "\u0003", "Tab" to "\t", "Esc" to "\u001B", "↑" to "\u001B[A", "↓" to "\u001B[B")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZoneScreen(repo: LabRepository, bar: BarNav, onSignIn: () -> Unit) {
    val gate = rememberGate(repo)
    val scope = rememberCoroutineScope()
    val rights by repo.rights.collectAsStateWithLifecycle()
    var session by remember { mutableStateOf(false) }
    var hosts by remember { mutableStateOf<List<ZoneHost>?>(null) }
    var message by remember { mutableStateOf("") }
    var host by rememberSaveable { mutableStateOf("") }
    var tick by remember { mutableStateOf(0) }

    /** A call to labhud: a session that ended or a refusal the PIN may mend goes back to the start button. */
    suspend fun ask(call: suspend LabApi.() -> ZoneAnswer): ZoneAnswer {
        val api = repo.api() ?: return ZoneAnswer(error = "No server paired.")
        val r = runCatching { api.call() }.getOrElse { if (it is CancellationException) throw it; ZoneAnswer(error = it.message ?: "Could not reach LabHUD.") }
        if (r.resume || (!r.ok && r.error == "forbidden")) session = false
        return r
    }

    fun start() = gate.press {
        scope.launch {
            val r = repo.api()?.let { api -> runCatching { api.zoneSession() }.getOrNull() }
            if (r?.ok == true) { session = true; message = "" } else message = r?.refusal() ?: "Could not reach LabHUD."
        }
    }
    LaunchedEffect(Unit) { if (rights.admin) start() }
    LaunchedEffect(session, tick) {
        if (!session) return@LaunchedEffect
        while (true) {
            val r = ask { zoneHosts() }
            if (r.ok) { hosts = r.hosts; message = "" } else { message = r.refusal(); if (!session) return@LaunchedEffect }
            delay(20_000)
        }
    }
    BackHandler(host.isNotEmpty()) { host = "" }

    val barScroll = rememberLabBarScroll()
    Scaffold(
        modifier = Modifier.labBarScroll(barScroll), containerColor = Lab.bg,
        topBar = { MainTopBar(repo, bar, title = { Text(if (host.isEmpty()) "Servers" else host) }) },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            when {
                !rights.admin -> Notice("Servers is for the admin.", "Sign in", onSignIn)
                !session -> Notice(message.ifEmpty { "Servers needs the PIN again." }, "Unlock") { start() }
                host.isEmpty() -> HostList(hosts, message, onOpen = { host = it }, onRefresh = { tick++ })
                else -> hosts?.firstOrNull { it.name == host }?.let { h -> HostScreen(h, ::ask, repo, gate) } ?: run { host = "" }
            }
        }
    }
    GateDialogs(gate, onSignIn)
}

@Composable
private fun Notice(text: String, button: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(text, color = Lab.sub, fontSize = 14.sp)
        Button(onClick = onClick) { Text(button) }
    }
}

@Composable
internal fun Dot(tone: Int, size: Int = 10) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(when (tone) { 0 -> Lab.green; 1 -> Lab.warn; else -> Lab.red }))
}

@Composable
private fun HostList(hosts: List<ZoneHost>?, message: String, onOpen: (String) -> Unit, onRefresh: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
        if (message.isNotEmpty()) item { Text(message, color = Lab.warn, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp)) }
        if (hosts == null) item { Text("Loading…", color = Lab.muted) }
        else if (hosts.isEmpty()) item { Text("No agents yet. Add one in Settings > Hosts and agents.", color = Lab.muted) }
        items(hosts.orEmpty(), key = { it.name }) { h ->
            Row(Modifier.fillMaxWidth().clickable { onOpen(h.name) }.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Dot(if (h.online) 0 else 2)
                Spacer(Modifier.width(12.dp))
                Text(h.name, fontSize = 16.sp, color = Lab.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(h.line(), fontSize = 13.sp, color = Lab.muted)
                if (h.online) Text("  ${h.cpu.toInt()}%  ${h.mem.toInt()}%", fontSize = 13.sp, color = Lab.muted)
                Icon(LabIcons.chevronRight, null, tint = Lab.muted, modifier = Modifier.size(20.dp))
            }
            androidx.compose.material3.HorizontalDivider(color = Lab.line)
        }
    }
}

private val TABS = listOf("overview" to "Overview", "containers" to "Containers", "stacks" to "Stacks", "images" to "Images", "volumes" to "Volumes", "networks" to "Networks", "vms" to "VMs & LXC", "host" to "Host")

@Composable
private fun HostScreen(h: ZoneHost, ask: suspend (suspend LabApi.() -> ZoneAnswer) -> ZoneAnswer, repo: LabRepository, gate: Gate) {
    var tab by rememberSaveable { mutableStateOf("containers") }
    val wide = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    @Composable fun content() = when (tab) {
        "overview" -> Overview(h)
        "stacks" -> StacksTab(h, ask, gate)
        "images" -> ImagesTab(h, ask, gate)
        "volumes" -> VolumesTab(h, ask, gate)
        "networks" -> NetworksTab(h, ask, gate)
        "vms" -> GuestsTab(h, ask, gate)
        "host" -> HostTab(h, ask, gate)
        else -> ContainersTab(h, ask, repo, gate)
    }
    val tabs = TABS.filter { it.first != "vms" || h.pve }
    if (wide) Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(140.dp).verticalScroll(rememberScrollState()).padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            tabs.forEach { (id, label) -> FilterChip(selected = tab == id, onClick = { tab = id }, label = { Text(label) }, modifier = Modifier.fillMaxWidth()) }
        }
        Box(Modifier.weight(1f)) { content() }
    } else Column(Modifier.fillMaxSize()) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tabs.forEach { (id, label) -> FilterChip(selected = tab == id, onClick = { tab = id }, label = { Text(label) }) }
        }
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
internal fun Cell(label: String, big: String, sub: String = "", modifier: Modifier = Modifier) {
    Surface(shape = CardShape, color = Lab.card, border = BorderStroke(1.dp, Lab.line), modifier = modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(label, fontSize = 12.sp, color = Lab.muted)
            Text(big, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Lab.text)
            if (sub.isNotEmpty()) Text(sub, fontSize = 12.sp, color = Lab.muted)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Overview(h: ZoneHost) {
    FlowRow(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Cell("Status", if (h.online) "Online" else "Offline", if (h.version.isNotEmpty()) "agent ${h.version}" else "", Modifier.width(150.dp))
        Cell("Containers", "${h.containers.running} / ${h.containers.total}", "running / all", Modifier.width(150.dp))
        Cell("CPU", "${h.cpu.toInt()}%", "", Modifier.width(150.dp))
        Cell("Memory", "${h.mem.toInt()}%", "", Modifier.width(150.dp))
        if (h.update) Cell("Agent", "Update", "a newer agent is out", Modifier.width(150.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContainersTab(h: ZoneHost, ask: suspend (suspend LabApi.() -> ZoneAnswer) -> ZoneAnswer, repo: LabRepository, gate: Gate) {
    var rows by remember(h.name) { mutableStateOf<List<ZoneContainer>?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var open by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(h.name, tick) {
        while (true) {
            val r = ask { zoneContainers(h.name) }
            if (r.ok) { rows = r.containers; message = "" } else message = r.refusal()
            delay(15_000)
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        OutlinedTextField(query, { query = it.take(60) }, singleLine = true, label = { Text("Filter") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        if (message.isNotEmpty()) Text(message, color = Lab.warn, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        // the list under an open sheet stays as it was: no re-sorting, no jump
        val frozen = remember { arrayOf<List<ZoneContainer>?>(null) }
        if (open == null) frozen[0] = rows
        val shown = (frozen[0] ?: rows)?.filtered(query).orEmpty()
        if (rows == null) Text("Loading…", color = Lab.muted, modifier = Modifier.padding(top = 12.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            items(shown, key = { it.name }) { c ->
                Row(Modifier.fillMaxWidth().clickable { open = c.name }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Dot(c.tone())
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.name, fontSize = 15.sp, color = Lab.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(c.image, fontSize = 12.sp, color = Lab.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(c.status(), fontSize = 12.sp, color = when (c.tone()) { 0 -> Lab.green; 1 -> Lab.warn; else -> Lab.red })
                        if (c.state == "running") Text("${c.cpu.toInt()}% · ${c.memPct.toInt()}%", fontSize = 12.sp, color = Lab.muted)
                    }
                }
                androidx.compose.material3.HorizontalDivider(color = Lab.line)
            }
        }
    }
    val current = rows?.firstOrNull { it.name == open }
    if (current != null) {
        LabSheet(onClose = { open = null }) {
            ContainerSheet(h.name, current, ask, repo, gate, onChanged = { tick++ })
        }
    }
}

@Composable
private fun ContainerSheet(host: String, c: ZoneContainer, ask: suspend (suspend LabApi.() -> ZoneAnswer) -> ZoneAnswer, repo: LabRepository, gate: Gate, onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    var view by rememberSaveable(c.name) { mutableStateOf("logs") }
    var confirm by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var said by remember { mutableStateOf("") }

    fun send(verb: String) = gate.press {
        busy = true
        scope.launch {
            val r = ask { zoneAction(host, c.name, verb) }
            said = if (r.ok) "" else "$verb: ${r.refusal()}"
            busy = false
            onChanged()
        }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
        Text(c.name, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Lab.text)
        Text("${c.image} · ${c.status()}", fontSize = 13.sp, color = Lab.muted)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (verb in listOf("start", "stop", "restart", "update")) {
                val on = verb in c.can && !busy && !(verb == "start" && c.state == "running") && !(verb == "stop" && c.state != "running")
                OutlinedButton(onClick = { if (verb == "stop" || verb == "update") confirm = verb else send(verb) }, enabled = on) {
                    Text(verb.replaceFirstChar { it.uppercase() })
                }
            }
        }
        if (said.isNotEmpty()) Text(said, color = Lab.warn, fontSize = 13.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(view == "logs", { view = "logs" }, label = { Text("Logs live") })
            FilterChip(view == "inspect", { view = "inspect" }, label = { Text("Inspect") })
            FilterChip(view == "stats", { view = "stats" }, label = { Text("Stats") })
            FilterChip(view == "shell", { view = "shell" }, enabled = c.shell, label = { Text("Shell") })
        }
        Spacer(Modifier.size(8.dp))
        when (view) {
            "inspect" -> InspectView(host, c.name, ask)
            "stats" -> StatsView(host, c.name, ask)
            "shell" -> ShellView(host, c.name, repo, ask)
            else -> LogView(host, c.name, repo, ask)
        }
    }
    confirm?.let { verb ->
        var typed by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("${verb.replaceFirstChar { it.uppercase() }} ${c.name}?") },
            text = { Column { Text("Type its name to confirm.", fontSize = 13.sp, color = Lab.sub)
                OutlinedTextField(typed, { typed = it }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) } },
            confirmButton = { TextButton(enabled = typed == c.name, onClick = { confirm = null; send(verb) }) { Text(verb.replaceFirstChar { it.uppercase() }) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}

/** The container's log as it is written: the last [LOG_LINES] lines, ends by itself after 10 minutes. */
@Composable
private fun LogView(host: String, name: String, repo: LabRepository, ask: suspend (suspend LabApi.() -> ZoneAnswer) -> ZoneAnswer) {
    var text by remember(name) { mutableStateOf("") }
    var status by remember(name) { mutableStateOf("opening…") }
    val scroll = rememberScrollState()
    var id by remember(name) { mutableStateOf<String?>(null) }
    LaunchedEffect(name) {
        val open = ask { zoneOpen(host, "logs", name) }
        if (!open.ok) { status = "✕ " + open.refusal(); return@LaunchedEffect }
        id = open.id
        status = "live"
        followStream(repo, open.id, onText = { text = tailLines(text + it, LOG_LINES); scroll.scrollTo(scroll.maxValue) }) { status = "ended: $it"; id = null }
    }
    DisposableEffect(name) { onDispose { id?.let { i -> repo.zoneCloseLater(i) } } }
    Text(status, fontSize = 12.sp, color = Lab.muted)
    Mono(text.ifEmpty { "…" }, Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 420.dp).verticalScroll(scroll))
}

/** Closes a stream when its view goes (a dispose cannot wait): its own scope, one call. */
@OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
private fun LabRepository.zoneCloseLater(id: String) {
    kotlinx.coroutines.GlobalScope.launch { api()?.zoneClose(id) }
}

private suspend fun followStream(repo: LabRepository, id: String, onText: suspend (String) -> Unit, onEnd: (String) -> Unit) {
    var after = 0
    var fails = 0
    val dec = Utf8Stream()
    while (true) {
        val api = repo.api() ?: return onEnd("no server")
        val r = runCatching { api.zoneRead(id, after) }.getOrElse { if (it is CancellationException) throw it; null }
        if (r == null || !r.ok) {
            if (++fails > 3) return onEnd(r?.refusal() ?: "lost")
            delay(1000)
            continue
        }
        fails = 0
        after = r.next
        if (r.data.isNotEmpty()) onText(dec.feed(android.util.Base64.decode(r.data, android.util.Base64.DEFAULT)))
        r.end?.let { return onEnd(it) }
    }
}

@Composable
internal fun Mono(text: String, modifier: Modifier = Modifier) {
    Surface(color = Color.Black, shape = CardShape, border = BorderStroke(1.dp, Lab.line), modifier = modifier) {
        Text(text, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Lab.text, modifier = Modifier.padding(8.dp))
    }
}

@Composable
private fun InspectView(host: String, name: String, ask: suspend (suspend LabApi.() -> ZoneAnswer) -> ZoneAnswer) {
    var r by remember(name) { mutableStateOf<ZoneAnswer?>(null) }
    LaunchedEffect(name) { r = ask { zoneInspect(host, name) } }
    val a = r
    when {
        a == null -> Text("Loading…", color = Lab.muted)
        !a.ok || a.inspect == null -> Text(a.refusal(), color = Lab.warn)
        else -> {
            val i = a.inspect
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                @Composable fun row(k: String, v: String) { if (v.isNotEmpty()) Column { Text(k, fontSize = 11.sp, color = Lab.muted); Text(v, fontSize = 13.sp, color = Lab.text) } }
                row("Image", i.image); row("Created", i.created); row("Started", i.started)
                row("Restart", i.restart + if (i.restarts > 0) " (${i.restarts} restarts)" else ""); row("Health", i.health); row("Command", i.command)
                row("Ports", i.ports.joinToString("\n")); row("Mounts", i.mounts.joinToString("\n")); row("Networks", i.networks.joinToString("\n"))
                row("Environment (names only)", i.env.joinToString("\n")); row("Labels", i.labels.entries.joinToString("\n") { "${it.key}=${it.value}" })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatsView(host: String, name: String, ask: suspend (suspend LabApi.() -> ZoneAnswer) -> ZoneAnswer) {
    var r by remember(name) { mutableStateOf<ZoneAnswer?>(null) }
    LaunchedEffect(name) { while (true) { r = ask { zoneStats(host, name) }; delay(5000) } }
    val a = r
    when {
        a == null -> Text("Loading…", color = Lab.muted)
        !a.ok || a.stats == null -> Text(a.refusal(), color = Lab.warn)
        else -> {
            val s = a.stats
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Cell("CPU", "${s.cpu.toInt()}%", "of one core", Modifier.width(150.dp))
                Cell("Memory", bytesText(s.mem), if (s.memLimit > 0) "${s.memPct.toInt()}% of ${bytesText(s.memLimit)}" else "", Modifier.width(150.dp))
                Cell("Network", "↓ ${bytesText(s.netRx)}", "↑ ${bytesText(s.netTx)} since start", Modifier.width(150.dp))
                Cell("Disk", "r ${bytesText(s.blockRead)}", "w ${bytesText(s.blockWrite)}", Modifier.width(150.dp))
                Cell("Processes", s.pids.toInt().toString(), "", Modifier.width(150.dp))
            }
        }
    }
}

/**
 * A shell in the container, on a plain screen: its output as text (colour and cursor codes left out) and a line to
 * type in, with the keys a phone has no key for. A program that draws on the whole screen (top, vim) does not show well here.
 */
@Composable
private fun ShellView(host: String, name: String, repo: LabRepository, ask: suspend (suspend LabApi.() -> ZoneAnswer) -> ZoneAnswer) {
    var text by remember(name) { mutableStateOf("") }
    var status by remember(name) { mutableStateOf("opening…") }
    var line by remember(name) { mutableStateOf("") }
    var id by remember(name) { mutableStateOf<String?>(null) }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(name) {
        val open = ask { zoneOpen(host, "shell", name, 80, 24) }
        if (!open.ok) { status = "✕ " + open.refusal(); return@LaunchedEffect }
        id = open.id
        status = "open · closes after 3 minutes without a key"
        followStream(repo, open.id, onText = { text = tailLines(text + plainTerminal(it), LOG_LINES); scroll.scrollTo(scroll.maxValue) }) { status = "ended: $it"; id = null }
    }
    DisposableEffect(name) { onDispose { id?.let { i -> repo.zoneCloseLater(i) } } }
    fun send(raw: String) {
        val i = id ?: return
        scope.launch {
            val data = android.util.Base64.encodeToString(raw.toByteArray(), android.util.Base64.NO_WRAP)
            repo.api()?.let { api -> runCatching { api.zoneInput(i, data) } }
        }
    }
    Text(status, fontSize = 12.sp, color = Lab.muted)
    Mono(text.ifEmpty { "…" }, Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 320.dp).verticalScroll(scroll))
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SHELL_KEYS.forEach { (label, seq) -> OutlinedButton(onClick = { send(seq) }, enabled = id != null) { Text(label, fontSize = 12.sp) } }
    }
    OutlinedTextField(
        line, { line = it }, singleLine = true, enabled = id != null, label = { Text("Command") }, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onSend = { send(line + "\n"); line = "" }),
    )
}
