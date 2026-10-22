package io.github.mithrandler.labhud.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mithrandler.labhud.data.ZoneAnswer
import io.github.mithrandler.labhud.data.ZoneEntry
import io.github.mithrandler.labhud.data.ZoneFile
import io.github.mithrandler.labhud.data.ZoneHost
import io.github.mithrandler.labhud.data.ZoneImage
import io.github.mithrandler.labhud.data.ZoneNetwork
import io.github.mithrandler.labhud.data.ZoneVolume
import io.github.mithrandler.labhud.data.bytesText
import io.github.mithrandler.labhud.data.childOf
import io.github.mithrandler.labhud.data.crumbs
import io.github.mithrandler.labhud.data.filteredImages
import io.github.mithrandler.labhud.data.filteredNetworks
import io.github.mithrandler.labhud.data.filteredVolumes
import io.github.mithrandler.labhud.data.parentOf
import io.github.mithrandler.labhud.data.refusal
import io.github.mithrandler.labhud.data.title
import io.github.mithrandler.labhud.data.usedText
import io.github.mithrandler.labhud.data.volumeSize
import io.github.mithrandler.labhud.data.zoneClean
import io.github.mithrandler.labhud.data.zoneImages
import io.github.mithrandler.labhud.data.zoneNetwork
import io.github.mithrandler.labhud.data.zoneNetworks
import io.github.mithrandler.labhud.data.zonePull
import io.github.mithrandler.labhud.data.zoneRm
import io.github.mithrandler.labhud.data.zoneVolumeAccess
import io.github.mithrandler.labhud.data.zoneVolumeLs
import io.github.mithrandler.labhud.data.zoneVolumeRead
import io.github.mithrandler.labhud.data.zoneVolumes
import kotlinx.coroutines.launch

/* The host's Docker in Servers: its images, volumes and networks, what uses each, and what may be removed. Removing asks for the name typed;
   a volume is opened for browsing (read only) by the admin, one by one. The host's "Unused images" tick in Hosts and agents is what lets labhud ask at all. */

/** The word typed before something is removed or cleaned: the button is on only when it is exactly [word]. */
@Composable
private fun TypedConfirm(title: String, word: String, button: String, onOk: () -> Unit, onDismiss: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) },
        text = { Column { Text("Type  $word  to confirm.", fontSize = 13.sp, color = Lab.sub)
            OutlinedTextField(typed, { typed = it }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) } },
        confirmButton = { TextButton(enabled = typed == word, onClick = { onDismiss(); onOk() }) { Text(button) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Said(text: String) { if (text.isNotEmpty()) Text(text, color = Lab.warn, fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp)) }

@Composable
private fun Row2(title: String, sub: String, right: String, rightSub: String = "", tone: Int = 0, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, color = Lab.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, fontSize = 12.sp, color = if (tone == 1) Lab.warn else Lab.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(right, fontSize = 13.sp, color = Lab.sub)
            if (rightSub.isNotEmpty()) Text(rightSub, fontSize = 11.sp, color = Lab.muted)
        }
    }
    HorizontalDivider(color = Lab.line)
}

/** What loads a list, asks again after a change, and says why when it cannot. */
@Composable
private fun <T> Loaded(host: String, ask: ZoneAsk, call: suspend io.github.mithrandler.labhud.data.LabApi.(Boolean) -> ZoneAnswer, pick: (ZoneAnswer) -> List<T>,
                       content: @Composable (rows: List<T>?, message: String, refresh: () -> Unit) -> Unit) {
    var rows by remember(host) { mutableStateOf<List<T>?>(null) }
    var message by remember(host) { mutableStateOf("") }
    var tick by remember(host) { mutableIntStateOf(0) }
    LaunchedEffect(host, tick) {
        val r = ask { call(tick > 0) }
        if (r.ok) { rows = pick(r); message = "" } else message = r.refusal()
    }
    content(rows, message) { tick++ }
}

