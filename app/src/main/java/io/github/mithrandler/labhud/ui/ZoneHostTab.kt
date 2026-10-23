package io.github.mithrandler.labhud.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Alignment
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mithrandler.labhud.data.LabApi
import io.github.mithrandler.labhud.data.ZoneActivity
import io.github.mithrandler.labhud.data.ZoneAnswer
import io.github.mithrandler.labhud.data.JOB_KINDS
import io.github.mithrandler.labhud.data.UPDATE_JOB
import io.github.mithrandler.labhud.data.ZoneHost
import io.github.mithrandler.labhud.data.ZoneJob
import io.github.mithrandler.labhud.data.ZoneGuest
import io.github.mithrandler.labhud.data.updatesText
import io.github.mithrandler.labhud.data.zoneGuests
import io.github.mithrandler.labhud.data.daysText
import io.github.mithrandler.labhud.data.jobOk
import io.github.mithrandler.labhud.data.zoneJobDelete
import io.github.mithrandler.labhud.data.zoneJobRun
import io.github.mithrandler.labhud.data.zoneJobSave
import io.github.mithrandler.labhud.data.zoneJobs
import io.github.mithrandler.labhud.data.ZoneUnit
import io.github.mithrandler.labhud.data.activityText
import io.github.mithrandler.labhud.data.refusal
import io.github.mithrandler.labhud.data.uptimeText
import io.github.mithrandler.labhud.data.zoneActivity
import io.github.mithrandler.labhud.data.zoneAuditVerify
import io.github.mithrandler.labhud.data.zoneHostInfo
import io.github.mithrandler.labhud.data.zoneHostPower
import io.github.mithrandler.labhud.data.zoneHostUpdate
import io.github.mithrandler.labhud.data.zoneUnit
import io.github.mithrandler.labhud.data.zoneUnitStop
import kotlinx.coroutines.launch

/* Host in Servers: the system's facts, Update OS / Reboot / Shutdown (the last two and a unit's Stop ask for the name typed), the systemd
   units (a sheet per unit with Start / Restart / Stop) and what was done to this host, with a check of the audit log's hash chain. */

