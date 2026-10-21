package io.github.mithrandler.labhud.ui.settings

import android.Manifest
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import io.github.mithrandler.labhud.data.AlertSetup
import io.github.mithrandler.labhud.data.AppSettings
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
    val rights by repo.rights.collectAsState()
    val settings by repo.store.settings.collectAsState(initial = AppSettings())
    val scope = rememberCoroutineScope()
    val gate = rememberGate(repo)
    var alerts by remember { mutableStateOf<AlertSetup?>(null) }
    var message by remember { mutableStateOf("") }

    fun load() = scope.launch {
        val api = repo.api() ?: return@launch
        runCatching { api.alertSetup() }.onSuccess { alerts = it }.onFailure { message = it.message ?: "Not loaded." }
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
                item { Soon(listOf("Email, webhook, Discord and Gotify, each with a test", "Which news goes where: alerts, a host down, updates, sign-ins")) }
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
    val rights by repo.rights.collectAsState()
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