// ---- images ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ImagesTab(h: ZoneHost, ask: ZoneAsk, gate: Gate) {
    var query by rememberSaveable { mutableStateOf("") }
    var open by remember { mutableStateOf<String?>(null) }
    var pulling by remember { mutableStateOf(false) }
    var cleaning by remember { mutableStateOf<String?>(null) }
    var said by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    Loaded(h.name, ask, { fresh -> zoneImages(h.name, fresh) }, { it.images }) { rows, message, refresh ->
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            OutlinedTextField(query, { query = it.take(60) }, singleLine = true, label = { Text("Filter") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pulling = true }) { Text("Pull image…") }
                OutlinedButton(onClick = { cleaning = "images" }) { Text("Clean unused") }
                OutlinedButton(onClick = { cleaning = "dangling" }) { Text("Clean untagged") }
            }
            Said(message.ifEmpty { said })
            if (rows == null && message.isEmpty()) Text("Loading…", color = Lab.muted)
            LazyColumn(Modifier.fillMaxSize()) {
                items(rows.orEmpty().filteredImages(query), key = { it.id }) { i ->
                    Row2(i.title() + if (i.tags.size > 1) "  +${i.tags.size - 1}" else "", usedText(i.used, i.usedBy), bytesText(i.size.toDouble()),
                        if (i.dangling) "untagged" else "", tone = if (i.used == 0) 1 else 0) { open = i.id }
                }
            }
        }
        val img = rows?.firstOrNull { it.id == open }
        if (img != null) LabSheet(onClose = { open = null }) {
            ImageSheet(img, h.name, ask, gate) { said = it; open = null; refresh() }
        }
        if (pulling) PullDialog(onDismiss = { pulling = false }) { ref ->
            gate.press { scope.launch { said = "Pulling $ref…"; val r = ask { zonePull(h.name, ref) }; said = if (r.ok) "" else "pull: ${r.refusal()}"; refresh() } }
        }
        cleaning?.let { what ->
            TypedConfirm("Clean " + if (what == "images") "unused images" else "untagged layers", what, "Clean", onOk = {
                gate.press { scope.launch { val r = ask { zoneClean(h.name, what, what) }; said = if (r.ok) r.text else "clean: ${r.refusal()}"; refresh() } }
            }, onDismiss = { cleaning = null })
        }
    }
}

