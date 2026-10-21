package io.github.mithrandler.labhud.ui.settings

import android.Manifest
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import io.github.mithrandler.labhud.data.AlertSetup
import io.github.mithrandler.labhud.data.AppSettings
import io.github.mithrandler.labhud.data.Channels
import io.github.mithrandler.labhud.data.channels
import io.github.mithrandler.labhud.data.forgetChannel
import io.github.mithrandler.labhud.data.saveChannel
import io.github.mithrandler.labhud.data.saveEvents
import io.github.mithrandler.labhud.data.testChannel
import io.github.mithrandler.labhud.data.LabRepository
import io.github.mithrandler.labhud.data.NotifyMode
import io.github.mithrandler.labhud.push.Push
import io.github.mithrandler.labhud.ui.CardShape
import io.github.mithrandler.labhud.ui.GateDialogs
import io.github.mithrandler.labhud.ui.Lab
import io.github.mithrandler.labhud.ui.Panel
import io.github.mithrandler.labhud.ui.rememberGate
import kotlinx.coroutines.launch

private val VIAS = listOf("ntfy" to "ntfy", "app" to "App", "both" to "Both")

/**
 * Notifications: what this phone does with LabHUD's news (anyone), then, for the admin, where
 * LabHUD sends them for every phone and the ntfy test (what Server management had).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotifySection(repo: LabRepository, nav: SettingsNav) {
    val rights by repo.rights.collectAsStateWithLifecycle()
    val settings by repo.store.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val scope = rememberCoroutineScope()
    val gate = rememberGate(repo)
    var alerts by remember { mutableStateOf<AlertSetup?>(null) }
    var chans by remember { mutableStateOf<Channels?>(null) }
    var message by remember { mutableStateOf("") }

    fun load() = scope.launch {
        val api = repo.api() ?: return@launch
        runCatching { api.alertSetup() }.onSuccess { alerts = it }.onFailure { message = it.message ?: "Not loaded." }
        runCatching { api.channels() }.onSuccess { c -> if (c.ok) chans = c else message = c.error.ifBlank { "Channels refused." } }
    }
    /** A channel's change behind the gate: what it did in the message line, the section anew. */
    fun change(done: String, what: String, call: suspend () -> Pair<Boolean, String>?) = gate.press {
        scope.launch {
            val (ok, error) = runCatching { call() }.getOrElse { false to (it.message ?: "") } ?: return@launch
            message = if (ok) done else "✕ $what: " + error.ifBlank { "refused" }
            if (ok) load()
        }
    }
    LaunchedEffect(rights.admin) { if (rights.admin && repo.mayPress()) load() }

    SettingsPage(section("notifications")!!, nav) {
        heading("On this phone")
        item { NotifyChoice(repo, settings.notify, via = alerts?.notify?.via) }
        if (rights.admin) {
            heading("From LabHUD, for every phone")
            if (alerts == null) item { Unlock { gate.press { load() } } }
            if (message.isNotEmpty()) item { Text(message, color = Lab.warn, fontSize = 13.sp) }
            alerts?.let { setup ->
                item {
                    Panel("") {
                        Text("Where LabHUD sends its news.", fontSize = 13.sp, color = Lab.sub)
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                            VIAS.forEachIndexed { i, (via, label) ->
                                SegmentedButton(selected = setup.notify.via == via, shape = SegmentedButtonDefaults.itemShape(i, VIAS.size), onClick = {
                                    gate.press {
                                        scope.launch {
                                            val r = runCatching { repo.api()?.saveNotify(setup, via) }.getOrNull()
                                            if (r?.ok == true) alerts = setup.copy(notify = setup.notify.copy(via = via))
                                            else message = "Not saved: " + (r?.error?.ifBlank { null } ?: "refused") +
                                                if (r?.error?.contains("take over") == true) " (on the wall: ⚙ > System > Take over)" else ""
                                        }
                                    }
                                }) { Text(label) }
                            }
                        }
                        Text(
                            if (setup.ntfy.set) "ntfy: ${setup.ntfy.host ?: "set"} · ${setup.ntfy.sent} sent" +
                                (setup.ntfy.lastError?.let { " · last error: $it" } ?: "")
                            else "ntfy: no topic set (on the wall: ⚙ > Notifications, or `labhud notify set <url>`).",
                            fontSize = 12.sp, color = if (setup.ntfy.lastError != null) Lab.warn else Lab.muted,
                        )
                        if (setup.ntfy.set) TextButton(onClick = {
                            gate.press {
                                scope.launch {
                                    val r = runCatching { repo.api()?.notifyTest() }.getOrNull()
                                    message = if (r?.ok == true) "✓ Test sent to ntfy." else "✕ " + (r?.error?.ifBlank { null } ?: "not sent")
                                }
                            }
                        }) { Text("Send a test to ntfy") }
                    }
                }
                chans?.let { c ->
                    item {
                        ChannelsPanel(c,
                            onSave = { ch, cfg, sec -> change("✓ ${CHANNEL_NAMES[ch]} saved.", "Not saved") { repo.api()?.saveChannel(ch, cfg, sec)?.let { it.ok to it.error } } },
                            onTest = { ch, cfg, sec ->
                                gate.press {
                                    scope.launch {
                                        val r = runCatching { repo.api()?.testChannel(ch, cfg, sec) }.getOrNull()
                                        message = if (r?.ok == true) "✓ Test sent to ${CHANNEL_NAMES[ch]}." else "✕ " + (r?.error?.ifBlank { null } ?: "not sent")
                                    }
                                }
                            },
                            onForget = { ch -> change("${CHANNEL_NAMES[ch]} forgotten.", "Not forgotten") { repo.api()?.forgetChannel(ch)?.let { it.ok to it.error } } })
                    }
                    item { MatrixPanel(c) { m -> change("", "Not saved") { repo.api()?.saveEvents(m)?.let { it.ok to it.error } } } }
                }
            }
        }
    }
    GateDialogs(gate, nav.signIn)
}

