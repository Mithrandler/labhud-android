package io.github.mithrandler.labhud.ui.settings

import io.github.mithrandler.labhud.ui.LabScale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.mithrandler.labhud.data.AlertRule
import io.github.mithrandler.labhud.data.AlertSetup
import io.github.mithrandler.labhud.data.LabRepository
import io.github.mithrandler.labhud.data.alertMarkMax
import io.github.mithrandler.labhud.data.alertRule
import io.github.mithrandler.labhud.data.quietText
import io.github.mithrandler.labhud.data.watching
import io.github.mithrandler.labhud.ui.GateDialogs
import io.github.mithrandler.labhud.ui.Lab
import io.github.mithrandler.labhud.ui.Panel
import io.github.mithrandler.labhud.ui.rememberGate
import kotlinx.coroutines.launch

/**
 * The alert rules on hosts and the quiet hours, as the wall's ⚙ > Alerts edits them. Each rule is a card whose fields change on
 * the spot; nothing is saved until Save (rules and quiet hours go together), Discard drops the changes. Behind the gate.
 * Where the news goes (ntfy, app) stays in Notifications; the warn/crit marks of every card and the screen's beep are the wall's.
 */
@Composable
fun AlertsSection(repo: LabRepository, nav: SettingsNav) {
    val scope = rememberCoroutineScope()
    val gate = rememberGate(repo)
    var setup by remember { mutableStateOf<AlertSetup?>(null) }
    var rules by remember { mutableStateOf<List<AlertRule>>(emptyList()) }
    var quiet by remember { mutableStateOf(0 to 0) }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    fun adopt(s: AlertSetup) {
        setup = s
        rules = s.rules.mapNotNull { alertRule(it) }
        quiet = (s.notify.quiet?.getOrNull(0) ?: 0) to (s.notify.quiet?.getOrNull(1) ?: 0)
    }
    fun load() = scope.launch {
        val api = repo.api() ?: return@launch
        runCatching { api.alertSetup() }.onSuccess { adopt(it) }.onFailure { message = it.message ?: "Not loaded." }
    }
    LaunchedEffect(Unit) { gate.press { load() } }
    val saved = setup?.let { s -> s.rules.mapNotNull { alertRule(it) } to ((s.notify.quiet?.getOrNull(0) ?: 0) to (s.notify.quiet?.getOrNull(1) ?: 0)) }
    val dirty = saved != null && saved != (rules to quiet)

    fun save() = gate.press {
        val s = setup ?: return@press
        busy = true
        scope.launch {
            val r = repo.api()?.let { api -> runCatching { api.saveAlerts(rules, s.notify.via, quiet) }.getOrNull() }
            message = if (r?.ok == true) "" else "Not saved: " + (r?.error?.ifBlank { null } ?: "refused") +
                if (r?.error?.contains("take over") == true) " (on the wall: ⚙ > System > Take over)" else ""
            // labhud applies a save a moment later, so reading it back now may give the old numbers: what was saved stays on screen
            if (r?.ok == true) setup = s.copy(rules = rules.map { it.json() }, notify = s.notify.copy(quiet = listOf(quiet.first, quiet.second)))
            busy = false
        }
    }

    SettingsPage(section("alerts")!!, nav) {
        if (setup == null) item { Unlock { gate.press { load() } } }
        if (message.isNotEmpty()) item { Text(message, color = Lab.warn, fontSize = LabScale.textSm) }
        setup?.let { s ->
            if (!s.managed) item { Text("On the wall, ⚙ > System > Take over first: then LabHUD keeps the rules and this page changes them.", fontSize = LabScale.textSm, color = Lab.warn) }
            if (dirty) item {
                Panel("") {
                    Text("Changes not saved yet.", fontSize = LabScale.textMd, fontWeight = FontWeight.Medium)
                    Row(Modifier.padding(top = LabScale.space2), horizontalArrangement = Arrangement.spacedBy(LabScale.space2)) {
                        Button(onClick = { save() }, enabled = !busy && s.managed) { Text("Save") }
                        OutlinedButton(onClick = { adopt(s) }) { Text("Discard") }
                    }
                }
            }
            item {
                Text("A host's numbers come from its agent. \"All hosts\" is every card with an agent of its own; a host's own rule wins over it for the same thing.",
                    fontSize = LabScale.textSm, color = Lab.muted)
            }
            rules.forEachIndexed { i, r ->
                item(key = "rule$i") {
                    RuleCard(r, s, onChange = { n -> rules = rules.toMutableList().also { it[i] = n } }, onRemove = { rules = rules.toMutableList().also { it.removeAt(i) } })
                }
            }
            if (rules.isEmpty()) item { Text("No rules: only the history and the marks on the cards.", fontSize = LabScale.textSm, color = Lab.muted) }
            item { OutlinedButton(onClick = { rules = rules + AlertRule() }) { Text("+ Rule") } }
            heading("Quiet hours")
            item { QuietCard(quiet) { quiet = it } }
        }
    }
    GateDialogs(gate, nav.signIn)
}