@Composable
private fun PullDialog(onDismiss: () -> Unit, onPull: (String) -> Unit) {
    var ref by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Pull an image") },
        text = { OutlinedTextField(ref, { ref = it.trim().take(200) }, singleLine = true, label = { Text("nginx:1.27") }, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(enabled = ref.isNotEmpty(), onClick = { onDismiss(); onPull(ref) }) { Text("Pull") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun ImageSheet(i: ZoneImage, host: String, ask: ZoneAsk, gate: Gate, done: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var removing by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(i.title(), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Lab.text)
        Text("${bytesText(i.size.toDouble())} · ${i.id} · ${usedText(i.used, i.usedBy)}", fontSize = 13.sp, color = Lab.muted)
        Text("Remove", fontSize = 12.sp, color = Lab.muted, modifier = Modifier.padding(top = 10.dp))
        val names = i.tags.ifEmpty { listOf(i.id) }
        names.forEach { n ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(n, fontSize = 14.sp, color = Lab.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                OutlinedButton(onClick = { removing = n }, enabled = i.used == 0) { Text("Remove") }
            }
        }
        if (i.used > 0) Text("In use: remove the containers that use it first.", fontSize = 12.sp, color = Lab.muted)
        i.tags.firstOrNull()?.let { tag ->
            OutlinedButton(onClick = { gate.press { scope.launch { val r = ask { zonePull(host, tag) }; done(if (r.ok) "" else "pull: ${r.refusal()}") } } }) { Text("Pull again") }
        }
    }
    removing?.let { n ->
        TypedConfirm("Remove $n?", n, "Remove", onOk = {
            gate.press { scope.launch { val r = ask { zoneRm(host, "image", n, n) }; done(if (r.ok) "" else "remove: ${r.refusal()}") } }
        }, onDismiss = { removing = null })
    }
}

// ---- volumes ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VolumesTab(h: ZoneHost, ask: ZoneAsk, gate: Gate) {
    var query by rememberSaveable { mutableStateOf("") }
    var open by remember { mutableStateOf<String?>(null) }
    var said by remember { mutableStateOf("") }
    Loaded(h.name, ask, { fresh -> zoneVolumes(h.name, fresh) }, { it.volumes }) { rows, message, refresh ->
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            OutlinedTextField(query, { query = it.take(60) }, singleLine = true, label = { Text("Filter") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            Said(message.ifEmpty { said })
            if (rows == null && message.isEmpty()) Text("Loading…", color = Lab.muted, modifier = Modifier.padding(top = 8.dp))
            LazyColumn(Modifier.fillMaxSize()) {
                items(rows.orEmpty().filteredVolumes(query), key = { it.name }) { v ->
                    Row2(v.name, usedText(v.used, v.usedBy), volumeSize(v.size), if (v.browse) "browsable" else "", tone = if (v.used == 0) 1 else 0) { open = v.name }
                }
            }
        }
        val vol = rows?.firstOrNull { it.name == open }
        if (vol != null) LabSheet(onClose = { open = null }) {
            VolumeSheet(vol, h.name, ask, gate, onChanged = { refresh() }) { said = it; open = null; refresh() }
        }
    }
}

@Composable
private fun VolumeSheet(v: ZoneVolume, host: String, ask: ZoneAsk, gate: Gate, onChanged: () -> Unit, done: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var removing by remember { mutableStateOf(false) }
    var said by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(v.name, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Lab.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${volumeSize(v.size)} · ${v.driver} · ${usedText(v.used, v.usedBy)}", fontSize = 13.sp, color = Lab.muted)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Browse its files", fontSize = 14.sp, color = Lab.text)
                Text("read only; secrets are hidden", fontSize = 12.sp, color = Lab.muted)
            }
            Switch(checked = v.browse, onCheckedChange = { on ->
                gate.press { scope.launch { val r = ask { zoneVolumeAccess(host, v.name, on) }; if (r.ok) onChanged() else said = r.refusal() } }
            })
        }
        Said(said)
        if (v.browse) FileBrowser(host, v.name, ask)
        OutlinedButton(onClick = { removing = true }, enabled = v.used == 0, modifier = Modifier.padding(top = 8.dp)) { Text("Remove volume") }
        if (v.used > 0) Text("In use: remove the containers that use it first.", fontSize = 12.sp, color = Lab.muted)
    }
    if (removing) TypedConfirm("Remove ${v.name}?", v.name, "Remove", onOk = {
        gate.press { scope.launch { val r = ask { zoneRm(host, "volume", v.name, v.name) }; done(if (r.ok) "" else "remove: ${r.refusal()}") } }
    }, onDismiss = { removing = false })
}

/** A volume's directories and files, read only: a path across the top, a tap on a folder goes in, a tap on a file shows it. */
@Composable
private fun FileBrowser(host: String, volume: String, ask: ZoneAsk) {
    var path by remember(volume) { mutableStateOf("/") }
    var entries by remember(volume) { mutableStateOf<List<ZoneEntry>?>(null) }
    var truncated by remember(volume) { mutableStateOf(false) }
    var message by remember(volume) { mutableStateOf("") }
    var file by remember(volume) { mutableStateOf<ZoneFile?>(null) }
    LaunchedEffect(volume, path) {
        entries = null
        val r = ask { zoneVolumeLs(host, volume, path) }
        if (r.ok) { entries = r.entries; truncated = r.truncated; message = "" } else { message = r.refusal(); entries = emptyList() }
    }
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        crumbs(path).forEachIndexed { n, (label, to) ->
            if (n > 0) Text("›", color = Lab.muted)
            TextButton(onClick = { path = to; file = null }) { Text(label, fontSize = 13.sp) }
        }
    }
    Said(message)
    val f = file
    if (f != null) {
        Text(f.path + " · " + bytesText(f.size.toDouble()) + if (f.truncated) " · first 64 KB" else "", fontSize = 12.sp, color = Lab.muted)
        when {
            f.masked && f.text.isEmpty() -> Text("Looks like a secret: its content is not shown.", color = Lab.warn, fontSize = 13.sp)
            f.binary -> Text("A binary file.", color = Lab.muted, fontSize = 13.sp)
            else -> Mono(f.text.ifEmpty { "(empty)" }, Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState()))
        }
        TextButton(onClick = { file = null }) { Text("Back to the folder") }
    } else Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
        if (entries == null) Text("Loading…", color = Lab.muted)
        if (path != "/") Row2("..", "up", "") { path = parentOf(path) }
        entries.orEmpty().forEach { e ->
            Row2((if (e.dir) "▸ " else "") + e.name, if (e.link) "link" else "", if (e.dir) "" else bytesText(e.size.toDouble())) {
                if (e.dir) path = childOf(path, e.name)
                else if (!e.link) { /* a file */ file = ZoneFile(path = childOf(path, e.name), size = e.size, text = "…"); }
            }
        }
        if (truncated) Text("More than this is in the folder.", fontSize = 12.sp, color = Lab.muted)
    }
    LaunchedEffect(file?.path) {
        val cur = file ?: return@LaunchedEffect
        if (cur.text != "…") return@LaunchedEffect
        val r = ask { zoneVolumeRead(host, volume, cur.path) }
        file = if (r.ok) r.file else ZoneFile(path = cur.path, text = "").also { message = r.refusal() }
    }
}