@Composable
internal fun HostTab(h: ZoneHost, ask: ZoneAsk, gate: Gate) {
    val scope = rememberCoroutineScope()
    var tick by remember(h.name) { mutableIntStateOf(0) }
    var info by remember(h.name) { mutableStateOf<ZoneAnswer?>(null) }
    var message by remember(h.name) { mutableStateOf("") }
    var said by remember(h.name) { mutableStateOf("") }
    var open by remember(h.name) { mutableStateOf<ZoneUnit?>(null) }
    var typing by remember(h.name) { mutableStateOf<String?>(null) }        // update, reboot or shutdown waiting for the name
    var log by remember(h.name) { mutableStateOf<List<ZoneActivity>>(emptyList()) }
    var more by remember(h.name) { mutableStateOf(false) }
    var chain by remember(h.name) { mutableStateOf("") }
    var jobs by remember(h.name) { mutableStateOf<List<ZoneJob>>(emptyList()) }
    var editing by remember(h.name) { mutableStateOf<ZoneJob?>(null) }       // a job open in the editor ("new" has an empty id)
    LaunchedEffect(h.name, tick) {
        val r = ask { zoneHostInfo(h.name) }
        if (r.ok) { info = r; message = "" } else message = r.refusal()
        val a = ask { zoneActivity(h.name, 0) }
        if (a.ok) { log = a.rows; more = a.more }
        val j = ask { zoneJobs() }
        if (j.ok) jobs = j.jobs.filter { it.agent == h.name || it.agent == "*" }
    }
    fun run(label: String, call: suspend LabApi.() -> ZoneAnswer) = gate.press {
        said = "$label…"
        scope.launch { val r = ask(call); said = if (r.ok) "" else "$label: ${r.refusal()}"; tick++ }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = LabScale.space4).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(LabScale.space2)) {
        Said(message.ifEmpty { said })
        val r = info
        if (r == null) { if (message.isEmpty()) Text("Loading…", color = Lab.muted, modifier = Modifier.padding(top = LabScale.space3)); return@Column }
        val sys = r.host
        listOf("Host" to (sys?.hostname?.ifEmpty { h.name } ?: h.name), "System" to (sys?.os.orEmpty().ifEmpty { "–" }), "Kernel" to (sys?.kernel.orEmpty().ifEmpty { "–" }),
            "Arch" to (sys?.arch.orEmpty().ifEmpty { "–" }), "Up" to uptimeText(r.uptime), "Agent" to r.version.ifEmpty { "–" })
            .plus(listOfNotNull(r.updates?.let { "Updates" to updatesText(it) })).forEach { (k, v) ->
            Row { Text(k, fontSize = LabScale.textSm, color = if (k == "Updates" && r.updates?.pending != 0) Lab.red else Lab.muted, modifier = Modifier.padding(end = LabScale.space3).fillMaxWidth(0.25f)); Text(v, fontSize = LabScale.textSm, color = Lab.text) }
        }
        Row(Modifier.padding(top = LabScale.space2), horizontalArrangement = Arrangement.spacedBy(LabScale.space2)) {
            OutlinedButton(enabled = r.can?.update == true, onClick = { typing = "update" }) { Text("Update OS") }
            OutlinedButton(enabled = r.can?.power == true, onClick = { typing = "reboot" }) { Text("Reboot", color = Lab.red) }
            OutlinedButton(enabled = r.can?.power == true, onClick = { typing = "shutdown" }) { Text("Shutdown", color = Lab.red) }
        }
        if (r.can?.update != true) Text("Tick Host for this agent in Settings > Hosts and agents to update or reboot it here.", fontSize = LabScale.textSm, color = Lab.muted)
        Text("Systemd units" + if (r.failed.isNotEmpty()) " · ${r.failed.size} failed" else "", fontSize = LabScale.textMd, fontWeight = FontWeight.SemiBold, color = Lab.text, modifier = Modifier.padding(top = LabScale.space3))
        r.units.forEach { u ->
            Row2(u.name + if (u.ticked) "" else "  (read only)", u.description, u.active + if (u.sub.isNotEmpty() && u.sub != u.active) " · ${u.sub}" else "",
                tone = if (u.active == "failed" || u.name in r.failed) 1 else 0) { open = u }
        }
        Text("Scheduler", fontSize = LabScale.textMd, fontWeight = FontWeight.SemiBold, color = Lab.text, modifier = Modifier.padding(top = LabScale.space3))
        OutlinedButton(onClick = { editing = ZoneJob(agent = h.name, kind = "prune", days = listOf(6)) }) { Text("Add job") }
        if (jobs.isEmpty()) Text("No job for this host yet.", fontSize = LabScale.textSm, color = Lab.muted)
        jobs.forEach { j ->
            val last = if (j.last.t > 0) (if (j.last.ok) "ok" else "failed") + " · " + j.last.day else "never run"
            Row2(j.name + if (j.enabled) "" else "  (off)", daysText(j.days) + " " + j.at + if (j.agent == "*") " · all hosts" else "", last,
                tone = if (j.last.t > 0 && !j.last.ok) 1 else 0) { editing = j }
        }
        Text("Activity", fontSize = LabScale.textMd, fontWeight = FontWeight.SemiBold, color = Lab.text, modifier = Modifier.padding(top = LabScale.space3))
        OutlinedButton(onClick = {
            scope.launch { val v = ask { zoneAuditVerify() }; chain = if (!v.ok) v.refusal() else if (v.intact) "Intact: ${v.checked} entries checked." else "Broken: ${v.broken}" }
        }) { Text("Verify chain") }
        Said(chain)
        if (log.isEmpty()) Text("Nothing done from here yet.", fontSize = LabScale.textSm, color = Lab.muted)
        log.forEach { e -> Row2(activityText(e), e.target, e.ts.replace("T", " ").take(16), e.who, tone = if (e.ok) 0 else 1) {} }
        if (more) OutlinedButton(onClick = {
            scope.launch { val a = ask { zoneActivity(h.name, log.size) }; if (a.ok) { log = log + a.rows; more = a.more } }
        }) { Text("Older") }
    }
    editing?.let { j -> LabSheet(onClose = { editing = null }) { JobSheet(j, h.name, ask, gate, onDone = { editing = null; tick++ }) } }
    open?.let { u -> LabSheet(onClose = { open = null }) { UnitSheet(u, h.name, run = { label, call -> open = null; run(label, call) }) } }
    typing?.let { what ->
        val title = when (what) { "update" -> "Update the packages of ${h.name}?"; "reboot" -> "Reboot ${h.name}?"; else -> "Shut down ${h.name}?" }
        TypedConfirm(title, h.name, when (what) { "update" -> "Update"; "reboot" -> "Reboot"; else -> "Shut down" }, onOk = {
            typing = null
            if (what == "update") run("Update") { zoneHostUpdate(h.name) } else run(what.replaceFirstChar { it.uppercase() }) { zoneHostPower(h.name, what, h.name) }
        }, onDismiss = { typing = null })
    }
}