/**
 * Instant (UnifiedPush through the ntfy app: labhud wakes this app, which reads the news itself),
 * ntfy only (the ntfy app shows labhud's own messages), or off. labhud's own `via` decides whether
 * the app is woken at all: [via] is it when the admin's part has loaded, else it is asked here.
 */
@Composable
private fun NotifyChoice(repo: LabRepository, mode: NotifyMode, via: String?) {
    val activity = LocalActivity.current as FragmentActivity
    val scope = rememberCoroutineScope()
    val rights by repo.rights.collectAsStateWithLifecycle()
    var asked by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }
    LaunchedEffect(mode) { asked = runCatching { repo.api()?.notes(Long.MAX_VALUE / 2)?.via }.getOrNull() }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun pick(m: NotifyMode) {
        note = ""
        scope.launch {
            repo.store.setNotify(m)
            if (m == NotifyMode.Instant) {
                Push.channels(activity)
                if (Build.VERSION.SDK_INT >= 33) ask.launch(Manifest.permission.POST_NOTIFICATIONS)
                Push.enable(activity) { ok ->
                    if (!ok) note = "No UnifiedPush app on this phone. Install ntfy (Play Store or F-Droid), open it once, then choose Instant again."
                    else Push.fetch(activity)
                }
            } else Push.disable(activity)
        }
    }
    val options = listOf(
        Triple(NotifyMode.Instant, "Instant",
            "Through the ntfy app (UnifiedPush). LabHUD only knocks; this app reads the news itself, at home or on the VPN."),
        Triple(NotifyMode.Ntfy, "ntfy only", "The ntfy app shows LabHUD's own messages, if a topic is set on the wall."),
        Triple(NotifyMode.Off, "Off", "Nothing on this phone."),
    )
    Surface(shape = CardShape, color = Lab.card, border = BorderStroke(1.dp, Lab.line)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            options.forEach { (m, title, hint) ->
                Row(
                    Modifier.fillMaxWidth().selectable(selected = m == mode, role = Role.RadioButton) { if (m != mode) pick(m) }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = m == mode, onClick = null, modifier = Modifier.padding(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(title, fontSize = 15.sp)
                        Text(hint, fontSize = 12.sp, color = Lab.muted)
                    }
                }
            }
            if (note.isNotEmpty()) Text(note, fontSize = 12.sp, color = Lab.warn, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
            if (mode == NotifyMode.Instant && (via ?: asked) == "ntfy") {
                Text("LabHUD sends its news only to ntfy now, so this app is not woken" +
                    (if (rights.admin) ": choose App or Both below." else ". The admin can change it here."),
                    fontSize = 12.sp, color = Lab.warn, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
            }
        }
    }
}