// ---- networks ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NetworksTab(h: ZoneHost, ask: ZoneAsk, gate: Gate) {
    var query by rememberSaveable { mutableStateOf("") }
    var open by remember { mutableStateOf<String?>(null) }
    var said by remember { mutableStateOf("") }
    Loaded(h.name, ask, { fresh -> zoneNetworks(h.name, fresh) }, { it.networks }) { rows, message, refresh ->
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            OutlinedTextField(query, { query = it.take(60) }, singleLine = true, label = { Text("Filter") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            Said(message.ifEmpty { said })
            if (rows == null && message.isEmpty()) Text("Loading…", color = Lab.muted, modifier = Modifier.padding(top = 8.dp))
            LazyColumn(Modifier.fillMaxSize()) {
                items(rows.orEmpty().filteredNetworks(query), key = { it.name }) { n ->
                    Row2(n.name, usedText(n.used, n.usedBy), n.driver, if (n.builtin) "built in" else "") { open = n.name }
                }
            }
        }
        val net = rows?.firstOrNull { it.name == open }
        if (net != null) LabSheet(onClose = { open = null }) {
            NetworkSheet(net, h.name, ask, gate) { said = it; open = null; refresh() }
        }
    }
}

@Composable
private fun NetworkSheet(n: ZoneNetwork, host: String, ask: ZoneAsk, gate: Gate, done: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var now by remember(n.name) { mutableStateOf<ZoneAnswer?>(null) }
    var removing by remember { mutableStateOf(false) }
    LaunchedEffect(n.name) { now = ask { zoneNetwork(host, n.name) } }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(n.name, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Lab.text)
        Text("${n.driver} · ${n.scope} · ${usedText(n.used, n.usedBy)}", fontSize = 13.sp, color = Lab.muted)
        val a = now
        when {
            a == null -> Text("Loading…", color = Lab.muted)
            !a.ok || a.network == null -> Said(a.refusal())
            else -> Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                if (a.network.containers.isEmpty()) Text("No container is on it.", color = Lab.muted, fontSize = 13.sp)
                a.network.containers.forEach { c -> Row2(c.name, c.ipv4, "") {} }
            }
        }
        OutlinedButton(onClick = { removing = true }, enabled = n.used == 0 && !n.builtin, modifier = Modifier.padding(top = 8.dp)) { Text("Remove network") }
        if (n.builtin) Text("A built-in network is never removed.", fontSize = 12.sp, color = Lab.muted)
    }
    if (removing) TypedConfirm("Remove ${n.name}?", n.name, "Remove", onOk = {
        gate.press { scope.launch { val r = ask { zoneRm(host, "network", n.name, n.name) }; done(if (r.ok) "" else "remove: ${r.refusal()}") } }
    }, onDismiss = { removing = false })
}


/** A bottom sheet with a visible X in its corner; Back and a swipe down close it too. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LabSheet(onClose: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Lab.card) {
        Box(Modifier.fillMaxWidth()) {
            content()
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd)) { Icon(LabIcons.close, "Close") }
        }
    }
}