@Composable
private fun Pick(label: String, options: List<Pair<String, String>>, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) { Text(label, maxLines = 1) }
        DropdownMenu(open, { open = false }) {
            options.forEach { (v, l) -> DropdownMenuItem(text = { Text(l) }, onClick = { open = false; onPick(v) }) }
        }
    }
}

/** A number field that keeps its text while it is typed and tells only a value in [min, max]. */
@Composable
private fun Num(value: Int, min: Int, max: Int, label: String, onValue: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    val n = text.toIntOrNull()
    OutlinedTextField(
        text, { t -> text = t.filter { it.isDigit() }.take(4); text.toIntOrNull()?.takeIf { it in min..max }?.let(onValue) },
        singleLine = true, label = { Text(label) }, isError = n == null || n !in min..max,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(LabScale.chartH),
    )
}

@Composable
private fun RuleCard(r: AlertRule, s: AlertSetup, onChange: (AlertRule) -> Unit, onRemove: () -> Unit) {
    val hosts = listOf("*" to "All hosts") + s.hosts.map { it.id to it.name.ifBlank { it.id } }
    val noMark = r.what in s.noMark
    Panel("") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(LabScale.space2)) {
            Pick(hosts.firstOrNull { it.first == r.on }?.second ?: r.on, hosts) { onChange(r.copy(on = it)) }
            Pick(s.what[r.what] ?: r.what, s.what.entries.map { it.key to it.value }) { onChange(r.watching(it, s.noMark)) }
            if (r.what in s.urgent) Text("urgent", fontSize = LabScale.textXs, color = Lab.warn)
            Box(Modifier.weight(1f)) {}
            TextButton(onClick = onRemove) { Text("✕", color = Lab.red) }
        }
        Row(Modifier.padding(top = LabScale.space1), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(LabScale.space2)) {
            if (!noMark) {
                Num(r.above ?: 90, 1, alertMarkMax(r.what), "above ${if (r.what == "temp") "°C" else "%"}") { onChange(r.copy(above = it)) }
            }
            Num(r.minutes, 0, 1440, "minutes") { onChange(r.copy(minutes = it)) }
        }
    }
}

@Composable
private fun QuietCard(quiet: Pair<Int, Int>, onChange: (Pair<Int, Int>) -> Unit) {
    val on = quiet.first != quiet.second
    Panel("") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(on, { onChange(if (it) 23 to 7 else 0 to 0) })
            Text("  " + if (on) quietText(quiet.first, quiet.second) else "Off", fontSize = LabScale.textMd)
        }
        if (on) Row(Modifier.padding(top = LabScale.space2), horizontalArrangement = Arrangement.spacedBy(LabScale.space2), verticalAlignment = Alignment.CenterVertically) {
            Num(quiet.first, 0, 23, "from (h)") { onChange(it to quiet.second) }
            Num(quiet.second, 0, 23, "to (h)") { onChange(quiet.first to it) }
        }
        Text("Only the urgent alerts are sent in these hours, by LabHUD's clock; the rest wait for the morning.", fontSize = LabScale.textSm, color = Lab.muted,
            modifier = Modifier.fillMaxWidth().padding(top = LabScale.space2))
    }
}