@Composable
private fun UnitSheet(u: ZoneUnit, host: String, run: (String, suspend LabApi.() -> ZoneAnswer) -> Unit) {
    var stopping by remember(u.name) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = LabScale.space4).padding(bottom = LabScale.space5).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(LabScale.space2)) {
        Text(u.name, fontSize = LabScale.textWallNum, fontWeight = FontWeight.SemiBold, color = Lab.text, modifier = Modifier.padding(end = LabScale.touch))
        Text("${u.active} · ${u.sub}", fontSize = LabScale.textSm, color = Lab.muted)
        Text(u.description, fontSize = LabScale.textSm, color = Lab.sub)
        Row(horizontalArrangement = Arrangement.spacedBy(LabScale.space2)) {
            OutlinedButton(enabled = u.ticked, onClick = { run("Start ${u.name}") { zoneUnit(host, u.name, "start") } }) { Text("Start") }
            OutlinedButton(enabled = u.ticked, onClick = { run("Restart ${u.name}") { zoneUnit(host, u.name, "restart") } }) { Text("Restart") }
            OutlinedButton(enabled = u.ticked, onClick = { stopping = true }) { Text("Stop", color = Lab.red) }
        }
        if (!u.ticked) Text("Not ticked: tick this unit for the agent in Settings > Hosts and agents to act on it.", fontSize = LabScale.textSm, color = Lab.muted)
    }
    if (stopping) TypedConfirm("Stop ${u.name}?", u.name, "Stop", onOk = { stopping = false; run("Stop ${u.name}") { zoneUnitStop(host, u.name, u.name) } }, onDismiss = { stopping = false })
}

