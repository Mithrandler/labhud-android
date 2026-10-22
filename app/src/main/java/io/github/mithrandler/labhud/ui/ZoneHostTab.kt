package io.github.mithrandler.labhud.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
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
import io.github.mithrandler.labhud.data.ZoneHost
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
    LaunchedEffect(h.name, tick) {
        val r = ask { zoneHostInfo(h.name) }
        if (r.ok) { info = r; message = "" } else message = r.refusal()
        val a = ask { zoneActivity(h.name, 0) }
        if (a.ok) { log = a.rows; more = a.more }
    }
    fun run(label: String, call: suspend LabApi.() -> ZoneAnswer) = gate.press {
        said = "$label…"
        scope.launch { val r = ask(call); said = if (r.ok) "" else "$label: ${r.refusal()}"; tick++ }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Said(message.ifEmpty { said })
        val r = info
        if (r == null) { if (message.isEmpty()) Text("Loading…", color = Lab.muted, modifier = Modifier.padding(top = 12.dp)); return@Column }
        val sys = r.host
        listOf("Host" to (sys?.hostname?.ifEmpty { h.name } ?: h.name), "System" to (sys?.os.orEmpty().ifEmpty { "–" }), "Kernel" to (sys?.kernel.orEmpty().ifEmpty { "–" }),
            "Arch" to (sys?.arch.orEmpty().ifEmpty { "–" }), "Up" to uptimeText(r.uptime), "Agent" to r.version.ifEmpty { "–" }).forEach { (k, v) ->
            Row { Text(k, fontSize = 13.sp, color = Lab.muted, modifier = Modifier.padding(end = 12.dp).fillMaxWidth(0.25f)); Text(v, fontSize = 13.sp, color = Lab.text) }
        }
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = r.can?.update == true, onClick = { typing = "update" }) { Text("Update OS") }
            OutlinedButton(enabled = r.can?.power == true, onClick = { typing = "reboot" }) { Text("Reboot", color = Lab.red) }
            OutlinedButton(enabled = r.can?.power == true, onClick = { typing = "shutdown" }) { Text("Shutdown", color = Lab.red) }
        }
        if (r.can?.update != true) Text("Tick Host for this agent in Settings > Hosts and agents to update or reboot it here.", fontSize = 12.sp, color = Lab.muted)
        Text("Systemd units" + if (r.failed.isNotEmpty()) " · ${r.failed.size} failed" else "", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Lab.text, modifier = Modifier.padding(top = 10.dp))
        r.units.forEach { u ->
            Row2(u.name + if (u.ticked) "" else "  (read only)", u.description, u.active + if (u.sub.isNotEmpty() && u.sub != u.active) " · ${u.sub}" else "",
                tone = if (u.active == "failed" || u.name in r.failed) 1 else 0) { open = u }
        }
        Text("Activity", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Lab.text, modifier = Modifier.padding(top = 10.dp))
        OutlinedButton(onClick = {
            scope.launch { val v = ask { zoneAuditVerify() }; chain = if (!v.ok) v.refusal() else if (v.intact) "Intact: ${v.checked} entries checked." else "Broken: ${v.broken}" }
        }) { Text("Verify chain") }
        Said(chain)
        if (log.isEmpty()) Text("Nothing done from here yet.", fontSize = 13.sp, color = Lab.muted)
        log.forEach { e -> Row2(activityText(e), e.target, e.ts.replace("T", " ").take(16), e.who, tone = if (e.ok) 0 else 1) {} }
        if (more) OutlinedButton(onClick = {
            scope.launch { val a = ask { zoneActivity(h.name, log.size) }; if (a.ok) { log = log + a.rows; more = a.more } }
        }) { Text("Older") }
    }
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
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(u.name, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Lab.text, modifier = Modifier.padding(end = 48.dp))
        Text("${u.active} · ${u.sub}", fontSize = 13.sp, color = Lab.muted)
        Text(u.description, fontSize = 13.sp, color = Lab.sub)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = u.ticked, onClick = { run("Start ${u.name}") { zoneUnit(host, u.name, "start") } }) { Text("Start") }
            OutlinedButton(enabled = u.ticked, onClick = { run("Restart ${u.name}") { zoneUnit(host, u.name, "restart") } }) { Text("Restart") }
            OutlinedButton(enabled = u.ticked, onClick = { stopping = true }) { Text("Stop", color = Lab.red) }
        }
        if (!u.ticked) Text("Not ticked: tick this unit for the agent in Settings > Hosts and agents to act on it.", fontSize = 12.sp, color = Lab.muted)
    }
    if (stopping) TypedConfirm("Stop ${u.name}?", u.name, "Stop", onOk = { stopping = false; run("Stop ${u.name}") { zoneUnitStop(host, u.name, u.name) } }, onDismiss = { stopping = false })
}