/* The editor of one job: nothing is saved until Save, Discard closes it. Delete asks for the name typed; Run now shows what the host answered. */
@Composable
private fun JobSheet(job: ZoneJob, host: String, ask: ZoneAsk, gate: Gate, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    val isNew = job.id.isEmpty()
    var name by remember(job.id) { mutableStateOf(job.name) }
    var kind by remember(job.id) { mutableStateOf(job.kind) }
    var at by remember(job.id) { mutableStateOf(job.at) }
    var days by remember(job.id) { mutableStateOf(job.days.toSet()) }
    var enabled by remember(job.id) { mutableStateOf(job.enabled) }
    var allHosts by remember(job.id) { mutableStateOf(job.agent == "*") }
    var said by remember(job.id) { mutableStateOf("") }
    var deleting by remember(job.id) { mutableStateOf(false) }
    var vmids by remember(job.id) { mutableStateOf(job.vmids.toSet()) }
    var storage by remember(job.id) { mutableStateOf(job.storage) }
    var mode by remember(job.id) { mutableStateOf(job.mode) }
    var guests by remember(job.id) { mutableStateOf<List<ZoneGuest>>(emptyList()) }
    LaunchedEffect(kind) { if (kind == "backup") { val g = ask { zoneGuests(host) }; if (g.ok) guests = g.guests.filter { it.ticked && !it.template } } }
    val agent = if (kind == "update" && allHosts) "*" else if (job.agent == "*") host else job.agent
    Column(Modifier.fillMaxWidth().padding(horizontal = LabScale.space4).padding(bottom = LabScale.space5).heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.9f).dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(LabScale.space2)) {
        Text(if (isNew) "New job" else job.name, fontSize = LabScale.textWallNum, fontWeight = FontWeight.SemiBold, color = Lab.text, modifier = Modifier.padding(end = LabScale.touch))
        OutlinedTextField(name, { name = it.take(60) }, singleLine = true, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(LabScale.space2)) {
            JOB_KINDS.forEach { (k, label) -> FilterChip(selected = kind == k, enabled = isNew, onClick = { kind = k }, label = { Text(label) }) }
        }
        if (kind == "update") Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(allHosts, { allHosts = it }, enabled = job.id != UPDATE_JOB)
            Text("  Every host" + if (allHosts) "" else " (only $host)", color = Lab.text, fontSize = LabScale.textMd)
        }
        if (kind == "backup") {
            Text("Guests to back up", fontSize = LabScale.textSm, color = Lab.muted)
            if (guests.isEmpty()) Text("No ticked guest on this host.", fontSize = LabScale.textSm, color = Lab.muted)
            guests.forEach { g ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(g.id in vmids, { vmids = if (it) vmids + g.id else vmids - g.id })
                    Text("${g.id} ${g.name}", fontSize = LabScale.textSm, color = Lab.text)
                }
            }
            OutlinedTextField(storage, { storage = it.take(40) }, singleLine = true, label = { Text("Backup storage (e.g. local)") }, modifier = Modifier.fillMaxWidth())
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(LabScale.space2)) {
                listOf("snapshot", "suspend", "stop").forEach { m -> FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(m) }) }
            }
        }
        OutlinedTextField(at, { at = it.take(5) }, singleLine = true, label = { Text("Time (HH:MM, the server's clock)") }, modifier = Modifier.fillMaxWidth())
        Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").forEachIndexed { i, d ->
                Checkbox(i in days, { days = if (it) days + i else days - i })
                Text(d, fontSize = LabScale.textSm, color = Lab.sub, modifier = Modifier.padding(end = LabScale.space2))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) { Switch(enabled, { enabled = it }); Text("  Enabled", color = Lab.text, fontSize = LabScale.textMd) }
        if (!isNew && job.last.t > 0) Text("Last run: ${job.last.day} · " + (if (job.last.ok) "ok" else "failed") + " · ${job.last.text}", fontSize = LabScale.textSm, color = Lab.muted)
        Said(said)
        Row(horizontalArrangement = Arrangement.spacedBy(LabScale.space2)) {
            OutlinedButton(enabled = jobOk(name, at, days, kind, vmids, storage), onClick = {
                gate.press { scope.launch {
                    val r = ask { zoneJobSave(job.copy(name = name.trim(), kind = kind, agent = agent, at = at, days = days.sorted(), enabled = enabled, vmids = vmids.toList(), storage = storage.trim(), mode = mode)) }
                    if (r.ok) onDone() else said = "Save: ${r.refusal()}"
                } }
            }) { Text("Save") }
            OutlinedButton(onClick = onDone) { Text("Discard") }
            if (!isNew) OutlinedButton(onClick = {
                gate.press { scope.launch { said = "Running…"; val r = ask { zoneJobRun(job.id) }; said = if (r.ok) "Done: ${r.text}" else "Failed: ${r.refusal()}" } }
            }) { Text("Run now") }
            if (!isNew && job.id != UPDATE_JOB) OutlinedButton(onClick = { deleting = true }) { Text("Delete", color = Lab.red) }
        }
    }
    if (deleting) TypedConfirm("Delete ${job.name}?", job.name, "Delete", onOk = {
        deleting = false
        gate.press { scope.launch { val r = ask { zoneJobDelete(job.id, job.name) }; if (r.ok) onDone() else said = "Delete: ${r.refusal()}" } }
    }, onDismiss = { deleting = false })
}
